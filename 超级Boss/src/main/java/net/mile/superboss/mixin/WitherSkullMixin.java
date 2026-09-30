package net.mile.superboss.mixin;

import net.mile.superboss.Advancements;
import net.mile.superboss.BossState;
import net.mile.superboss.SuperBossConfig;
import net.mile.superboss.VolleySkull;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 凋灵骷髅头:爆炸半径翻倍(1 格 → 2 格)、范围内追加直接伤害(≈伤害翻倍)与明显击退;
 * 被炸到的玩家拿进度,同一次齐射的 3 个头颅都炸到 → "把伤害吃满了"。
 */
@Mixin(targets = "net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull")
public abstract class WitherSkullMixin implements VolleySkull {

	@Unique
	private int superboss$volleyId = 0;

	@Override
	public void superboss$setVolleyId(int volleyId) {
		this.superboss$volleyId = volleyId;
	}

	@Override
	public int superboss$getVolleyId() {
		return this.superboss$volleyId;
	}

	@Redirect(method = "onHit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)V"))
	private void superboss$biggerBlastWithKnockback(Level level, Entity source, double x, double y, double z,
			float radius, boolean fire, Level.ExplosionInteraction interaction) {
		float boostedRadius = radius * SuperBossConfig.WITHER_SKULL_BLAST_MULTIPLIER;
		level.explode(source, x, y, z, boostedRadius, fire, interaction);

		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		// 这颗头颅属于哪次齐射(用于"3 个都炸到"判定)
		int volleyId = source instanceof VolleySkull skull ? skull.superboss$getVolleyId() : 0;
		LivingEntity owner = source instanceof Projectile projectile
				&& projectile.getOwner() instanceof LivingEntity livingOwner ? livingOwner : null;
		DamageSource extraDamage = serverLevel.damageSources().explosion(source, owner);

		// 追加直接伤害(伤害翻倍) + 击退:离爆心越近越狠
		double range = boostedRadius * 2.0;
		for (LivingEntity living : serverLevel.getEntitiesOfClass(LivingEntity.class,
				new AABB(x - range, y - range, z - range, x + range, y + range, z + range))) {
			if (living == source || living == owner || !living.isAlive()) {
				continue;
			}
			Vec3 away = living.position().subtract(x, y, z);
			double distance = away.length();
			if (distance > range) {
				continue;
			}
			double strength = (1.0 - distance / range) * SuperBossConfig.WITHER_SKULL_KNOCKBACK;
			Vec3 direction = distance < 0.01 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
			living.push(direction.x * strength,
					direction.y * strength * 0.6 + 0.25 * strength,
					direction.z * strength);
			living.hurtMarked = true;
			// 爆炸范围翻倍的同时追加直接伤害
			living.hurtServer(serverLevel, extraDamage, SuperBossConfig.WITHER_SKULL_EXTRA_DAMAGE);
			if (living instanceof ServerPlayer player) {
				Advancements.grant(player, "wither_skull_hit");
				BossState.recordVolleyHit(player, volleyId);
			}
		}
	}
}
