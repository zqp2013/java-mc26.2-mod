package net.mile.chainminer;

import java.util.List;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 连锁挖掘进度:连锁一次 / 累计 5000、50000 方块(走原版自定义统计,自动随存档持久化)
 * / 一次连锁同时挖到浅层+深层矿脉 / 一次连锁 ≥2 个钻石块 / 撤销一次。
 */
public final class ChainMinerAdvancements {
	/** 累计连锁挖掘方块数的自定义统计(注册进原版统计系统,自动保存) */
	public static final Identifier CHAIN_BLOCKS_STAT = net.minecraft.core.Registry.register(
			BuiltInRegistries.CUSTOM_STAT, ChainMinerMod.id("chain_blocks_mined"),
			ChainMinerMod.id("chain_blocks_mined"));

	private ChainMinerAdvancements() {
	}

	public static void grant(ServerPlayer player, String path) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		AdvancementHolder holder = server.getAdvancements().get(
				Identifier.fromNamespaceAndPath(ChainMinerMod.MOD_ID, path));
		if (holder == null) {
			return;
		}
		player.getAdvancements().award(holder, "unlocked");
	}

	/** 一次连锁结束:累计统计 + 里程碑进度。 */
	public static void awardChain(ServerPlayer player, int blocks) {
		grant(player, "chain_once");
		player.awardStat(CHAIN_BLOCKS_STAT, blocks);
		int total = player.getStats().getValue(Stats.CUSTOM, CHAIN_BLOCKS_STAT);
		if (total >= 5000) {
			grant(player, "mine_5000");
		}
		if (total >= 50000) {
			grant(player, "mine_50000");
		}
	}

	/** 一次连锁里同时有浅层矿石和深板岩变种 → "这都行?" */
	public static void checkVeinTypes(ServerPlayer player, List<BlockState> brokenStates) {
		boolean shallow = false;
		boolean deep = false;
		for (BlockState state : brokenStates) {
			if (state == null) {
				continue;
			}
			String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
			if (!path.endsWith("_ore")) {
				continue;
			}
			if (path.startsWith("deepslate_")) {
				deep = true;
			} else {
				shallow = true;
			}
			if (shallow && deep) {
				grant(player, "mixed_vein");
				return;
			}
		}
	}

	/** 一次连锁挖到 2 个及以上钻石块 → "奢侈——" */
	public static void checkDiamondBlocks(ServerPlayer player, List<BlockState> brokenStates) {
		int count = 0;
		for (BlockState state : brokenStates) {
			if (state != null && state.is(Blocks.DIAMOND_BLOCK) && ++count >= 2) {
				grant(player, "diamond_blocks");
				return;
			}
		}
	}

	/** 静态初始化触发点(onInitialize 里调用一次) */
	public static void init() {
	}
}
