package net.mile.superboss.mixin;

import net.mile.superboss.SuperDragonBreath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 超级龙息弹落地时走 SuperDragonBreath 自己的逻辑(直接伤害 + 清增益 + 大毒云),
 * 跳过原版的小龙息云;普通弹幕火球不受影响。
 */
@Mixin(DragonFireball.class)
public abstract class DragonFireballMixin {
	@Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
	private void superboss$superBreathImpact(HitResult hitResult, CallbackInfo ci) {
		DragonFireball self = (DragonFireball) (Object) this;
		if (!self.entityTags().contains(SuperDragonBreath.SUPER_TAG)) {
			return;
		}
		if (self.level() instanceof ServerLevel serverLevel) {
			SuperDragonBreath.impact(serverLevel, self);
		}
		ci.cancel();
	}
}
