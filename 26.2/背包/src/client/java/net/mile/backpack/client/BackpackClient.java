package net.mile.backpack.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.mile.backpack.BackpackMenu;
import net.mile.backpack.BackpackMod;
import net.mile.backpack.BackpackType;
import net.mile.backpack.payload.OpenBackpackPayload;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

public class BackpackClient implements ClientModInitializer {

	/** 进世界时给玩家的署名提示 */
	private static final Component GREETING = Component.literal("[背包] zym制造")
			.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)));

	@Override
	public void onInitializeClient() {
		// 三档背包各一个菜单类型,布局固定
		for (BackpackType type : BackpackType.values()) {
			MenuScreens.<BackpackMenu, BackpackScreen>register(type.menuType(), BackpackScreen::new);
		}

		// 进世界时提示 zym制造
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.player != null) {
				client.player.sendSystemMessage(GREETING);
			}
		});
	}

	/** 客户端是否装备着背包(E 键拦截判断用;附件已 syncWith 玩家本人) */
	public static boolean hasEquippedBackpack(LocalPlayer player) {
		if (player == null) {
			return false;
		}
		ItemStack equipped = player.getAttachedOrElse(BackpackMod.EQUIPPED_BACKPACK, ItemStack.EMPTY);
		return BackpackType.fromItem(equipped.getItem()) != null;
	}

	public static void sendOpenBackpack() {
		ClientPlayNetworking.send(OpenBackpackPayload.INSTANCE);
	}
}
