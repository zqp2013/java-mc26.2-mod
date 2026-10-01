package net.mile.durabilitydisplay.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DurabilityDisplayClient implements ClientModInitializer {
	/** 进世界时给玩家的署名提示 */
	private static final Component GREETING = Component.literal("[耐久显示] zym制造")
			.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)));

	@Override
	public void onInitializeClient() {
		// 挂在原版 HUD 之后:屏幕右侧中间的耐久面板
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.MISC_OVERLAYS,
				DurabilityDisplayClient.id("durability_panel"),
				new DurabilityPanel());

		// 进世界时提示 zym制造
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.player != null) {
				client.player.sendSystemMessage(GREETING);
			}
		});
	}

	public static net.minecraft.resources.Identifier id(String path) {
		return net.minecraft.resources.Identifier.fromNamespaceAndPath("durabilitydisplay", path);
	}

	/**
	 * 耐久面板:屏幕右侧垂直居中,列出 头/胸/腿/脚/主手/副手 上所有带耐久值的物品。
	 * 每行 = 物品图标 + 剩余耐久数字(颜色沿用原版耐久条的绿→红渐变),悬停显示物品 tooltip。
	 * 没耐久物品的槽位不占行,面板随行数自适应高度与宽度。
	 */
	static final class DurabilityPanel implements HudElement {
		private static final EquipmentSlot[] SLOTS = {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
				EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
		};

		private static final int ROW_HEIGHT = 18;
		/** 图标右边距(距屏幕右缘) */
		private static final int RIGHT_MARGIN = 4;
		/** 文字与图标间距 */
		private static final int TEXT_GAP = 3;
		/** 面板背景色(半透明黑) */
		private static final int BACKGROUND_COLOR = 0x66000000;

		@Override
		public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
			Minecraft minecraft = Minecraft.getInstance();
			Player player = minecraft.player;
			if (player == null) {
				return;
			}

			// 收集所有带耐久值的装备
			List<ItemStack> items = new ArrayList<>();
			for (EquipmentSlot slot : SLOTS) {
				ItemStack stack = player.getItemBySlot(slot);
				if (!stack.isEmpty() && stack.isDamageableItem()) {
					items.add(stack);
				}
			}
			if (items.isEmpty()) {
				return;
			}

			Font font = minecraft.font;

			// 按最长的耐久数字对齐面板宽度
			int maxTextWidth = 0;
			for (ItemStack stack : items) {
				maxTextWidth = Math.max(maxTextWidth, font.width(durabilityText(stack)));
			}

			int screenWidth = gui.guiWidth();
			int screenHeight = gui.guiHeight();
			int iconX = screenWidth - RIGHT_MARGIN - 16;
			int panelTop = (screenHeight - items.size() * ROW_HEIGHT) / 2 + 1;

			// 面板背景(盖在聊天/其他 HUD 之上,半透明不遮挡)
			int bgLeft = iconX - maxTextWidth - TEXT_GAP - 2;
			int bgRight = iconX + 16 + 2;
			int bgTop = panelTop - 2;
			int bgBottom = panelTop + items.size() * ROW_HEIGHT + 1;
			gui.fill(bgLeft, bgTop, bgRight, bgBottom, BACKGROUND_COLOR);

			// 鼠标位置换算成 GUI 缩放坐标,用于悬停 tooltip
			double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
			double mouseY = minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());

			for (int i = 0; i < items.size(); i++) {
				ItemStack stack = items.get(i);
				int y = panelTop + i * ROW_HEIGHT;

				// 物品图标 + 原版数量/耐久条装饰
				gui.item(stack, iconX, y);
				gui.itemDecorations(font, stack, iconX, y);

				// 剩余耐久,颜色沿用原版耐久条(满耐久绿,快坏红);text 的颜色必须带 alpha,否则直接不画
				String text = durabilityText(stack);
				int color = 0xFF000000 | stack.getBarColor();
				gui.text(font, text, iconX - TEXT_GAP - font.width(text), y + 4, color, true);

				// 悬停在图标上时显示物品 tooltip
				if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= y && mouseY < y + 16) {
					gui.setTooltipForNextFrame(font, stack, (int) mouseX, (int) mouseY);
				}
			}
		}

		private static String durabilityText(ItemStack stack) {
			return String.valueOf(stack.getMaxDamage() - stack.getDamageValue());
		}
	}
}
