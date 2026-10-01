package net.mile.superboss.mixin;

import net.mile.superboss.SuperBossConfig;
import net.mile.superboss.SuperBossPillars;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 末影水晶不再放在柱顶,而是嵌进柱身里(默认约 3/4 高度处):想打水晶得先挖开黑曜石,
 * 弓箭打不到。基岩底座和火仍留在柱顶当"空底座"。世界生成和重生末影龙都走这里。
 */
@Mixin(EndSpikeFeature.class)
public abstract class EndSpikeFeatureMixin {
	@Unique
	private int superboss$spikeTopY;

	@Redirect(method = "placeSpike",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;snapTo(DDDFF)V"))
	private void superboss$embedCrystal(EndCrystal crystal, double x, double y, double z, float yRot, float xRot) {
		superboss$spikeTopY = (int) (y - 1.0); // 原版 y = 柱高 + 1
		crystal.snapTo(x, SuperBossPillars.embedY(crystal.level().getMinY(), superboss$spikeTopY), z, yRot, xRot);
	}

	@Redirect(method = "placeSpike",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;blockPosition()Lnet/minecraft/core/BlockPos;"))
	private BlockPos superboss$keepTopDecor(EndCrystal crystal) {
		// 基岩底座和火仍放在柱顶(原版位置),别跟着水晶埋进柱身
		return BlockPos.containing(crystal.getX(), superboss$spikeTopY + 1.0, crystal.getZ());
	}
}
