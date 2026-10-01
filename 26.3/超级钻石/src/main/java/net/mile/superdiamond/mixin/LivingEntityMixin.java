package net.mile.superdiamond.mixin;

import net.mile.superdiamond.SuperDamageReduction;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在原版最后一环减伤(护甲+药水效果)之后应用超级下界合金的最终减伤。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void superdiamond$applyFinalDamageReduction(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		float original = cir.getReturnValueF();
		float reduced = SuperDamageReduction.applyFinalReduction((LivingEntity) (Object) this, source, original);
		if (reduced != original) {
			cir.setReturnValue(reduced);
		}
	}
}
