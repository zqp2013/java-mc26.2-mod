package net.mile.superfurnace.mixin;

import net.mile.superfurnace.SuperFurnaceBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版漏斗成功搬运一次后冷却 8 刻(0.4 秒,每秒 2.5 个)。
 * 挨着超级熔炉的漏斗冷却缩到 1 刻 → 每个游戏刻都能搬一次(20 个/秒,8 倍速);
 * 其他漏斗保持原版节奏不变。
 * 原版 26.2 里 setCooldown 只有两个调用点:pushItemsTick 的 setCooldown(0)(进搬运前清零,
 * 放行不管)和 tryMoveItems 成功后的 setCooldown(8)(就是这里要改的)。
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {
	@Shadow
	private int cooldownTime;

	@Inject(method = "setCooldown", at = @At("HEAD"), cancellable = true)
	private void superfurnace$fasterNearFurnace(int cooldown, CallbackInfo ci) {
		if (cooldown == 8 && this.superfurnace$isNearSuperFurnace()) {
			this.cooldownTime = 1;
			ci.cancel();
		}
	}

	/** 漏斗六面中任意一面贴着超级熔炉就算(上面吸、朝向送、侧面/底下靠着都行) */
	private boolean superfurnace$isNearSuperFurnace() {
		HopperBlockEntity self = (HopperBlockEntity) (Object) this;
		Level level = self.getLevel();
		if (level == null || level.isClientSide()) {
			return false;
		}
		var pos = self.getBlockPos();
		for (Direction dir : Direction.values()) {
			if (level.getBlockEntity(pos.relative(dir)) instanceof SuperFurnaceBlockEntity) {
				return true;
			}
		}
		return false;
	}
}
