package net.mile.superfurnace;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 超级熔炉菜单:左侧燃料槽竖列,中间输入槽网格,右侧成品槽网格,下方玩家背包。
 * 槽位坐标算好存在菜单里(客户端/服务端各自按同一套等级参数构造,布局一致)。
 */
public class SuperFurnaceMenu extends AbstractContainerMenu {
	public final FurnaceTier tier;
	private final ContainerLevelAccess access;
	private final Container container;
	private final ContainerData data;
	private final Level level;

	// ---- 布局(供 Screen 画背景和进度条用;gen_gui.py 按同一套公式烘贴图) ----
	public final int fuelX;
	public final int inputX;
	public final int outputX;
	public final int gridY;
	public final int flameX;
	public final int flameY;
	public final int arrowX;
	public final int arrowY;
	public final int inputCols;
	public final int inputRows;
	public final int outputCols;
	public final int gridRows;
	/** 网格下方一行(×N 显示)的起始 y */
	public final int barsY;
	/** 玩家背包槽起始 y */
	public final int invY;
	/** "物品栏"标签的 y */
	public final int invLabelY;
	public final int imageWidth;
	public final int imageHeight;
	/** GUI 贴图实际宽度(宽面板 512,其余 256;高度一律 256) */
	public final int texWidth;

	public SuperFurnaceMenu(MenuType<?> type, int id, Inventory inventory, Container container,
			ContainerData data, FurnaceTier tier, ContainerLevelAccess access) {
		super(type, id);
		this.tier = tier;
		this.access = access;
		this.container = container;
		this.data = data;
		this.level = inventory.player.level();

		checkContainerSize(container, tier.totalSlots);
		checkContainerDataCount(data, 5);

		// 布局:火焰 | 燃料列 | 输入网格 | 箭头 | 成品网格
		inputCols = Math.min(tier.inputSlots, 5);
		outputCols = Math.min(tier.outputSlots, 5);
		inputRows = (tier.inputSlots + inputCols - 1) / inputCols;
		int outputRows = (tier.outputSlots + outputCols - 1) / outputCols;
		gridY = 17;
		gridRows = Math.max(tier.fuelSlots, Math.max(inputRows, outputRows));
		int flame0 = 7;
		int fuel0 = 25;
		int input0 = 51;
		int output0 = input0 + inputCols * 18 + 28;
		int contentRight0 = output0 + outputCols * 18;
		imageWidth = Math.max(contentRight0 + 5, 176);
		int shift = Math.max(0, (imageWidth - contentRight0 - 4) / 2);
		flameX = flame0 + shift;
		fuelX = fuel0 + shift;
		inputX = input0 + shift;
		outputX = output0 + shift;
		arrowX = inputX + inputCols * 18 + 2;
		arrowY = gridY + (gridRows * 18 - 16) / 2;
		flameY = gridY + (gridRows * 18 - 14) / 2;
		barsY = gridY + gridRows * 18 + 4;
		invY = barsY + 15;
		invLabelY = invY - 10;
		imageHeight = invY + 76;
		texWidth = imageWidth > 256 ? 512 : 256;

		// 熔炉槽:燃料 → 输入 → 成品
		for (int f = 0; f < tier.fuelSlots; f++) {
			addSlot(new FuelSlot(container, tier.fuelBase() + f, fuelX, gridY + f * 18));
		}
		for (int i = 0; i < tier.inputSlots; i++) {
			addSlot(new Slot(container, tier.inputBase() + i, inputX + (i % inputCols) * 18,
					gridY + (i / inputCols) * 18));
		}
		for (int o = 0; o < tier.outputSlots; o++) {
			addSlot(new ResultSlot(container, tier.outputBase() + o, outputX + (o % outputCols) * 18,
					gridY + (o / outputCols) * 18));
		}

		// 玩家背包 + 快捷栏
		addStandardInventorySlots(inventory, 8, invY);

		addDataSlots(data);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		int total = tier.totalSlots;
		if (index < total) {
			// 从熔炉 → 玩家背包
			if (!moveItemStackTo(stack, total, total + 36, true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// 玩家背包 → 熔炉:能烧的进输入槽,燃料进燃料槽,否则背包↔快捷栏
			boolean moved = false;
			if (isSmeltable(player.level(), stack)) {
				moved = moveItemStackTo(stack, tier.fuelSlots, tier.fuelSlots + tier.inputSlots, false);
			} else if (isFuel(player.level(), stack)) {
				moved = moveItemStackTo(stack, 0, tier.fuelSlots, false);
			}
			if (!moved) {
				if (index < total + 27) {
					moved = moveItemStackTo(stack, total + 27, total + 36, false);
				} else {
					moved = moveItemStackTo(stack, total, total + 27, false);
				}
				if (!moved) {
					return ItemStack.EMPTY;
				}
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, tier.block);
	}

	// ---- 界面读取的同步数据 ----

	public int getBurnTime() {
		return data.get(0);
	}

	public int getBurnDuration() {
		return data.get(1);
	}

	/** 熔炉当前等级(同步数据,客户端可读) */
	public int getFurnaceLevel() {
		return data.get(2);
	}

	/** 本批(同时烧的几个物品)的共用进度 */
	public int getProgress() {
		return data.get(3);
	}

	/** 当前实际同时烧的个数(料不够等级时小于等级) */
	public int getBurningCount() {
		return data.get(4);
	}

	private static boolean isFuel(Level level, ItemStack stack) {
		return level.fuelValues().isFuel(stack);
	}

	private static boolean isSmeltable(Level level, ItemStack stack) {
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			// 服务端:直接查配方(成品是否匹配也顺带覆盖)
			return serverLevel.recipeAccess()
					.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), serverLevel).isPresent();
		}
		// 客户端:配方管理器不在,用同步来的"熔炉可烧物品集合"判断
		RecipePropertySet set = level.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT);
		return set.test(stack);
	}

	/** 燃料槽:只收燃料 */
	private class FuelSlot extends Slot {
		FuelSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return isFuel(level, stack);
		}
	}

	/** 成品槽:只出不进 */
	private static class ResultSlot extends Slot {
		ResultSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}
}
