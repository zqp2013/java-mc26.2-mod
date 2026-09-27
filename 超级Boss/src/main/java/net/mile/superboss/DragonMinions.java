package net.mile.superboss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 超级末影龙的召唤技能:周期性在目标玩家周围召唤幻翼(空中)、末影螨和潜影贝(地面)。
 * 仆从用计分板标签 {@link #MINION_TAG} 标记并计入场上上限;末影龙死亡时全部消散。
 */
public final class DragonMinions {
	/** 召唤仆从的计分板标签(计数上限 + 龙死清场都用它认人)。 */
	public static final String MINION_TAG = "superboss_dragon_minion";

	private DragonMinions() {
	}

	/** 末影龙周期性调用:在 target 周围补齐一波仆从(没到上限的部分才召)。 */
	public static void summon(ServerLevel level, Player target) {
		int phantoms = 0;
		for (int i = superboss$countTagged(level, target, Phantom.class);
				i < SuperBossConfig.DRAGON_SUMMON_PHANTOM_CAP && phantoms < SuperBossConfig.DRAGON_SUMMON_PHANTOMS; i++, phantoms++) {
			Phantom mob = new Phantom(EntityTypes.PHANTOM, level);
			superboss$summonInAir(level, mob, target);
		}
		int mites = 0;
		for (int i = superboss$countTagged(level, target, Endermite.class);
				i < SuperBossConfig.DRAGON_SUMMON_ENDERMITE_CAP && mites < SuperBossConfig.DRAGON_SUMMON_ENDERMITES; i++, mites++) {
			Endermite mob = new Endermite(EntityTypes.ENDERMITE, level);
			superboss$summonOnGround(level, mob, target);
		}
		int shulkers = 0;
		for (int i = superboss$countTagged(level, target, Shulker.class);
				i < SuperBossConfig.DRAGON_SUMMON_SHULKER_CAP && shulkers < SuperBossConfig.DRAGON_SUMMON_SHULKERS; i++, shulkers++) {
			Shulker mob = new Shulker(EntityTypes.SHULKER, level);
			superboss$summonOnGround(level, mob, target);
		}
		if (phantoms + mites + shulkers > 0) {
			for (ServerPlayer player : level.players()) {
				player.sendOverlayMessage(Component.literal(String.format(
						"§5超级末影龙召唤了仆从!(幻翼x%d 末影螨x%d 潜影贝x%d)", phantoms, mites, shulkers)));
			}
		}
	}

	/** 末影龙死亡时把还活着的仆从全部清掉,别让它们永远留在末地。 */
	public static void dismissAll(ServerLevel level) {
		int removed = 0;
		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof Mob mob) || !mob.entityTags().contains(MINION_TAG)) {
				continue;
			}
			level.sendParticles(ParticleTypes.POOF,
					mob.getX(), mob.getY(0.5), mob.getZ(), 15, 0.3, 0.4, 0.3, 0.02);
			mob.discard();
			removed++;
		}
		if (removed > 0) {
			for (ServerPlayer player : level.players()) {
				player.sendOverlayMessage(Component.literal("§5末影龙的仆从随着主人消散了..."));
			}
		}
	}

	/** 场上还活着、带标签的某类仆从数量(在目标玩家周围一圈里数)。 */
	private static int superboss$countTagged(ServerLevel level, Player target, Class<? extends Mob> type) {
		return level.getEntitiesOfClass(type,
				target.getBoundingBox().inflate(SuperBossConfig.DRAGON_FIREBALL_RANGE),
				mob -> mob.isAlive() && mob.entityTags().contains(MINION_TAG)).size();
	}

	/** 幻翼:在目标上空绕一圈的空中落点。 */
	private static void superboss$summonInAir(ServerLevel level, Mob mob, Player target) {
		double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
		double dist = SuperBossConfig.DRAGON_SUMMON_RADIUS * 0.5 + mob.getRandom().nextDouble() * SuperBossConfig.DRAGON_SUMMON_RADIUS;
		double x = target.getX() + Math.cos(angle) * dist;
		double z = target.getZ() + Math.sin(angle) * dist;
		double y = target.getY() + 8.0 + mob.getRandom().nextDouble() * 8.0;
		superboss$finalizeSummon(level, mob, target, x, y, z);
	}

	/** 末影螨/潜影贝:在目标周围找一块实心地面落点,最多试 8 次,找不到就放弃这只。 */
	private static void superboss$summonOnGround(ServerLevel level, Mob mob, Player target) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
			double dist = 4.0 + mob.getRandom().nextDouble() * SuperBossConfig.DRAGON_SUMMON_RADIUS;
			int x = Mth.floor(target.getX() + Math.cos(angle) * dist);
			int z = Mth.floor(target.getZ() + Math.sin(angle) * dist);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (y <= level.getMinY()) {
				continue; // 下面是虚空
			}
			if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
				continue; // 头顶空间不够(高度图之上的两格得是空的)
			}
			superboss$finalizeSummon(level, mob, target, x + 0.5, y, z + 0.5);
			return;
		}
	}

	/** 共用收尾:定位、打标签、锁定仇恨、入世界、粒子+音效。 */
	private static void superboss$finalizeSummon(ServerLevel level, Mob mob, Player target, double x, double y, double z) {
		mob.snapTo(x, y, z, mob.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.setPersistenceRequired();
		mob.addTag(MINION_TAG);
		mob.setTarget(target);
		level.addFreshEntity(mob);
		level.sendParticles(ParticleTypes.PORTAL, x, y + 0.5, z, 25, 0.3, 0.5, 0.3, 0.2);
		level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
	}
}
