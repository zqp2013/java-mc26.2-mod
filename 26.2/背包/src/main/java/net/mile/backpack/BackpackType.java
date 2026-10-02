package net.mile.backpack;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;

/**
 * 背包档位:普通 18 格 / 高级 45 格 / 储存终端 72 格(无堆叠上限) /
 * 超级储存终端 900 格(每页 18 格,可搜索,带耐久物品上限 100 且耐久取平均)。
 * CRAFTING = 合成终端(不占背包档位,穿戴在第二个附件栏,要求身上有终端)。
 */
public enum BackpackType {
	/** 普通背包:18 格 */
	NORMAL(18, 9, Component.translatable("item.backpack.normal_backpack")),
	/** 高级背包:45 格 */
	ADVANCED(45, 15, Component.translatable("item.backpack.advanced_backpack")),
	/** 储存终端:72 格,每格无堆叠上限 */
	TERMINAL(72, 18, Component.translatable("item.backpack.storage_terminal")),
	/** 超级储存终端:900 格,分页显示(每页 18 格)+ 搜索;带耐久物品每格上限 100,耐久取平均 */
	SUPER(900, 9, Component.translatable("item.backpack.super_storage_terminal")),
	/** 合成终端:穿戴件(储存终端类装备时可穿),提供 3x3 无限材料的合成界面 */
	CRAFTING(0, 9, Component.translatable("item.backpack.crafting_terminal"));

	/** 每页可见格数(只有 SUPER 分页) */
	public static final int PAGE_SLOTS = 18;

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

	/** 面板行数(分页档位只算一页的行数) */
	public int rows() {
		return paged() ? PAGE_SLOTS / columns : slots / columns;
	}

	/** 终端的每格没有堆叠上限 */
	public boolean unlimited() {
		return this == TERMINAL || this == SUPER;
	}

	/** 超级储存终端:分页 + 搜索 */
	public boolean paged() {
		return this == SUPER;
	}

	/** 是"终端"类背包(合成终端的穿戴条件) */
	public boolean isTerminal() {
		return this == TERMINAL || this == SUPER;
	}

	/** 面板总宽(至少与原版物品栏同宽) */
	public int imageWidth() {
		return Math.max(176, 16 + columns * 18);
	}

	/** 背包内容区第一格的 y 坐标(相对);分页档位上面要多一行搜索框 */
	public int contentY() {
		return paged() ? 190 : 176;
	}

	/** 面板总高 = 原版物品栏 166 + 背包区标签/槽位 */
	public int imageHeight() {
		return contentY() + rows() * 18 + 4;
	}

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
