package net.mile.superboss.mixin;

import java.util.List;

import net.mile.superboss.SuperBossConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 强化凋零:800 血、战斗中定期召唤凋灵骷髅、10 秒未受伤后每秒回 0.5% 最大生命。
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {
	@Unique
	private int superboss$lastHurtTick = -100000;

	@Redirect(method = "createAttributes",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;add(Lnet/minecraft/core/Holder;D)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;"))
	private static AttributeSupplier.Builder superboss$boostHealth(AttributeSupplier.Builder builder, Holder<Attribute> attribute, double value) {
		if (attribute == Attributes.MAX_HEALTH) {
			value = SuperBossConfig.WITHER_MAX_HEALTH;
		}
		return builder.add(attribute, value);
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void superboss$trackLastHurt(ServerLevel serverLevel, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			superboss$lastHurtTick = ((WitherBoss) (Object) this).tickCount;
		}
	}

	@Inject(method = "customServerAiStep", at = @At("HEAD"))
	private void superboss$regenAndSummon(ServerLevel serverLevel, CallbackInfo ci) {
		WitherBoss self = (WitherBoss) (Object) this;
		if (!self.isAlive()) {
			return;
		}

		// 10 秒没受到伤害 → 每秒回复 0.5% 最大生命
		if (self.tickCount - superboss$lastHurtTick >= SuperBossConfig.WITHER_REGEN_DELAY
				&& self.tickCount % 20 == 0
				&& self.getHealth() < self.getMaxHealth()) {
			self.heal(self.getMaxHealth() * SuperBossConfig.WITHER_REGEN_PERCENT_PER_SECOND);
		}

		// 战斗中定期召唤凋灵骷髅(出生无敌阶段不召唤)
		if (self.getInvulnerableTicks() > 0 || self.getTarget() == null
				|| self.tickCount % SuperBossConfig.WITHER_SUMMON_INTERVAL != 0) {
			return;
		}
		List<WitherSkeleton> nearby = serverLevel.getEntitiesOfClass(
				WitherSkeleton.class, self.getBoundingBox().inflate(48.0));
		if (nearby.size() >= SuperBossConfig.WITHER_SKELETON_CAP) {
			return;
		}
		for (int i = 0; i < SuperBossConfig.WITHER_SUMMON_COUNT; i++) {
			WitherSkeleton skeleton = EntityTypes.WITHER_SKELETON.create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
			if (skeleton == null) {
				continue;
			}
			double angle = self.getRandom().nextDouble() * Math.PI * 2.0;
			double x = self.getX() + Math.cos(angle) * 3.0;
			double z = self.getZ() + Math.sin(angle) * 3.0;
			skeleton.snapTo(x, self.getY(), z, self.getRandom().nextFloat() * 360.0F, 0.0F);
			serverLevel.addFreshEntity(skeleton);
			serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, self.getY() + skeleton.getBbHeight() * 0.5, z,
					20, 0.3, 0.5, 0.3, 0.02);
		}
	}
}
