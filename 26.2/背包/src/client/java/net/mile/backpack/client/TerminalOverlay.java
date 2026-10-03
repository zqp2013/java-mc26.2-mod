package net.mile.backpack.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.mile.backpack.BackpackContents;
import net.mile.backpack.BackpackMenu;
import net.mile.backpack.BackpackMod;
import net.mile.backpack.BackpackType;
import net.mile.backpack.payload.TerminalClickPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 箱子/熔炉等容器界面右侧的终端面板:显示身上储存终端/超级终端里的物品,
 * 点击直接存取——左键拿一捧/放下手上的,右键拿一半/放 1 个,shift+左键整堆塞玩家背包。
 * 面板纯客户端渲染,交互走 TerminalClickPayload 由服务端对着终端组件操作;
 * 超级终端面板有搜索条(点它打字),页码用 TerminalPages 跨界面记忆。
 */
public final class TerminalOverlay {

	private static final int COLS = 9;
	private static final int ROWS = 2;
	private static final int PAGE_SLOTS = COLS * ROWS;
	private static final int PANEL_W = 8 + COLS * 18 + 8;
	private static final int PANEL_H = 94;

	// 界面切换时重置(页码从记忆恢复)
	private static AbstractContainerScreen<?> lastScreen;
	private static int page = 0;
	private static String search = "";
	private static boolean searchFocused = false;

	// 面板几何(渲染时刷新,点击命中复用)
	private static int panelX;
	private static int panelY;
	private static int gridX;
	private static int gridY;
	private static int searchX;
	private static int searchY;
	private static int searchW;
	private static int prevX;
	private static int nextX;
	private static int arrowY;

	// 搜索匹配缓存(附件列表实例没变就不重算)
	private static List<ItemStack> cachedStacks;
	private static String cachedSearch;
	private static List<Integer> cachedMatches;

	private TerminalOverlay() {
	}

	// ---- 渲染(mixin 挂在 AbstractContainerScreen.extractContents 尾部,绝对坐标) ----

