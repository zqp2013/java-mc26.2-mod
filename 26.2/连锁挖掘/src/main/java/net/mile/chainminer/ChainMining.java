package net.mile.chainminer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * 潜行挖掘时,把与所挖方块相连的同类方块整片连锁破坏(26 邻域含对角;同种矿的深板岩变种视为同类)。
 * 用 BEFORE 事件接管整次破坏(含手动挖的第一格),这样:
 * 耐久预算从挖之前结算,连锁结束后工具必定还剩至少 1 点耐久;
 * 掉落物聚脚功能也能覆盖第一格的掉落。
 * 每个方块的破坏流程与原版 ServerPlayerGameMode.destroyBlock 保持一致
 * (playerWillDestroy → removeBlock → destroy → 掉落/经验 → 工具耐久)。
 */
public final class ChainMining {
	/** 26 邻域(含对角):矿脉经常只通过对角相连,只连 6 面会漏挖。 */
	private static final int[][] NEIGHBOR_OFFSETS = buildNeighborOffsets();

	/** 方块家族名缓存:去掉 deepslate_ 前缀后的注册名,用于把 X_ore 和 deepslate_X_ore 视为同族。 */
	private static final Map<Block, String> FAMILY_NAME_BY_BLOCK = new ConcurrentHashMap<>();

	private ChainMining() {
	}

