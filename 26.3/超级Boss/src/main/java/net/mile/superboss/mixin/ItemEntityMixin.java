package net.mile.superboss.mixin;

import net.mile.superboss.BossState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 捡起"凋零召唤的骷髅"掉落的凋灵骷髅头颅 → 计入"还能召唤凋零吗？"进度。
 * 数量在 HEAD 先记下(捡起后堆叠会被清空),TAIL 确认真的进了背包才计数。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

	@Unique
	private int superboss$stackCountBeforeTouch;

	@Inject(method = "playerTouch", at = @At("HEAD"))
	private void superboss$rememberStack(Player player, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		this.superboss$stackCountBeforeTouch = self.getItem().getItem() == Items.WITHER_SKELETON_SKULL
				? self.getItem().getCount()
				: 0;
	}

	@Inject(method = "playerTouch", at = @At("TAIL"))
	private void superboss$trackSummonedSkullPickup(Player player, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (!self.isRemoved()
				|| this.superboss$stackCountBeforeTouch <= 0
				|| !BossState.TRACKED_SKULL_ITEMS.remove(self.getId())
				|| !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		BossState.recordSummonedSkullPickup(serverPlayer, this.superboss$stackCountBeforeTouch);
	}
}
