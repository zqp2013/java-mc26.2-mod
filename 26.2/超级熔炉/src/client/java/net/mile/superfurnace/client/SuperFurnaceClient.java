package net.mile.superfurnace.client;

import net.mile.superfurnace.FurnaceTier;
import net.mile.superfurnace.SuperFurnaceMenu;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public class SuperFurnaceClient implements ClientModInitializer {
	/** 进世界时给玩家的署名提示 */
	private static final Component GREETING = Component.literal("[超级熔炉] zym制造")
			.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)));

	@Override
	public void onInitializeClient() {
		// 五个等级的菜单各自注册同一个 Screen 类(布局参数在菜单里)
		for (FurnaceTier tier : FurnaceTier.values()) {
			MenuScreens.<SuperFurnaceMenu, SuperFurnaceScreen>register(tier.menuType,
					(menu, inventory, title) -> new SuperFurnaceScreen(menu, inventory, title));
		}

		// 进世界时提示 zym制造
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.player != null) {
				client.player.sendSystemMessage(GREETING);
			}
		});
	}
}
