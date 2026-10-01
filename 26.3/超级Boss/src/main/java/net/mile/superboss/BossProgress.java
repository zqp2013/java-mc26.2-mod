package net.mile.superboss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;

/**
 * 服务端每刻跑的进度判定:
 * - 进末地 → "这个末影龙不对劲";
 * - 三 Boss 同时活着出现在末地 → "三星汇聚!";
 * - 末影龙/凋零/坚守者在 1 分钟内相继被杀 → "三boss战玩家";
 * - 在末影柱上累计挖 100 块黑曜石 → "刷黑曜石机"。
 */
public final class BossProgress {
	/** 三杀判定窗口(毫秒) */
	private static final long TRIPLE_KILL_WINDOW_MS = 60_000L;

	private BossProgress() {
	}

	public static void tick(MinecraftServer server) {
		ServerLevel end = server.getLevel(Level.END);
		if (end == null || end.players().isEmpty()) {
			return;
		}
		// 进末地提示(只发一次)
		for (ServerPlayer player : end.players()) {
			if (BossState.SEEN_END.add(player.getUUID())) {
				Advancements.grant(player, "enter_end");
			}
		}
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		superboss$checkGather(end);
		superboss$checkTripleKill(end);
	}

	/** 末影龙 + 凋零 + 坚守者同时活着待在末地 */
	private static void superboss$checkGather(ServerLevel end) {
		boolean hasDragon = !end.getEntities(EntityTypes.ENDER_DRAGON, Entity::isAlive).isEmpty();
		if (!hasDragon) {
			return;
		}
		boolean hasWither = !end.getEntities(EntityTypes.WITHER, Entity::isAlive).isEmpty();
		if (!hasWither) {
			return;
		}
		boolean hasWarden = !end.getEntities(EntityTypes.WARDEN, Entity::isAlive).isEmpty();
		if (!hasWarden) {
			return;
		}
		for (ServerPlayer player : end.players()) {
			Advancements.grant(player, "three_bosses_gather");
		}
	}

	/** 三 Boss 在 1 分钟内相继被杀 */
	private static void superboss$checkTripleKill(ServerLevel end) {
		long dragon = BossState.dragonDeathAt;
		long wither = BossState.witherDeathAt;
		long warden = BossState.wardenDeathAt;
		if (dragon <= 0L || wither <= 0L || warden <= 0L) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - dragon > TRIPLE_KILL_WINDOW_MS || now - wither > TRIPLE_KILL_WINDOW_MS
				|| now - warden > TRIPLE_KILL_WINDOW_MS) {
			return;
		}
		for (ServerPlayer player : end.players()) {
			Advancements.grant(player, "three_bosses_kill");
		}
		// 时间戳清零,避免同一次三杀反复判定
		BossState.dragonDeathAt = 0L;
		BossState.witherDeathAt = 0L;
		BossState.wardenDeathAt = 0L;
	}

	/** 玩家挖方块后:挖的是末影柱上的黑曜石就计数 */
	public static void afterBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel)
				|| !(player instanceof ServerPlayer serverPlayer)
				|| !state.is(Blocks.OBSIDIAN)
				|| serverLevel.dimension() != Level.END) {
			return;
		}
		for (EndSpikeFeature.EndSpike spike : EndSpikeFeature.getSpikesForLevel(serverLevel)) {
			int dx = pos.getX() - spike.getCenterX();
			int dz = pos.getZ() - spike.getCenterZ();
			int reach = spike.getRadius() + 1;
			if (dx * dx + dz * dz > reach * reach || pos.getY() > spike.getHeight() + 1) {
				continue;
			}
			int mined = BossState.PILLAR_OBSIDIAN_MINED.merge(serverPlayer.getUUID(), 1, Integer::sum);
			if (mined >= 100) {
				Advancements.grant(serverPlayer, "obsidian_100");
			}
			return;
		}
	}
}
