package net.mile.backpack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;

/** 三档背包:普通 18 格 / 高级 45 格 / 储存终端 72 格(无堆叠上限) */
public enum BackpackType {
	/** 普通背包:18 格 */
	NORMAL(18, 9, Component.translatable("item.backpack.normal_backpack")),
	/** 高级背包:45 格 */
	ADVANCED(45, 15, Component.translatable("item.backpack.advanced_backpack")),
	/** 储存终端:72 格,每格无堆叠上限 */
	TERMINAL(72, 18, Component.translatable("item.backpack.storage_terminal"));

	public final int slots;
	public final int columns;
	public final Component title;

	private BackpackItem item;
	private MenuType<BackpackMenu> menuType;

	BackpackType(int slots, int columns, Component title) {
		this.slots = slots;
		this.columns = columns;
		this.title = title;
	}

	/** 面板行数 */
	public int rows() {
		return slots / columns;
	}

	/** 储存终端的每格没有堆叠上限 */
	public boolean unlimited() {
		return this == TERMINAL;
	}

	/** 面板总宽(至少与原版物品栏同宽) */
	public int imageWidth() {
		return Math.max(176, 16 + columns * 18);
	}

	/** 面板总高 = 原版物品栏 166 + 背包区标签/槽位 */
	public int imageHeight() {
		return 176 + rows() * 18 + 4;
	}

	/** 背包内容区第一格的 y 坐标(相对) */
	public static final int CONTENT_Y = 176;
	/** 背包内容区 x 起点(相对) */
	public static final int CONTENT_X = 8;

	void item(BackpackItem item) {
		this.item = item;
	}

	public BackpackItem item() {
		return item;
	}

	void menuType(MenuType<BackpackMenu> type) {
		this.menuType = type;
	}

	public MenuType<BackpackMenu> menuType() {
		return menuType;
	}

	/** 从物品反查类型;不是背包物品返回 null */
	public static BackpackType fromItem(Item item) {
		for (BackpackType type : values()) {
			if (type.item == item) {
				return type;
			}
		}
		return null;
	}
}
