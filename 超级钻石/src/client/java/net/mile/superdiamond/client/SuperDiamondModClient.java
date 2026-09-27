package net.mile.superdiamond.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.mile.superdiamond.SuperAttributes;
import net.mile.superdiamond.SuperDiamondMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class SuperDiamondModClient implements ClientModInitializer {
	/** 进世界时给玩家的署名提示 */
	private static final Component GREETING = Component.literal("[超级钻石] zym制造")
			.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)));

	@Override
	public void onInitializeClient() {
		// 挂在原版护甲条后面:用紫色图标盖住护甲条左侧的一部分
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.ARMOR_BAR,
				SuperDiamondMod.id("purple_armor_bar"),
				new PurpleArmorBar());

		// 进世界时提示 zym制造
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.player != null) {
				client.player.sendSystemMessage(GREETING);
			}
		});
	}

	/**
	 * 紫色护甲条:与原版白条同一条、同一行,紫色图标从最左侧开始覆盖白色图标。
	 * 坐标计算与 26.2 原版 Hud.extractArmor / extractPlayerHealth 完全一致。
	 */
	static final class PurpleArmorBar implements HudElement {
		private static final Identifier PURPLE_FULL_SPRITE = SuperDiamondMod.id("hud/armor_purple_full");
		private static final Identifier PURPLE_HALF_SPRITE = SuperDiamondMod.id("hud/armor_purple_half");

		@Override
		public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
			Minecraft minecraft = Minecraft.getInstance();
			Player player = minecraft.player;
			if (player == null || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
				return; // 创造/旁观不显示,与原版一致
			}
			if (player.getArmorValue() <= 0) {
				return; // 原版护甲条本身不画,紫色条也不画
			}
			int purpleArmor = Mth.floor(player.getAttributeValue(SuperAttributes.PURPLE_ARMOR) + 0.5);
			if (purpleArmor <= 0) {
				return;
			}

			int startX = gui.guiWidth() / 2 - 91;
			float effectiveHealth = Math.max(player.getMaxHealth(), player.getHealth());
			int heartRows = Mth.ceil((effectiveHealth + player.getAbsorptionAmount()) / 2.0F / 10.0F);
			int rowSpacing = Math.max(12 - heartRows, 3);
			int y = (gui.guiHeight() - 39) - (heartRows - 1) * rowSpacing - 10;

			// 与原版一致:每 2 点护甲一个图标,9x9,横向间距 8px,只画满格和半格
			for (int i = 0; i < 10; i++) {
				int threshold = i * 2 + 1;
				if (threshold < purpleArmor) {
					gui.blitSprite(RenderPipelines.GUI_TEXTURED, PURPLE_FULL_SPRITE, startX + i * 8, y, 9, 9);
				} else if (threshold == purpleArmor) {
					gui.blitSprite(RenderPipelines.GUI_TEXTURED, PURPLE_HALF_SPRITE, startX + i * 8, y, 9, 9);
					break;
				} else {
					break;
				}
			}
		}
	}
}
