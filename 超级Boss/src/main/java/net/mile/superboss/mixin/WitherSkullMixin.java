package net.mile.superboss.mixin;

import net.mile.superboss.SuperBossConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 凋灵骷髅头:爆炸半径翻倍(1 格 → 2 格),并对范围内生物追加明显击退。
 */
@Mixin(targets = "net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull")
public abstract class WitherSkullMixin {

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
		// 追加击退:离爆心越近推得越远
		double range = boostedRadius * 2.0;
		for (LivingEntity living : serverLevel.getEntitiesOfClass(LivingEntity.class,
				new AABB(x - range, y - range, z - range, x + range, y + range, z + range))) {
			if (living == source || !living.isAlive()) {
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
		}
	}
}
