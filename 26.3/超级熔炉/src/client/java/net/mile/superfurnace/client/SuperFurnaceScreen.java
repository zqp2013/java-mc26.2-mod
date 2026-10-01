package net.mile.superfurnace.client;

import net.mile.superfurnace.SuperFurnaceMenu;
import net.mile.superfurnace.SuperFurnaceMod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * 超级熔炉界面:原版风格贴图面板(gen_gui.py 用原版 furnace.png 的
 * 边框/槽位/箭头/火焰拼出来),运行时叠原版 lit_progress 火焰和
 * burn_progress 箭头进度,火焰左侧、燃料列、输入网格、箭头、成品网格布局。
 */
public class SuperFurnaceScreen extends AbstractContainerScreen<SuperFurnaceMenu> {
	/** 贴图里"精灵货架"的 y:点燃火焰 (0,210) 14×14,箭头填充 (24,210) 24×16 */
	private static final int SHELF_Y = 210;

	private final Identifier texture;

	public SuperFurnaceScreen(SuperFurnaceMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, menu.imageWidth, menu.imageHeight);
		this.texture = Identifier.fromNamespaceAndPath(SuperFurnaceMod.MOD_ID,
				"textures/gui/" + menu.tier.name + ".png");
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = menu.invLabelY;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
		super.extractLabels(gui, mouseX, mouseY);
		// 右上角金色等级显示(可升级的等级才显示)
		if (menu.tier.maxLevel > 1) {
			String level = "等级 " + menu.getFurnaceLevel() + "/" + menu.tier.maxLevel;
			gui.text(this.font, level, imageWidth - this.font.width(level) - 8, 6, 0xFFFFAA00);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(gui, mouseX, mouseY, partialTick);
		int texW = menu.texWidth;

		// 原版风格面板
		gui.blit(RenderPipelines.GUI_TEXTURED, texture, leftPos, topPos, 0, 0,
				imageWidth, imageHeight, texW, 256);

		// 点燃的火焰:自下而上,按剩余燃烧时间比例显示原版 lit_progress
		int burnTime = menu.getBurnTime();
		int burnDuration = menu.getBurnDuration();
		if (burnTime > 0 && burnDuration > 0) {
			int fh = Math.min(14, Math.max(1, 14 * burnTime / burnDuration));
			gui.blit(RenderPipelines.GUI_TEXTURED, texture,
					leftPos + menu.flameX, topPos + menu.flameY + (14 - fh),
					0, SHELF_Y + (14 - fh), 14, fh, texW, 256);
		}

		// 箭头进度:一批同时烧的物品共用一条(原版 burn_progress)
		int progress = menu.getProgress();
		if (progress > 0) {
			int aw = Math.min(24, Math.max(1, 24 * progress / menu.tier.cookTicks));
			gui.blit(RenderPipelines.GUI_TEXTURED, texture,
					leftPos + menu.arrowX, topPos + menu.arrowY,
					24, SHELF_Y, aw, 16, texW, 256);
		}

		// 箭头下方显示本批同时烧几个
		int burning = menu.getBurningCount();
		if (burning > 0) {
			gui.text(this.font, "×" + burning, leftPos + menu.arrowX - 1, topPos + menu.barsY, 0xFF404040);
		}
	}
}