	private static int[][] buildNeighborOffsets() {
		int[][] offsets = new int[26][];
		int index = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dy != 0 || dz != 0) {
						offsets[index++] = new int[]{dx, dy, dz};
					}
				}
			}
		}
		return offsets;
	}

	/** 同族判定:完全相同的方块,或同一种矿的深板岩变种(仅对 _ore 结尾的方块生效,避免把深板岩砖和石砖误并)。 */
	private static boolean sameFamily(Block a, Block b) {
		if (a == b) {
			return true;
		}
		String familyA = FAMILY_NAME_BY_BLOCK.computeIfAbsent(a, ChainMining::familyNameOf);
		String familyB = FAMILY_NAME_BY_BLOCK.computeIfAbsent(b, ChainMining::familyNameOf);
		return familyA != null && familyA.equals(familyB);
	}

	/** 归一化注册名:去掉 deepslate_ 前缀;非 _ore 结尾返回 null(不参与跨变种匹配)。 */
	private static String familyNameOf(Block block) {
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (path.startsWith("deepslate_")) {
			path = path.substring("deepslate_".length());
		}
		return path.endsWith("_ore") ? path : null;
	}

	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!(level instanceof ServerLevel serverLevel) || !player.isShiftKeyDown()) {
				return true; // 与连锁无关,交给原版
			}
			if (player.isSpectator() || state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
				return true;
			}

			ServerPlayer serverPlayer = (ServerPlayer) player;
			ChainSettings settings = ChainMinerConfig.get(serverPlayer);

			// 总量受设置上限与工具耐久(至少保留 1 点)双重限制
			ItemStack tool = player.getMainHandItem();
			int allowedTotal = settings.max();
			boolean durabilityBound = false;
			if (!player.isCreative() && tool.isDamageableItem()) {
				int durabilityBudget = Math.max(0, tool.getMaxDamage() - 1 - tool.getDamageValue());
				durabilityBound = durabilityBudget < allowedTotal;
				allowedTotal = Math.min(allowedTotal, durabilityBudget);
			}
			if (allowedTotal <= 0) {
				// 再挖一格工具就坏了:取消本次破坏,保住最后 1 点耐久
				player.sendOverlayMessage(Component.literal("工具耐久不足,连锁挖掘已停止(已保留 1 点耐久)"));
				return false;
			}

			Block target = state.getBlock();
			// 设置开启且工具等级不够时,提醒这次连锁不会掉东西
			if (settings.warnWrongTier() && !player.hasCorrectToolForDrops(state)) {
				player.sendOverlayMessage(Component.literal("§c挖掘等级不够,这些方块不会掉落!"));
			}
			boolean[] moreRemain = new boolean[1];
			List<BlockPos> toBreak = collectChain(serverLevel, pos, target, allowedTotal, moreRemain);

			// 撤销快照用的工具耐久基线(挖之前)
			Item toolItem = (!player.isCreative() && tool.isDamageableItem()) ? tool.getItem() : null;
			int toolDamageBefore = toolItem != null ? tool.getDamageValue() : -1;

			List<BlockState> brokenStates = new ArrayList<>(toBreak.size());
			for (BlockPos chainPos : toBreak) {
				brokenStates.add(breakBlock(serverLevel, serverPlayer, chainPos));
			}

			// 记录撤销快照:本次生成的掉落物实体(age==0 即本刻刚生成)
			recordUndoSnapshot(serverPlayer, serverLevel, toBreak, brokenStates, toolItem, toolDamageBefore);

			// 进度:连锁一次 / 累计数量 / 浅深层混合矿脉 / 多个钻石块
			if (toBreak.size() >= 2) {
				ChainMinerAdvancements.awardChain(serverPlayer, toBreak.size());
				ChainMinerAdvancements.checkVeinTypes(serverPlayer, brokenStates);
				ChainMinerAdvancements.checkDiamondBlocks(serverPlayer, brokenStates);
			}

			if (toBreak.size() >= 2) {
				MutableComponent message = Component.literal("本次连锁了 " + toBreak.size() + " 个方块");
				if (durabilityBound && moreRemain[0]) {
					message.append(Component.literal(" (工具耐久不足,已保留 1 点耐久)"));
				}
				player.sendSystemMessage(message);
			}
			return false; // 本次破坏已由模组完成,原版不要重复处理
		});
	}

	/** 从起点向 26 邻域 BFS 收集同族方块(含起点),最多 limit 个;moreRemain[0] 表示预算用尽时仍有同族方块剩余。 */
	private static List<BlockPos> collectChain(ServerLevel level, BlockPos origin, Block target, int limit, boolean[] moreRemain) {
		List<BlockPos> result = new ArrayList<>(Math.min(limit, 256));
		result.add(origin);
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin);
		for (int[] offset : NEIGHBOR_OFFSETS) {
			queue.add(origin.offset(offset[0], offset[1], offset[2]));
		}

		while (!queue.isEmpty() && result.size() < limit) {
			BlockPos current = queue.poll();
			if (!visited.add(current)) {
				continue;
			}
			BlockState state = level.getBlockState(current);
			if (!matches(state, target, level, current)) {
				continue;
			}
			result.add(current);
			for (int[] offset : NEIGHBOR_OFFSETS) {
				queue.add(current.offset(offset[0], offset[1], offset[2]));
			}
		}

		moreRemain[0] = false;
		if (result.size() >= limit) {
			for (BlockPos pending : queue) {
				BlockState state = level.getBlockState(pending);
				if (!visited.contains(pending) && matches(state, target, level, pending)) {
					moreRemain[0] = true;
					break;
				}
			}
		}
		return result;
	}

	private static boolean matches(BlockState state, Block target, ServerLevel level, BlockPos pos) {
		return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F && sameFamily(state.getBlock(), target);
	}

	/** 记录撤销快照:方块位置与原状态、本次新生成的掉落物(实体 id + 物品)、工具耐久基线。 */
	private static void recordUndoSnapshot(ServerPlayer player, ServerLevel level, List<BlockPos> broken,
			List<BlockState> brokenStates, Item toolItem, int toolDamageBefore) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos pos : broken) {
			minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
			maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
		}
		BlockPos feet = player.blockPosition();
		minX = Math.min(minX, feet.getX()); minY = Math.min(minY, feet.getY()); minZ = Math.min(minZ, feet.getZ());
		maxX = Math.max(maxX, feet.getX()); maxY = Math.max(maxY, feet.getY()); maxZ = Math.max(maxZ, feet.getZ());

		AABB region = new AABB(minX - 2, minY - 2, minZ - 2, maxX + 3, maxY + 3, maxZ + 3);
		List<Integer> dropEntityIds = new ArrayList<>();
		List<ItemStack> dropStacks = new ArrayList<>();
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, region, e -> e.getAge() == 0)) {
			dropEntityIds.add(item.getId());
			dropStacks.add(item.getItem().copy());
		}
		// 本次生成的经验球(撤销时要一并收回,防止刷经验)
		List<Integer> orbEntityIds = new ArrayList<>();
		int orbAmount = 0;
		for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, region, o -> o.tickCount == 0)) {
			orbEntityIds.add(orb.getId());
			orbAmount += orb.getValue();
		}
		ChainMinerUndo.record(player, level, List.copyOf(broken), List.copyOf(brokenStates), dropEntityIds, dropStacks,
				orbEntityIds, orbAmount, toolItem, toolDamageBefore);
	}

	/** 按原版流程破坏一个方块(掉落按当前主手工具结算,经验正常掉落,工具消耗 1 点耐久),返回被破坏前的方块状态。所有掉落物一律生成在玩家脚边。 */
	private static BlockState breakBlock(ServerLevel level, ServerPlayer player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return null;
		}
		BlockEntity blockEntity = level.getBlockEntity(pos);
		Block block = state.getBlock();

		BlockState afterDestroy = block.playerWillDestroy(level, pos, state, player);
		boolean removed = level.removeBlock(pos, false);
		if (removed) {
			block.destroy(level, pos, afterDestroy);
		}
		// 广播破坏音效+粒子(2001),让包括挖掘者在内的玩家都能看到
		level.levelEvent(null, 2001, pos, Block.getId(state));

		if (player.preventsBlockDrops()) {
			return state;
		}
		ItemStack tool = player.getMainHandItem();
		ItemStack toolCopy = tool.copy();
		boolean correctTool = player.hasCorrectToolForDrops(state);
		tool.mineBlock(level, state, pos, player);
		if (removed && correctTool) {
			// 与 Block.playerDestroy 等价,但掉落物一律生成在玩家脚边而不是方块处
			player.causeFoodExhaustion(0.005F);
			for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, player, toolCopy)) {
				Block.popResource(level, player.blockPosition(), drop);
			}
			// 经验仍在方块处掉落(经验球会自动飞向附近的玩家)
			state.spawnAfterBreak(level, pos, toolCopy, true);
		}
		return state;
	}
}
