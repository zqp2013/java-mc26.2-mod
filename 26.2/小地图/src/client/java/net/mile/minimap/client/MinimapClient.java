package net.mile.minimap.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

public class MinimapClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("minimap");

	public static KeyMapping openMapKey;

	@Override
	public void onInitializeClient() {
		// 按键:默认 P 打开大地图,可在 控件设置 里改
		// (26.3: KEYSYM 改名 KEYBOARD,GLFW 常量换成 InputConstants 自带的)
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		openMapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.minimap.open_map", InputConstants.Type.KEYBOARD, InputConstants.KEY_P, category));

		// 右上角小地图 HUD
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("minimap"),
				(graphics, deltaTracker) -> MinimapHud.render(graphics, deltaTracker));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && client.level != null) {
				// 检测钻进矿洞/回到地表,切换地图层(返回 true = 刚切层,触发重绘)
				if (MapData.tickCaveMode(client.level, client.player)) {
					MinimapHud.reset();
				}
				// 传送门切维度:清地形缓存,重新扫存档
				if (MapData.dimensionChanged(client.level)) {
					MapData.clear();
					MapData.updateDimension(client.level);
					MinimapHud.reset();
					RegionScanner.reset();
					RegionScanner.requestScan(client);
				}
				if (client.gui.screen() == null && openMapKey.consumeClick()) {
					client.gui.setScreen(new MapScreen());
				}
				// 定期补扫:玩家走远后把新区域的存档地形灌进来
				if (client.player.tickCount % 600 == 0) {
					RegionScanner.requestScan(client);
				}
			}
			MinimapStore.tickDeath(client.player);
			MinimapHud.tick(client);
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.level != null) {
				MapData.updateDimension(client.level);
			}
			MapData.clear();
			MinimapStore.onJoin(client);
			MinimapHud.reset();
			RegionScanner.reset();
			RegionScanner.requestScan(client);
			if (client.player != null) {
				client.player.sendSystemMessage(Component.literal("[小地图] zym制造")
						.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00))));
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MinimapStore.onLeave();
			MapData.clear();
			MinimapHud.reset();
			RegionScanner.reset();
		});
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("minimap", path);
	}
}
