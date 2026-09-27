package net.mile.superboss.mixin;

import net.mile.superboss.WardenBossBars;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 坚守者被移除(死亡清理/钻地消失/卸载)时,顺手清掉它的 Boss 血条。
 * Warden 本身没有重写 remove,所以要挂在 Entity 上。
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

	@Inject(method = "remove", at = @At("TAIL"))
	private void superboss$clearWardenBossBar(Entity.RemovalReason reason, CallbackInfo ci) {
		if ((Object) this instanceof Warden warden) {
			WardenBossBars.remove(warden);
		}
	}
}
