package net.mile.superboss.mixin;

import net.mile.superboss.Advancements;
import net.mile.superboss.DragonBulletRain;
import net.mile.superboss.DragonMinions;
import net.mile.superboss.SuperBossConfig;
import net.mile.superboss.SuperBossPillars;
import net.mile.superboss.SuperDragonBreath;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 超级末影龙:500 血 + 飞行中周期性朝附近玩家喷火球弹幕 + 每 15 秒一轮末影弹雨
 * + 栖息在祭坛上时有概率朝玩家发射清增益的"超级龙息" + 栖息时补全末影柱(不复活水晶)
 * + 周期性在目标玩家周围召唤幻翼/末影螨/潜影贝,龙死时仆从清场。
 */
@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {
	@Unique
	private int superboss$fireballCooldown;
	@Unique
	private int superboss$rainCooldown = SuperBossConfig.DRAGON_BULLET_RAIN_INTERVAL;
	@Unique
	private int superboss$sittingBreathCooldown = SuperBossConfig.DRAGON_SUPER_BREATH_INTERVAL;
	@Unique
	private boolean superboss$wasSitting;
	@Unique
	private int superboss$summonCooldown = SuperBossConfig.DRAGON_SUMMON_INTERVAL;

	@Redirect(method = "createAttributes",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;add(Lnet/minecraft/core/Holder;D)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;"))
	private static AttributeSupplier.Builder superboss$boostHealth(AttributeSupplier.Builder builder, Holder<Attribute> attribute, double value) {
		if (attribute == Attributes.MAX_HEALTH) {
			value = SuperBossConfig.DRAGON_MAX_HEALTH;
		}
		return builder.add(attribute, value);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void superboss$fireballBarrage(CallbackInfo ci) {
		EnderDragon self = (EnderDragon) (Object) this;
		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		if (--superboss$fireballCooldown > 0 || !self.isAlive() || self.isDeadOrDying()) {
			return;
		}
		// 停在祭坛上时交给原版的吐息阶段,不追加弹幕
		if (self.getPhaseManager().getCurrentPhase().isSitting()) {
			return;
		}
		Player target = superboss$findTarget(serverLevel, self);
		if (target == null) {
			return;
		}
		superboss$fireballCooldown = SuperBossConfig.DRAGON_FIREBALL_INTERVAL;

		if (!self.isSilent()) {
			serverLevel.levelEvent(null, 1017, self.blockPosition(), 0);
		}

		double x = self.getX();
		double y = self.getY();
		double z = self.getZ();
		for (int i = 0; i < SuperBossConfig.DRAGON_FIREBALL_COUNT; i++) {
			// 以玩家为基准方向,水平面上左右各偏一点角度形成弹幕
			double spreadYaw = (i - (SuperBossConfig.DRAGON_FIREBALL_COUNT - 1) / 2.0) * 0.12;
			double dx = target.getX() - x;
			double dy = target.getY(0.5) - y;
			double dz = target.getZ() - z;
			double cos = Math.cos(spreadYaw);
			double sin = Math.sin(spreadYaw);
			Vec3 direction = new Vec3(dx * cos - dz * sin, dy, dx * sin + dz * cos).normalize();
			DragonFireball fireball = new DragonFireball(serverLevel, self, direction);
			fireball.snapTo(x, y, z, 0.0F, 0.0F);
			serverLevel.addFreshEntity(fireball);
		}
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void superboss$bulletRain(CallbackInfo ci) {
		EnderDragon self = (EnderDragon) (Object) this;
		if (!(self.level() instanceof ServerLevel serverLevel) || !self.isAlive() || self.isDeadOrDying()) {
			return;
		}
		if (--superboss$rainCooldown > 0) {
			return;
		}
		Player target = superboss$findTarget(serverLevel, self);
		if (target == null) {
			// 附近没有可攻击的玩家:5 秒后再试,不浪费这轮
			superboss$rainCooldown = 100;
			return;
		}
		superboss$rainCooldown = SuperBossConfig.DRAGON_BULLET_RAIN_INTERVAL;
		DragonBulletRain.start(serverLevel, target, self);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void superboss$sittingSuperBreath(CallbackInfo ci) {
		EnderDragon self = (EnderDragon) (Object) this;
		if (!(self.level() instanceof ServerLevel serverLevel) || !self.isAlive() || self.isDeadOrDying()) {
			return;
		}
		// 只在栖息(停在祭坛上)时判定,飞行的弹幕交给上面两个技能
		if (!self.getPhaseManager().getCurrentPhase().isSitting()) {
			return;
		}
		if (--superboss$sittingBreathCooldown > 0) {
			return;
		}
		superboss$sittingBreathCooldown = SuperBossConfig.DRAGON_SUPER_BREATH_INTERVAL;
		if (self.getRandom().nextFloat() >= SuperBossConfig.DRAGON_SUPER_BREATH_CHANCE) {
			return;
		}
		Player target = superboss$findTarget(serverLevel, self);
		if (target == null) {
			return;
		}
		// 从龙头朝玩家吐一枚超级龙息:命中直接伤害 + 清光增益 + 大毒云
		Vec3 head = self.head.position();
		Vec3 direction = new Vec3(target.getX() - head.x, target.getY(0.5) - head.y,
				target.getZ() - head.z).normalize();
		DragonFireball fireball = new DragonFireball(serverLevel, self, direction);
		fireball.snapTo(head.x, head.y, head.z, 0.0F, 0.0F);
		fireball.addTag(SuperDragonBreath.SUPER_TAG);
		if (!self.isSilent()) {
			serverLevel.levelEvent(null, 1017, self.blockPosition(), 0);
		}
		serverLevel.addFreshEntity(fireball);
		// 出膛时的紫色粒子预告
		serverLevel.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F),
				head.x, head.y, head.z, 30, 0.5, 0.5, 0.5, 0.02);
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void superboss$perchPillarRepair(CallbackInfo ci) {
		EnderDragon self = (EnderDragon) (Object) this;
		boolean sitting = self.isAlive()
				&& self.level() instanceof ServerLevel
				&& self.getPhaseManager().getCurrentPhase().isSitting();
		// 栖息瞬间(未栖息 → 栖息)补全末影柱;已死的水晶不会复活
		if (sitting && !superboss$wasSitting && SuperBossConfig.DRAGON_PILLAR_REPAIR_ON_PERCH) {
			SuperBossPillars.repair((ServerLevel) self.level());
		}
		superboss$wasSitting = sitting;
	}

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void superboss$summonMinions(CallbackInfo ci) {
		EnderDragon self = (EnderDragon) (Object) this;
		if (!(self.level() instanceof ServerLevel serverLevel) || !self.isAlive() || self.isDeadOrDying()) {
			return;
		}
		if (--superboss$summonCooldown > 0) {
			return;
		}
		superboss$summonCooldown = SuperBossConfig.DRAGON_SUMMON_INTERVAL;
		Player target = superboss$findTarget(serverLevel, self);
		if (target == null) {
			// 附近没有可攻击的玩家:5 秒后再试,不浪费这轮
			superboss$summonCooldown = 100;
			return;
		}
		DragonMinions.summon(serverLevel, target);
	}

	@Inject(method = "tickDeath", at = @At("HEAD"))
	private void superboss$dismissMinionsOnDeath(CallbackInfo ci) {
		EnderDragon self = (EnderDragon) (Object) this;
		// tickDeath 第一刻(dragonDeathTime 还是 0)把仆从清场,别让它们留在末地
		if (SuperBossConfig.DRAGON_SUMMON_CLEAR_ON_DEATH && self.dragonDeathTime == 0
				&& self.level() instanceof ServerLevel serverLevel) {
			DragonMinions.dismissAll(serverLevel);
		}
	}

	/** 被龙身撞飞的玩家 → "我飞天了" */
	@Inject(method = "knockBack", at = @At("TAIL"))
	private void superboss$trackDragonKnock(ServerLevel serverLevel, List<Entity> entities, CallbackInfo ci) {
		for (Entity entity : entities) {
			if (entity instanceof ServerPlayer player) {
				Advancements.grant(player, "dragon_knock");
			}
		}
	}

	@Unique
	private static Player superboss$findTarget(ServerLevel serverLevel, EnderDragon self) {
		double rangeSq = SuperBossConfig.DRAGON_FIREBALL_RANGE * SuperBossConfig.DRAGON_FIREBALL_RANGE;
		ServerPlayer best = null;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayer player : serverLevel.getPlayers(p ->
				!p.isSpectator() && !p.isCreative() && p.distanceToSqr(self) <= rangeSq)) {
			double dist = player.distanceToSqr(self);
			if (dist < bestDist) {
				best = player;
				bestDist = dist;
			}
		}
		return best;
	}
}
