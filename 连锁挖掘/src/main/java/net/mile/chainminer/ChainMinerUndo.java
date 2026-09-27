package net.mile.chainminer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 连锁挖掘的撤销:记录每个玩家最近一次连锁的快照(方块、掉落物、工具耐久),一键恢复。 */
public final class ChainMinerUndo {
	private static final Map<UUID, Snapshot> LAST_BY_PLAYER = new HashMap<>();

	private ChainMinerUndo() {
	}

	private record Snapshot(ResourceKey<Level> dimension, List<BlockPos> positions, List<BlockState> states,
			List<Integer> dropEntityIds, List<ItemStack> dropStacks, Item toolItem, int toolDamageBefore) {
	}

	static void record(ServerPlayer player, ServerLevel level, List<BlockPos> positions, List<BlockState> states,
			List<Integer> dropEntityIds, List<ItemStack> dropStacks, Item toolItem, int toolDamageBefore) {
		LAST_BY_PLAYER.put(player.getUUID(),
				new Snapshot(level.dimension(), positions, states, dropEntityIds, dropStacks, toolItem, toolDamageBefore));
	}

	public static void undo(MinecraftServer server, ServerPlayer player) {
		Snapshot snapshot = LAST_BY_PLAYER.remove(player.getUUID());
		if (snapshot == null) {
			player.sendSystemMessage(Component.literal("没有可撤销的连锁"));
			return;
		}
		ServerLevel level = server.getLevel(snapshot.dimension());
		if (level == null) {
			player.sendSystemMessage(Component.literal("连锁所在维度已不可用,无法撤销"));
			return;
		}

		// 恢复方块:位置被占用(比如已重新放置了东西)的跳过
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

		// 回收掉落物:还在地上的直接删,已经捡进背包的移除对应物品
		int reclaimed = 0;
		for (int i = 0; i < snapshot.dropEntityIds().size(); i++) {
			Entity entity = level.getEntity(snapshot.dropEntityIds().get(i));
			if (entity instanceof ItemEntity && !entity.isRemoved()) {
				entity.discard();
				reclaimed++;
			} else if (player.getInventory().contains(snapshot.dropStacks().get(i))) {
				player.getInventory().removeItem(snapshot.dropStacks().get(i));
				reclaimed++;
			}
		}

		// 返还工具耐久(主手还是同一件工具时)
		ItemStack tool = player.getMainHandItem();
		boolean durabilityRefunded = false;
		if (snapshot.toolDamageBefore() >= 0 && tool.isDamageableItem() && tool.getItem() == snapshot.toolItem()) {
			tool.setDamageValue(snapshot.toolDamageBefore());
			durabilityRefunded = true;
		}

		MutableComponent message = Component.literal("已撤销连锁:恢复了 " + restored + " 个方块");
		if (reclaimed > 0) {
			message.append(",回收了 " + reclaimed + " 件掉落");
		}
		if (skipped > 0) {
			message.append(",跳过了 " + skipped + " 个被占用的位置");
		}
		if (durabilityRefunded) {
			message.append(",工具耐久已返还");
		}
		player.sendSystemMessage(message);
	}
}
