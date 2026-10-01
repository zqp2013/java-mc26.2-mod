package net.mile.chainminer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 连锁挖掘的撤销:记录每个玩家最近一次连锁的快照(方块、掉落物、经验、工具耐久)。
 * 撤销是"全有全无":所有掉落物(地上的+已捡进背包的)和经验都还得起才撤销,
 * 任何一样被消耗过就拒绝——防止"挖了→把矿用掉→再撤销"无限刷物品/经验。
 */
public final class ChainMinerUndo {
	private static final Map<UUID, Snapshot> LAST_BY_PLAYER = new HashMap<>();

	private ChainMinerUndo() {
	}

	private record Snapshot(ResourceKey<Level> dimension, List<BlockPos> positions, List<BlockState> states,
			List<Integer> dropEntityIds, List<ItemStack> dropStacks, List<Integer> orbEntityIds, int orbAmount,
			Item toolItem, int toolDamageBefore) {
	}

	static void record(ServerPlayer player, ServerLevel level, List<BlockPos> positions, List<BlockState> states,
			List<Integer> dropEntityIds, List<ItemStack> dropStacks, List<Integer> orbEntityIds, int orbAmount,
			Item toolItem, int toolDamageBefore) {
		LAST_BY_PLAYER.put(player.getUUID(), new Snapshot(level.dimension(), positions, states,
				dropEntityIds, dropStacks, orbEntityIds, orbAmount, toolItem, toolDamageBefore));
	}

	public static void undo(MinecraftServer server, ServerPlayer player) {
		Snapshot snapshot = LAST_BY_PLAYER.get(player.getUUID());
		if (snapshot == null) {
			player.sendSystemMessage(Component.literal("没有可撤销的连锁"));
			return;
		}
		ServerLevel level = server.getLevel(snapshot.dimension());
		if (level == null) {
			LAST_BY_PLAYER.remove(player.getUUID());
			player.sendSystemMessage(Component.literal("连锁所在维度已不可用,无法撤销"));
			return;
		}

		// ── 第一步:清点欠账(地上的掉落物还剩多少,背包能不能补齐缺口) ──
		// 地上能顶账的有两类:快照里记过 id 的原实体(还躺在矿脉处没捡),
		// 以及玩家重新丢在身边的同物品掉落(丢弃会生成新 id,光认快照 id 会误判"被消耗过")
		Map<Item, List<ItemEntity>> groundByItem = collectGroundEntities(level, player, snapshot);
		Map<Item, Integer> need = new HashMap<>(); // 应收回的物品总量
		for (ItemStack recorded : snapshot.dropStacks()) {
			if (recorded != null && !recorded.isEmpty()) {
				need.merge(recorded.getItem(), recorded.getCount(), Integer::sum);
			}
		}
		StringBuilder missingText = new StringBuilder();
		for (Map.Entry<Item, Integer> entry : need.entrySet()) {
			int ground = groundCount(groundByItem, entry.getKey());
			int fromInventory = entry.getValue() - ground;
			if (fromInventory > 0 && countInInventory(player.getInventory(), entry.getKey()) < fromInventory) {
				if (missingText.length() > 0) {
					missingText.append("、");
				}
				missingText.append(fromInventory).append("个")
						.append(Component.translatable(entry.getKey().getDescriptionId()).getString());
			}
		}
		if (missingText.length() > 0) {
			// 掉落物被消耗过:拒绝撤销,快照保留(把东西找回来还能再撤)
			player.sendSystemMessage(Component.literal("掉落物已被消耗,无法撤销:还差 " + missingText
					+ "(需要把连锁挖到的掉落物放回背包,或丢在自己身边)"));
			return;
		}

		// ── 第二步:恢复方块(位置被占用的跳过) ──
		int restored = 0;
		int skipped = 0;
		for (int i = 0; i < snapshot.positions().size(); i++) {
			BlockPos pos = snapshot.positions().get(i);
			if (level.getBlockState(pos).isAir() && level.setBlock(pos, snapshot.states().get(i), 3)) {
				restored++;
			} else {
				skipped++;
			}
		}

		// ── 第三步:回收掉落物(按物品类型记账,地上的删掉/缩量,缺的部分从背包扣;合堆过也能对上账) ──
		Map<Item, Integer> takeFromInventory = new HashMap<>();
		int reclaimedEntities = 0;
		for (Map.Entry<Item, Integer> entry : need.entrySet()) {
			int left = entry.getValue();
			for (ItemEntity itemEntity : groundByItem.getOrDefault(entry.getKey(), List.of())) {
				if (left <= 0) {
					break;
				}
				ItemStack current = itemEntity.getItem();
				int take = Math.min(left, current.getCount());
				current.shrink(take);
				if (current.isEmpty()) {
					itemEntity.discard();
				} else {
					itemEntity.setItem(current);
				}
				reclaimedEntities++;
				left -= take;
			}
			if (left > 0) {
				// 地上收不齐的部分从背包扣
				takeFromInventory.put(entry.getKey(), left);
			}
		}
		removeFromInventory(player.getInventory(), takeFromInventory);

		// ── 第四步:撤销经验(还在地上的球直接删,已捡走的扣回来) ──
		int xpToRevoke = snapshot.orbAmount();
		for (int orbId : snapshot.orbEntityIds()) {
			Entity entity = level.getEntity(orbId);
			if (entity instanceof ExperienceOrb orb && !entity.isRemoved()) {
				xpToRevoke -= orb.getValue();
				orb.discard();
			}
		}
		if (xpToRevoke > 0) {
			player.giveExperiencePoints(-xpToRevoke);
		}

		// ── 第五步:返还工具耐久(主手还是同一件工具时) ──
		ItemStack tool = player.getMainHandItem();
		boolean durabilityRefunded = false;
		if (snapshot.toolDamageBefore() >= 0 && tool.isDamageableItem() && tool.getItem() == snapshot.toolItem()) {
			tool.setDamageValue(snapshot.toolDamageBefore());
			durabilityRefunded = true;
		}

		LAST_BY_PLAYER.remove(player.getUUID());
		ChainMinerAdvancements.grant(player, "undo_once");

		MutableComponent message = Component.literal("已撤销连锁:恢复了 " + restored + " 个方块");
		if (!takeFromInventory.isEmpty()) {
			message.append(",从背包扣回了掉落物");
		}
		if (reclaimedEntities > 0) {
			message.append(",回收了 " + reclaimedEntities + " 组地上掉落");
		}
		if (xpToRevoke > 0) {
			message.append(",扣回了 " + xpToRevoke + " 点经验");
		}
		if (skipped > 0) {
			message.append(",跳过了 " + skipped + " 个被占用的位置");
		}
		if (durabilityRefunded) {
			message.append(",工具耐久已返还");
		}
		player.sendSystemMessage(message);
	}

