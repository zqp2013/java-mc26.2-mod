package net.mile.backpack.mixin;

import net.mile.backpack.TerminalPickup;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家碰到掉落物时,先尝试把掉落物并进身上终端里已有的同类堆(见 TerminalPickup)。
 * 整堆吸光 → 删掉实体并取消原版拾取;只吸了一部分 → 剩下的走原版拾取(进玩家背包)。
 * 拾取延迟期(pickupDelay)内不吸,和原版节奏一致。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

	@Shadow
	private int pickupDelay;

	@Shadow
	public abstract ItemStack getItem();

	@Shadow
	public abstract void setItem(ItemStack stack);

	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void backpack$absorbIntoTerminal(Player player, CallbackInfo ci) {
		if (this.pickupDelay != 0) {
			return;
		}
		ItemStack stack = this.getItem();
		if (stack.isEmpty()) {
			return;
		}
		int absorbed = TerminalPickup.absorb(player, stack);
		if (absorbed <= 0) {
			return;
		}
		if (stack.isEmpty()) {
			((ItemEntity) (Object) this).discard();
			ci.cancel();
		} else {
			this.setItem(stack);
		}
	}
}
