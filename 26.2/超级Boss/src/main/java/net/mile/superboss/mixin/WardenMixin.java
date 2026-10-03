package net.mile.superboss.mixin;

import net.mile.superboss.Advancements;
import net.mile.superboss.SuperBossConfig;
import net.mile.superboss.WardenBossBars;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 强化坚守者:1000 血、显示 Boss 血条、受击 15% 概率"大运冲撞"把敌人往正上方创飞并造成 21 点伤害。
 * 音波攻击的随机负面效果在 LivingEntityMixin 里统一处理。
 */
@Mixin(Warden.class)
public abstract class WardenMixin {
	@Unique
	private int superboss$chargeReadyTick;

	@Unique
	private boolean superboss$announcedSummon = false;

	/** 坚守者刚登场 → 给附近玩家发"『宿管』来了" */
	@Inject(method = "customServerAiStep", at = @At("HEAD"))
	private void superboss$announceWarden(ServerLevel serverLevel, CallbackInfo ci) {
		Warden self = (Warden) (Object) this;
		if (superboss$announcedSummon || !self.isAlive()) {
			return;
		}
		superboss$announcedSummon = true;
		for (ServerPlayer player : serverLevel.players()) {
			if (player.distanceToSqr(self) <= 64.0 * 64.0) {
				Advancements.grant(player, "summon_warden");
			}
		}
	}

	@Redirect(method = "createAttributes",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;add(Lnet/minecraft/core/Holder;D)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;"))
	private static AttributeSupplier.Builder superboss$boostHealth(AttributeSupplier.Builder builder, Holder<Attribute> attribute, double value) {
		if (attribute == Attributes.MAX_HEALTH) {
			value = SuperBossConfig.WARDEN_MAX_HEALTH;
		}
		return builder.add(attribute, value);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void superboss$updateBossBar(CallbackInfo ci) {
		Warden self = (Warden) (Object) this;
		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (self.isRemoved() || !self.isAlive()) {
			WardenBossBars.remove(self);
			return;
		}
		ServerBossEvent bossEvent = WardenBossBars.getOrCreate(self);
		bossEvent.setProgress(Math.max(0.0F, self.getHealth() / self.getMaxHealth()));
		double rangeSq = SuperBossConfig.WARDEN_BOSS_BAR_RANGE * SuperBossConfig.WARDEN_BOSS_BAR_RANGE;
		for (ServerPlayer player : serverLevel.getPlayers(p -> p.distanceToSqr(self) <= rangeSq)) {
			if (!bossEvent.getPlayers().contains(player)) {
				bossEvent.addPlayer(player);
			}
		}
		for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
			if (player.distanceToSqr(self) > rangeSq || player.isRemoved()) {
				bossEvent.removePlayer(player);
			}
		}
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void superboss$bigRushCharge(ServerLevel serverLevel, DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		Warden self = (Warden) (Object) this;
		if (!cir.getReturnValueZ() || !self.isAlive() || self.tickCount < superboss$chargeReadyTick) {
			return;
		}
		if (self.getRandom().nextFloat() >= SuperBossConfig.WARDEN_CHARGE_CHANCE) {
			return;
		}
		if (!(source.getEntity() instanceof LivingEntity attacker)
				|| !attacker.isAlive()
				|| self.distanceToSqr(attacker) > 20.0 * 20.0) {
			return;
		}

		superboss$chargeReadyTick = self.tickCount + SuperBossConfig.WARDEN_CHARGE_COOLDOWN;
		self.playSound(SoundEvents.WARDEN_ROAR, 3.0F, 0.7F);
		// 大运冲撞:把对方往正上方创飞 + 21 点伤害
		attacker.push(0.0, SuperBossConfig.WARDEN_CHARGE_LAUNCH, 0.0);
		attacker.syncVelocity = true; // 26.3: hurtMarked 改名 syncVelocity
		attacker.hurtServer(serverLevel, serverLevel.damageSources().mobAttack(self), SuperBossConfig.WARDEN_CHARGE_DAMAGE);
	}
}