	/**
	 * 地上还能收回去的掉落物:快照原实体(按 id 找,可能在矿脉处没捡)
	 * + 玩家周围 16 格内的同物品掉落(玩家把东西重新丢回地上时是新 id)。
	 * 按物品类型分组返回;合堆过的实体按当前实际数量算,不会漏账。
	 */
	private static Map<Item, List<ItemEntity>> collectGroundEntities(ServerLevel level, ServerPlayer player,
			Snapshot snapshot) {
		Map<Item, List<ItemEntity>> map = new HashMap<>();
		Set<Integer> seen = new HashSet<>();
		for (int id : snapshot.dropEntityIds()) {
			Entity entity = level.getEntity(id);
			if (entity instanceof ItemEntity itemEntity && !entity.isRemoved() && !itemEntity.getItem().isEmpty()) {
				seen.add(entity.getId());
				map.computeIfAbsent(itemEntity.getItem().getItem(), item -> new ArrayList<>()).add(itemEntity);
			}
		}
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(16.0), e -> !e.isRemoved() && !e.getItem().isEmpty())) {
			if (seen.add(itemEntity.getId())) {
				map.computeIfAbsent(itemEntity.getItem().getItem(), item -> new ArrayList<>()).add(itemEntity);
			}
		}
		return map;
	}

	private static int groundCount(Map<Item, List<ItemEntity>> groundByItem, Item item) {
		int total = 0;
		for (ItemEntity itemEntity : groundByItem.getOrDefault(item, List.of())) {
			total += itemEntity.getItem().getCount();
		}
		return total;
	}

	private static int countInInventory(Inventory inventory, Item item) {
		int total = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.getItem() == item) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void removeFromInventory(Inventory inventory, Map<Item, Integer> amounts) {
		for (Map.Entry<Item, Integer> entry : amounts.entrySet()) {
			int left = entry.getValue();
			for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.getItem() != entry.getKey()) {
					continue;
				}
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
				if (stack.isEmpty()) {
					inventory.setItem(slot, ItemStack.EMPTY);
				}
			}
		}
	}
}
