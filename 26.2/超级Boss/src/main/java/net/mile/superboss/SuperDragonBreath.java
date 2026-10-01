package net.mile.superboss;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.phys.AABB;

/**
 * 超级龙息:栖息中的超级末影龙射出的强化龙息弹。命中时对周围生物直接造成伤害并
 * 清除其全部增益效果,原地留下比原版更大更毒的龙息云。
 */
public final class SuperDragonBreath {
	/** 打在龙息弹身上的实体标签,DragonFireballMixin 靠它区分超级龙息和普通弹幕火球。 */
	public static final String SUPER_TAG = "superboss_super_breath";

	private SuperDragonBreath() {
	}

	/** 超级龙息落地:直接伤害 + 清增益 + 大号毒云。只在服务端调用。 */
	public static void impact(ServerLevel level, DragonFireball fireball) {
		double x = fireball.getX();
		double y = fireball.getY();
		double z = fireball.getZ();

		// 落点爆开的紫色粒子
		level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F),
				x, y, z, 80, 1.2, 0.6, 1.2, 0.05);

		// 直接命中的生物:受伤 + 清除全部增益效果
		double radius = SuperBossConfig.DRAGON_SUPER_BREATH_HIT_RADIUS;
		AABB hitBox = new AABB(x, y, z, x, y, z).inflate(radius);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, hitBox,
				e -> e.isAlive() && !e.isSpectator() && !(e instanceof EnderDragon))) {
			victim.hurtServer(level, level.damageSources().dragonBreath(),
					SuperBossConfig.DRAGON_SUPER_BREATH_DAMAGE);
			int removed = superboss$clearBeneficialEffects(victim);
			if (removed > 0 && victim instanceof ServerPlayer player) {
				player.sendOverlayMessage(Component.literal(
						"§5超级龙息清除了你的 " + removed + " 个增益效果!"));
				player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.6F);
			}
		}

		// 残留毒云:比原版更大,除了瞬间伤害还带凋零
		AreaEffectCloud cloud = new AreaEffectCloud(level, x, y, z);
		if (fireball.getOwner() instanceof LivingEntity owner) {
			cloud.setOwner(owner);
		}
		cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
		cloud.setRadius(SuperBossConfig.DRAGON_SUPER_BREATH_CLOUD_RADIUS);
		cloud.setDuration(SuperBossConfig.DRAGON_SUPER_BREATH_CLOUD_DURATION);
		cloud.setRadiusPerTick((7.0F - cloud.getRadius()) / cloud.getDuration());
		cloud.setPotionDurationScale(0.25F);
		cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1));
		cloud.addEffect(new MobEffectInstance(MobEffects.WITHER,
				SuperBossConfig.DRAGON_SUPER_BREATH_WITHER_DURATION, 0));
		level.addFreshEntity(cloud);
		// 原版龙息云落地同款"嘶"声(2006)
		level.levelEvent(2006, fireball.blockPosition(), fireball.isSilent() ? -1 : 1);
	}

	/** 清掉目标身上所有增益(正面)效果,返回清了几个。 */
	private static int superboss$clearBeneficialEffects(LivingEntity target) {
		// getActiveEffects 是内部 Map 的视图,先拷贝再删,避免 ConcurrentModificationException
		List<Holder<MobEffect>> beneficial = new ArrayList<>();
		for (MobEffectInstance instance : target.getActiveEffects()) {
			if (instance.getEffect().value().isBeneficial()) {
				beneficial.add(instance.getEffect());
			}
		}
		for (Holder<MobEffect> effect : beneficial) {
			target.removeEffect(effect);
		}
		return beneficial.size();
	}
}