	public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor gui, Font font,
			int leftPos, int topPos, int imageWidth, int screenWidth, int mouseX, int mouseY) {
		BackpackType type = equippedType();
		if (type == null || !showFor(screen)) {
			return;
		}
		if (screen != lastScreen) {
			lastScreen = screen;
			page = TerminalPages.load(type);
			search = "";
			searchFocused = false;
		}
		layout(leftPos, topPos, imageWidth, screenWidth);

		List<ItemStack> stacks = stacksOf(type);
		List<Integer> matches = searchActive() ? matchesOf(type, stacks) : null;
		int pages = pageCount(type, stacks, matches);
		if (page > pages - 1) {
			page = pages - 1;
		}

		// 面板:1px 黑边 + 1px 白边 + 灰底
		gui.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF000000);
		gui.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + PANEL_H - 1, 0xFFFFFFFF);
		gui.fill(panelX + 2, panelY + 2, panelX + PANEL_W - 2, panelY + PANEL_H - 2, 0xFFC6C6C6);

		gui.text(font, type == BackpackType.SUPER ? "超级终端" : "储存终端",
				panelX + 8, panelY + 6, 0xFF404040, false);

		// 搜索条
		gui.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + 13,
				searchFocused ? 0xFFFFFFFF : 0xFF555555);
		gui.fill(searchX, searchY, searchX + searchW, searchY + 12, 0xFF000000);
		gui.text(font, search.isEmpty() ? "搜索…" : search, searchX + 3, searchY + 2,
				search.isEmpty() ? 0xFF808080 : 0xFFFFFFFF, false);

		// 格子
		for (int p = 0; p < PAGE_SLOTS; p++) {
			int cx = gridX + (p % COLS) * 18;
			int cy = gridY + (p / COLS) * 18;
			BackpackScreen.drawSlotBox(gui, cx - 1, cy - 1);
			int real = realIndex(type, matches, p);
			if (real < 0 || real >= stacks.size()) {
				continue;
			}
			ItemStack stack = stacks.get(real);
			if (stack.isEmpty()) {
				continue;
			}
			gui.item(stack, cx, cy);
			gui.itemDecorations(font, stack, cx, cy);
			if (mouseX >= cx && mouseX < cx + 16 && mouseY >= cy && mouseY < cy + 16) {
				gui.setTooltipForNextFrame(font, stack, cx, cy);
			}
		}

		// 翻页
		String pageInfo = (page + 1) + "/" + pages;
		gui.text(font, "<", prevX + 5, arrowY, page > 0 ? 0xFF404040 : 0xFFAAAAAA, false);
		gui.text(font, ">", nextX + 5, arrowY, page < pages - 1 ? 0xFF404040 : 0xFFAAAAAA, false);
		gui.text(font, pageInfo, panelX + PANEL_W / 2 - font.width(pageInfo) / 2, arrowY, 0xFF404040, false);
	}

	// ---- 点击(mixin 头部拦截;返回 true = 面板消费了这次点击) ----

	public static boolean handleClick(AbstractContainerScreen<?> screen, MouseButtonEvent event,
			int leftPos, int topPos, int imageWidth, int screenWidth) {
		BackpackType type = equippedType();
		if (type == null || !showFor(screen)) {
			return false;
		}
		layout(leftPos, topPos, imageWidth, screenWidth);
		int mx = (int) event.x();
		int my = (int) event.y();
		if (mx < panelX || mx >= panelX + PANEL_W || my < panelY || my >= panelY + PANEL_H) {
			searchFocused = false; // 点面板外 = 取消搜索焦点
			return false;
		}
		int button = event.button();
		if (button != 0 && button != 1) {
			return true;
		}
		if (my >= arrowY - 2 && my < arrowY + 12) {
			if (mx >= prevX && mx < prevX + 16) {
				flipPage(type, -1);
			} else if (mx >= nextX && mx < nextX + 16) {
				flipPage(type, 1);
			} else {
				searchFocused = false;
			}
			return true;
		}
		if (my >= searchY - 2 && my < searchY + 14 && mx >= searchX && mx < searchX + searchW) {
			searchFocused = true;
			return true;
		}
		searchFocused = false;
		if (my >= gridY && my < gridY + ROWS * 18 && mx >= gridX && mx < gridX + COLS * 18) {
			if (vanillaSlotAt(screen, event.x(), event.y()) != null) {
				return false; // 空间太挤和原版槽位重叠时,让原版优先
			}
			int p = (my - gridY) / 18 * COLS + (mx - gridX) / 18;
			List<ItemStack> stacks = stacksOf(type);
			List<Integer> matches = searchActive() ? matchesOf(type, stacks) : null;
			int real = realIndex(type, matches, p);
			if (real >= 0 && real < stacks.size()) {
				ClientPlayNetworking.send(new TerminalClickPayload(real, button == 1, event.hasShiftDown()));
			}
		}
		return true;
	}

	// ---- 搜索条输入(mixin 头部拦截;焦点在搜索条时吞掉按键) ----

	public static boolean keyPressed(KeyEvent event) {
		if (!searchFocused) {
			return false;
		}
		// 26.3: GLFW 不再暴露,键码常量改用 InputConstants(KEY_RETURN=主回车,KEY_NUMPADENTER=小键盘回车)
		if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RETURN
				|| event.key() == InputConstants.KEY_NUMPADENTER) {
			searchFocused = false;
			return true;
		}
		if (event.key() == InputConstants.KEY_BACKSPACE) {
			if (!search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
				page = 0;
			}
			return true;
		}
		return true; // 防 E 键顺手关界面/数字键交换
	}

	public static boolean charTyped(CharacterEvent event) {
		if (!searchFocused) {
			return false;
		}
		String s = event.codepointAsString();
		if (!s.isEmpty() && search.length() < 20 && event.isAllowedChatCharacter()) {
			search += s;
			page = 0;
		}
		return true;
	}

	// ---- 内部 ----

	private static boolean searchActive() {
		return !search.trim().isEmpty();
	}

	private static boolean showFor(AbstractContainerScreen<?> screen) {
		return !(screen instanceof BackpackScreen)
				&& !(screen instanceof CreativeModeInventoryScreen)
				&& !(screen.getMenu() instanceof BackpackMenu);
	}

	private static BackpackType equippedType() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		ItemStack equipped = player.getAttachedOrElse(BackpackMod.EQUIPPED_BACKPACK, ItemStack.EMPTY);
		BackpackType type = BackpackType.fromItem(equipped.getItem());
		return type != null && type.unlimited() ? type : null;
	}

	/** 终端内容(不足槽位补 EMPTY;SUPER 全满时直接返回组件列表,身份稳定可做缓存键) */
	private static List<ItemStack> stacksOf(BackpackType type) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return List.of();
		}
		ItemStack equipped = player.getAttachedOrElse(BackpackMod.EQUIPPED_BACKPACK, ItemStack.EMPTY);
		if (BackpackType.fromItem(equipped.getItem()) != type) {
			return List.of();
		}
		List<ItemStack> saved = BackpackContents.read(equipped);
		if (saved.size() >= type.slots) {
			return saved;
		}
		List<ItemStack> padded = new ArrayList<>(saved);
		while (padded.size() < type.slots) {
			padded.add(ItemStack.EMPTY);
		}
		return padded;
	}

	private static int pageCount(BackpackType type, List<ItemStack> stacks, List<Integer> matches) {
		int count = matches != null ? matches.size() : type.slots;
		return Math.max(1, (count + PAGE_SLOTS - 1) / PAGE_SLOTS);
	}

	private static void flipPage(BackpackType type, int dir) {
		List<ItemStack> stacks = stacksOf(type);
		List<Integer> matches = searchActive() ? matchesOf(type, stacks) : null;
		int pages = pageCount(type, stacks, matches);
		page = Math.max(0, Math.min(page + dir, pages - 1));
		if (!searchActive()) {
			TerminalPages.save(type, page);
		}
	}

	/** 面板第 p 格对应的终端真实下标(搜索时只映射命中项,未映射返回 -1) */
	private static int realIndex(BackpackType type, List<Integer> matches, int p) {
		int start = page * PAGE_SLOTS + p;
		if (matches != null) {
			return start < matches.size() ? matches.get(start) : -1;
		}
		return start < type.slots ? start : -1;
	}

	private static List<Integer> matchesOf(BackpackType type, List<ItemStack> stacks) {
		if (cachedStacks == stacks && cachedSearch != null && cachedSearch.equals(search)) {
			return cachedMatches;
		}
		String query = search.trim().toLowerCase(Locale.ROOT);
		List<Integer> matches = new ArrayList<>();
		for (int i = 0; i < stacks.size() && i < type.slots; i++) {
			ItemStack stack = stacks.get(i);
			if (!stack.isEmpty() && stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
				matches.add(i);
			}
		}
		cachedStacks = stacks;
		cachedSearch = search;
		cachedMatches = matches;
		return matches;
	}

	/** 原版槽位命中(面板和原版槽位重叠时让原版优先;正常布局不会重叠) */
	private static Slot vanillaSlotAt(AbstractContainerScreen<?> screen, double mx, double my) {
		for (Slot slot : screen.getMenu().slots) {
			if (slot.isActive() && mx >= slot.x && mx < slot.x + 16 && my >= slot.y && my < slot.y + 16) {
				return slot;
			}
		}
		return null;
	}

	private static void layout(int leftPos, int topPos, int imageWidth, int screenWidth) {
		int x = leftPos + imageWidth + 8;
		if (x + PANEL_W > screenWidth) {
			int leftX = leftPos - PANEL_W - 8;
			x = leftX >= 0 ? leftX : Math.max(0, screenWidth - PANEL_W);
		}
		panelX = x;
		panelY = topPos;
		gridX = panelX + 8;
		gridY = panelY + 36;
		searchX = panelX + 8;
		searchY = panelY + 20;
		searchW = PANEL_W - 16;
		prevX = panelX + 8;
		nextX = panelX + PANEL_W - 24;
		arrowY = panelY + 76;
	}
}
