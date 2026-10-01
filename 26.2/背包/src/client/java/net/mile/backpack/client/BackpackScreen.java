package net.mile.backpack.client;

import net.mile.backpack.BackpackMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 背包物品栏界面:上半部分 = 原版生存物品栏布局(盔甲/合成2x2/玩家模型/玩家背包),
 * 下半部分 = 背包内容区。面板全部手绘(适配 15/18 列宽布局),玩家模型沿用原版渲染。
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {

	public BackpackScreen(BackpackMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, menu.type.imageWidth(), menu.type.imageHeight());
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 73;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(gui, mouseX, mouseY, partialTick);
		int x = this.leftPos;
		int y = this.topPos;
		int w = this.imageWidth;
		int h = this.imageHeight;

		// 面板:1px 黑边 + 1px 白边 + 灰底(原版风格)
		gui.fill(x, y, x + w, y + h, 0xFF000000);
		gui.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
		gui.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFC6C6C6);

		// 所有槽位盒
		for (Slot slot : this.menu.slots) {
			if (slot.isActive()) {
				drawSlotBox(gui, x + slot.x - 1, y + slot.y - 1);
			}
		}

		// 合成格 → 产物格 的箭头
		drawArrow(gui, x + 135, y + 32);

		// 背包区标题底下的分隔
		gui.fill(x + 2, y + 165, x + w - 2, y + 166, 0xFF8B8B8B);

		// 玩家模型(原版参数:区域 (26,8)-(75,78),与盔甲/合成格之间的空间对齐)
		if (this.minecraft != null && this.minecraft.player != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(gui,
					x + 26, y + 8, x + 75, y + 78, 30, 0.0625f, mouseX, mouseY, this.minecraft.player);
		}
	}

	private static void drawSlotBox(GuiGraphicsExtractor gui, int x, int y) {
		gui.fill(x, y, x + 18, y + 18, 0xFF373737);
		gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
		gui.fill(x + 1, y + 1, x + 17, y + 2, 0xFFFFFFFF);
		gui.fill(x + 1, y + 1, x + 2, y + 17, 0xFFFFFFFF);
	}

	/** 11x5 的右向箭头 */
	private static void drawArrow(GuiGraphicsExtractor gui, int x, int y) {
		gui.fill(x, y + 2, x + 8, y + 3, 0xFF555555);   // 杆
		gui.fill(x + 4, y, x + 5, y + 5, 0xFF555555);   // 头:最宽列
		gui.fill(x + 5, y + 1, x + 7, y + 4, 0xFF555555); // 头:中段
		gui.fill(x + 7, y + 2, x + 8, y + 3, 0xFF555555); // 头:尖
	}

	@Override
	public void extractLabels(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
		super.extractLabels(gui, mouseX, mouseY);
		// 背包区标题:名称 + 格数
		Component label = Component.translatable("gui.backpack.area",
				this.menu.type.title, this.menu.type.slots);
		gui.text(this.font, label, 8, 167, 0xFF404040, false);
	}
}
