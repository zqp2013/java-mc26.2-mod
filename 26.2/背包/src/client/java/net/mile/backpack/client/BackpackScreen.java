package net.mile.backpack.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.mile.backpack.BackpackMenu;
import net.mile.backpack.payload.SetBackpackViewPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import org.lwjgl.glfw.GLFW;

/**
 * 背包物品栏界面:上半部分 = 原版生存物品栏布局(盔甲/合成 2x2/玩家模型/玩家背包),
 * 下半部分 = 背包内容区。合成终端变体 = 3x3 合成格 + 产物,无玩家模型。
 * 超级储存终端 = 搜索框 + 翻页(每页 18 格)。面板全部手绘,玩家模型沿用原版渲染。
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {

	private EditBox searchBox;
	private Button prevPage;
	private Button nextPage;
	private int page = 0;
	private String search = "";
	/** 内容变化时重算页数/视图(stateId 变了就是变了) */
	private int lastStateId = -1;
	private int cachedPages = 1;

	public BackpackScreen(BackpackMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, menu.panelWidth, menu.panelHeight);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 73;
	}

	@Override
	protected void init() {
		super.init();
		if (this.menu.type.paged()) {
			int x = this.leftPos;
			int y = this.topPos;
			int w = this.menu.panelWidth;
			this.searchBox = new EditBox(this.font, x + 8, y + 176, w - 48, 14,
					Component.translatable("gui.backpack.search_hint"));
			this.searchBox.setHint(Component.translatable("gui.backpack.search_hint"));
			this.searchBox.setValue(this.search);
			this.searchBox.setResponder(s -> {
				this.search = s;
				this.setPage(0);
			});
			this.addRenderableWidget(this.searchBox);
			this.prevPage = Button.builder(Component.literal("<"), b -> this.setPage(this.page - 1))
					.bounds(x + w - 38, y + 176, 16, 14).build();
			this.nextPage = Button.builder(Component.literal(">"), b -> this.setPage(this.page + 1))
					.bounds(x + w - 20, y + 176, 16, 14).build();
			this.addRenderableWidget(this.prevPage);
			this.addRenderableWidget(this.nextPage);
			this.refreshView(true);
		}
	}

	private void setPage(int newPage) {
		this.page = Math.max(0, Math.min(newPage, this.cachedPages - 1));
		this.refreshView(true);
	}

	/** 本地应用视图;send 时把页码/搜索词同步给服务端 */
	private void refreshView(boolean send) {
		this.cachedPages = Math.max(1, this.menu.pageCount(this.search));
		this.page = Math.max(0, Math.min(this.page, this.cachedPages - 1));
		this.lastStateId = this.menu.getStateId();
		this.menu.applyView(this.page, this.search);
		if (send) {
			ClientPlayNetworking.send(new SetBackpackViewPayload(this.page, this.search));
		}
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

		// 翻页状态刷新(内容变化 → 重算页数并校正当前页)
		if (this.menu.type.paged()) {
			int stateId = this.menu.getStateId();
			if (stateId != this.lastStateId) {
				this.lastStateId = stateId;
				this.cachedPages = Math.max(1, this.menu.pageCount(this.search));
				int clamped = Math.max(0, Math.min(this.page, this.cachedPages - 1));
				if (clamped != this.page) {
					this.setPage(clamped);
				} else {
					this.menu.applyView(this.page, this.search);
				}
			}
		}

		// 所有槽位盒(非激活的分页槽会被跳过)
		for (Slot slot : this.menu.slots) {
			if (slot.isActive()) {
				drawSlotBox(gui, x + slot.x - 1, y + slot.y - 1);
			}
		}

		// 合成格 → 产物格 的箭头(贴着产物槽左侧,随 2x2/3x3 布局走)
		Slot result = this.menu.getResultSlot();
		if (result != null) {
			drawArrow(gui, x + result.x - 14, y + result.y + 4);
		}

		// 背包区标题底下的分隔
		gui.fill(x + 2, y + 165, x + w - 2, y + 166, 0xFF8B8B8B);

		// 玩家模型(原版参数:区域 (26,8)-(75,78);合成终端布局没有这块空间)
		if (!this.menu.craftingGrid && this.minecraft != null && this.minecraft.player != null) {
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

	/** 12x7 的右向箭头 */
	private static void drawArrow(GuiGraphicsExtractor gui, int x, int y) {
		gui.fill(x, y + 2, x + 8, y + 5, 0xFF555555);    // 杆
		gui.fill(x + 8, y + 1, x + 10, y + 6, 0xFF555555); // 头:左段
		gui.fill(x + 10, y, x + 12, y + 7, 0xFF555555);   // 头:尖
	}

	@Override
	public void extractLabels(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
		super.extractLabels(gui, mouseX, mouseY);
		// 背包区标题:名称 + 格数
		Component label = Component.translatable("gui.backpack.area",
				this.menu.type.title, this.menu.type.slots);
		gui.text(this.font, label, 8, 167, 0xFF404040, false);
		// 分页信息 + 按钮可用状态
		if (this.menu.type.paged()) {
			String pageInfo = (this.page + 1) + "/" + this.cachedPages;
			gui.text(this.font, pageInfo, this.imageWidth - 8 - this.font.width(pageInfo), 167, 0xFF404040, false);
			this.prevPage.active = this.page > 0;
			this.nextPage.active = this.page < this.cachedPages - 1;
		}
	}

	// ---- 搜索框焦点/输入(事件对象 API) ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		if (this.searchBox != null && this.searchBox.mouseClicked(event, doubled)) {
			this.setFocused(this.searchBox);
			return true;
		}
		return super.mouseClicked(event, doubled);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.searchBox != null && this.searchBox.isFocused()) {
			if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
				this.searchBox.setFocused(false);
				return true;
			}
			this.searchBox.keyPressed(event);
			return true; // 焦点在搜索框时吞掉按键,防止 E 键顺手关界面
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.searchBox != null && this.searchBox.isFocused()) {
			return this.searchBox.charTyped(event);
		}
		return super.charTyped(event);
	}
}
