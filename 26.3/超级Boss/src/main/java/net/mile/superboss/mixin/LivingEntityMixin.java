package net.mile.superboss.mixin;

import net.mile.superboss.Advancements;
import net.mile.superboss.SuperBossConfig;
import net.mile.superboss.SuperBossEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * 2) 坚守者音波攻击命中后,给目标随机附加 1-5 种负面效果(瞬间伤害为 II 级);
 * 3) 相关进度:对龙造成爆炸伤害 / 被坚守者音波击中。
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

		// 坚守者音波 → 随机负面效果 + 进度
		if (source.is(DamageTypes.SONIC_BOOM) && source.getDirectEntity() instanceof Warden
				&& self instanceof ServerPlayer hitPlayer) {
			SuperBossEffects.Result result = SuperBossEffects.applyRandomHarmfulEffects(self,
					SuperBossConfig.WARDEN_SONIC_EFFECT_MIN, SuperBossConfig.WARDEN_SONIC_EFFECT_MAX);
			Advancements.grant(hitPlayer, "warden_sonic");
			if (result.count() >= 5) {
				Advancements.grant(hitPlayer, "warden_sonic_5");
				if (result.instantHarmTwo()) {
					Advancements.grant(hitPlayer, "warden_sonic_deadly");
				}
			}
		}

		// 末影龙/凋零:爆炸伤害只吃 20%
		if (!superboss$reducingExplosion && amount > 0.0F && source.is(DamageTypeTags.IS_EXPLOSION)
				&& (self instanceof EnderDragon || self instanceof WitherBoss)) {
			// 对末影龙造成过爆炸伤害 → 进度(优先记给爆炸来源玩家,否则记给附近玩家)
			if (self instanceof EnderDragon dragon) {
				if (source.getEntity() instanceof ServerPlayer attacker) {
					Advancements.grant(attacker, "dragon_explosion");
				} else {
					for (ServerPlayer near : serverLevel.players()) {
						if (near.distanceToSqr(dragon) < 64.0 * 64.0) {
							Advancements.grant(near, "dragon_explosion");
						}
					}
				}
			}
			superboss$reducingExplosion = true;
			try {
				cir.setReturnValue(self.hurtServer(serverLevel, source, amount * SuperBossConfig.BOSS_EXPLOSION_DAMAGE_FACTOR));
			} finally {
				superboss$reducingExplosion = false;
			}
		}
	}
}
