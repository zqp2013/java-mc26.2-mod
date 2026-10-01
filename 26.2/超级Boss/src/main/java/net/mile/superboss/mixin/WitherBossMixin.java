package net.mile.superboss.mixin;

import java.util.List;

import net.mile.superboss.Advancements;
import net.mile.superboss.BossState;
import net.mile.superboss.SuperBossConfig;
import net.mile.superboss.VolleySkull;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 强化凋零:800 血、战斗中每 20 秒在地面召唤 5 只持剑凋灵骷髅(上限 10)、
 * 10 秒未受伤后每秒回 0.5% 最大生命、一次齐射发射 3 个头颅(半血以下带蓝色头颅)。
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {
	@Unique
	private int superboss$lastHurtTick = -100000;

	@Unique
	private boolean superboss$announcedSummon = false;

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

		// 召唤完成(出生无敌结束)→ 给附近玩家发"超强凋零来袭"
		if (!superboss$announcedSummon && self.getInvulnerableTicks() <= 0) {
			superboss$announcedSummon = true;
			superboss$grantNearby(serverLevel, self, "summon_wither");
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
			superboss$maybeGrantArmy(serverLevel, self, nearby.size());
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
			// 落到地面高度,不在空中生成
			int groundY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING,
					Mth.floor(x), Mth.floor(z));
			skeleton.snapTo(x, groundY, z, self.getRandom().nextFloat() * 360.0F, 0.0F);
			// 拿石剑 + 打上标记(区分凋零召唤的骷髅)
			skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
			skeleton.addTag("superboss_summoned");
			serverLevel.addFreshEntity(skeleton);
			serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, groundY + skeleton.getBbHeight() * 0.5, z,
					20, 0.3, 0.5, 0.3, 0.02);
		}
		superboss$maybeGrantArmy(serverLevel, self, nearby.size() + SuperBossConfig.WITHER_SUMMON_COUNT);
	}

	/** 场上凋灵骷髅 ≥ 10 只 → 给附近玩家发"招募援兵" */
	@Unique
	private static void superboss$maybeGrantArmy(ServerLevel serverLevel, WitherBoss self, int count) {
		if (count < SuperBossConfig.WITHER_SKELETON_CAP) {
			return;
		}
		superboss$grantNearby(serverLevel, self, "skeleton_army_10");
	}

	@Unique
	private static void superboss$grantNearby(ServerLevel serverLevel, WitherBoss self, String path) {
		for (ServerPlayer player : serverLevel.players()) {
			if (player.distanceToSqr(self) <= 64.0 * 64.0) {
				Advancements.grant(player, path);
			}
		}
	}

	/** 一次齐射 3 个头颅:中间一颗直射,两侧呈扇形散开;半血以下全部换成蓝色头颅 */
	@Inject(method = "performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V",
			at = @At("HEAD"), cancellable = true)
	private void superboss$skullVolley(LivingEntity target, float power, CallbackInfo ci) {
		WitherBoss self = (WitherBoss) (Object) this;
		if (!(self.level() instanceof ServerLevel serverLevel) || !self.isAlive()) {
			return;
		}
		ci.cancel();

		double originX = self.getX();
		double originY = self.getY() + self.getEyeHeight();
		double originZ = self.getZ();
		Vec3 aim = new Vec3(target.getX() - originX,
				target.getEyeY() - originY,
				target.getZ() - originZ).normalize();
		boolean dangerous = self.getHealth() <= self.getMaxHealth() * 0.5;
		int volleyId = BossState.VOLLEY_ID.incrementAndGet();
		int count = SuperBossConfig.WITHER_SKULL_VOLLEY_COUNT;

		for (int i = 0; i < count; i++) {
			// 扇形:第 i 颗相对正中的水平偏角(0、±12°)
			double yawOffset = (i - (count - 1) / 2.0) * Math.toRadians(12.0);
			double cos = Math.cos(yawOffset);
			double sin = Math.sin(yawOffset);
			Vec3 dir = new Vec3(aim.x * cos - aim.z * sin, aim.y, aim.x * sin + aim.z * cos).normalize();
			WitherSkull skull = new WitherSkull(serverLevel, self, dir);
			skull.snapTo(originX, originY, originZ, self.getYRot(), self.getXRot());
			if (dangerous) {
				skull.setDangerous(true);
			}
			((VolleySkull) skull).superboss$setVolleyId(volleyId);
			skull.shoot(dir.x, dir.y, dir.z, 0.9F, 1.0F);
			serverLevel.addFreshEntity(skull);
		}
		serverLevel.playSound(null, originX, originY, originZ, SoundEvents.WITHER_SHOOT,
				self.getSoundSource(), 2.0F, (self.getRandom().nextFloat() - self.getRandom().nextFloat()) * 0.2F + 0.4F);
	}
}
