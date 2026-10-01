package net.mile.superboss.mixin;

import net.mile.superboss.Advancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.dimension.end.DragonRespawnStage;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在祭坛四周摆末影水晶成功触发末影龙复活 → "再次折磨"。
 * 挂在复活流程真正启动(状态进入 STARTING)的那一刻,水晶摆少了不算。
 */
@Mixin(EnderDragonFight.class)
public abstract class EnderDragonFightMixin {

	@Shadow
	private ServerLevel level;

	@Inject(method = "setRespawnStage", at = @At("HEAD"))
	private void superboss$trackRespawnStart(DragonRespawnStage stage, CallbackInfo ci) {
		if (stage != DragonRespawnStage.START) {
			return;
		}
		for (ServerPlayer player : this.level.players()) {
			Advancements.grant(player, "respawn_dragon");
		}
	}
}
