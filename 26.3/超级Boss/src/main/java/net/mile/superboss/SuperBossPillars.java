package net.mile.superboss;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.phys.AABB;

/**
 * 末影柱强化:末影龙栖息时补全被挖坏的黑曜石柱(不复活已死的水晶),
 * 并把老存档里还留在柱顶的水晶挪进柱身,和新生成的水晶保持一致。
 */
public final class SuperBossPillars {
	private SuperBossPillars() {
	}

	/** 水晶嵌进柱身后的落点 Y(和 EndSpikeFeatureMixin 里同一条公式)。 */
	public static double embedY(int minY, int spikeHeight) {
		return Math.floor(minY + (spikeHeight - minY) * SuperBossConfig.CRYSTAL_EMBED_FRACTION) + 1.0;
	}

	/** 末影龙栖息时调用:补全柱子 + 挪走柱顶的活水晶。 */
	public static void repair(ServerLevel level) {
		int repairedBlocks = 0;
		int movedCrystals = 0;
		for (EndSpikeFeature.EndSpike spike : EndSpikeFeature.getSpikesForLevel(level)) {
			repairedBlocks += superboss$fillSpike(level, spike);
			movedCrystals += superboss$relocateTopCrystals(level, spike);
			// 柱顶的紫色传送门粒子,提示"柱子被修好了"
			level.sendParticles(ParticleTypes.PORTAL,
					spike.getCenterX() + 0.5, spike.getHeight() + 1.0, spike.getCenterZ() + 0.5,
					40, spike.getRadius() * 0.5, 1.0, spike.getRadius() * 0.5, 0.5);
		}
		if (repairedBlocks > 0 || movedCrystals > 0) {
			for (ServerPlayer player : level.players()) {
				player.sendOverlayMessage(Component.literal(
						"§5末影龙栖息时补全了末影柱!(水晶不会复活)"));
				if (repairedBlocks > 0) {
					Advancements.grant(player, "pillar_repair");
				}
			}
		}
	}

	/** 把柱子补回完整的黑曜石圆柱(原版生成同款判定),返回补了多少格。 */
	private static int superboss$fillSpike(ServerLevel level, EndSpikeFeature.EndSpike spike) {
		int radius = spike.getRadius();
		int cx = spike.getCenterX();
		int cz = spike.getCenterZ();
		int minY = level.getMinY();
		int radiusSq = radius * radius + 1;
		int placed = 0;
		for (BlockPos pos : BlockPos.betweenClosed(
				new BlockPos(cx - radius, minY, cz - radius),
				new BlockPos(cx + radius, spike.getHeight() - 1, cz + radius))) {
			if (pos.distToLowCornerSqr(cx, pos.getY(), cz) > radiusSq) {
				continue;
			}
			if (level.getBlockState(pos).is(Blocks.OBSIDIAN)) {
				continue;
			}
			// 别把正在挖柱子的玩家(或活水晶)活埋进黑曜石里
			if (!level.getEntitiesOfClass(Entity.class, new AABB(pos),
					e -> e.isAlive() && (e instanceof LivingEntity || e instanceof EndCrystal)).isEmpty()) {
				continue;
			}
			level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
			placed++;
		}
		return placed;
	}

	/** 老存档迁移:把还留在柱顶的活水晶挪进柱身中间(已死的不复活)。 */
	private static int superboss$relocateTopCrystals(ServerLevel level, EndSpikeFeature.EndSpike spike) {
		int radius = spike.getRadius();
		int height = spike.getHeight();
		AABB topBox = new AABB(spike.getCenterX() - radius - 1, height, spike.getCenterZ() - radius - 1,
				spike.getCenterX() + radius + 1, height + 3, spike.getCenterZ() + radius + 1);
		List<EndCrystal> crystals = level.getEntitiesOfClass(EndCrystal.class, topBox);
		if (crystals.isEmpty()) {
			return 0;
		}
		double embedY = embedY(level.getMinY(), height);
		for (EndCrystal crystal : crystals) {
			crystal.snapTo(crystal.getX(), embedY, crystal.getZ(), crystal.getYRot(), 0.0F);
		}
		return crystals.size();
	}
}
