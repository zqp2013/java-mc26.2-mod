package net.mile.superboss.mixin;

import net.mile.superboss.SuperBossConfig;
import net.mile.superboss.SuperBossEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1) 末影龙与凋零免疫 80% 爆炸伤害;
 * 2) 坚守者音波攻击命中后,给目标随机附加 5-10 种负面效果。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Unique
	private static boolean superboss$reducingExplosion = false;

	@Inject(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"), cancellable = true)
	private void superboss$bossDefenses(ServerLevel serverLevel, DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;

		// 坚守者音波 → 随机负面效果
		if (source.is(DamageTypes.SONIC_BOOM) && source.getDirectEntity() instanceof Warden) {
			SuperBossEffects.applyRandomHarmfulEffects(self,
					SuperBossConfig.WARDEN_SONIC_EFFECT_MIN, SuperBossConfig.WARDEN_SONIC_EFFECT_MAX);
		}

		// 末影龙/凋零:爆炸伤害只吃 20%
		if (!superboss$reducingExplosion && amount > 0.0F && source.is(DamageTypeTags.IS_EXPLOSION)
				&& (self instanceof EnderDragon || self instanceof WitherBoss)) {
			superboss$reducingExplosion = true;
			try {
				cir.setReturnValue(self.hurtServer(serverLevel, source, amount * SuperBossConfig.BOSS_EXPLOSION_DAMAGE_FACTOR));
			} finally {
				superboss$reducingExplosion = false;
			}
		}
	}
}
