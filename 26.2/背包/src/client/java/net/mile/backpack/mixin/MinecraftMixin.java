package net.mile.backpack.mixin;

import net.mile.backpack.client.BackpackClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 按 E 时,如果装备了背包,把原版物品栏替换成背包物品栏:
 * 在 handleKeybinds 开头把 keyInventory 的点击消费掉并发包给服务端开菜单,
 * 原版后面的 while(consumeClick) 循环就不会再开原版界面。
 * 没装备背包时什么都不做,原版行为不变。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void backpack$openBackpackInstead(CallbackInfo ci) {
		Minecraft self = (Minecraft) (Object) this;
		LocalPlayer player = self.player;
		if (player == null || self.gui.screen() != null || self.options == null) {
			return;
		}
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		if (!BackpackClient.hasEquippedBackpack(player)) {
			return;
		}
		while (self.options.keyInventory.consumeClick()) {
			BackpackClient.sendOpenBackpack();
		}
	}
}
