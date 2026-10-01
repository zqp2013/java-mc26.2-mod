package net.mile.backpack;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 背包物品栏菜单:复刻原版生存物品栏(合成 2x2 + 盔甲 + 玩家背包),
 * 额外加一个背包装备栏位和背包内容区(18/45/72 格,布局随档位)。
 * 储存终端的内容槽没有堆叠上限,所有取出的路径都会被截到原版安全数量,
 * 防止 >99 的堆进入玩家背包/掉落物(磁盘编解码会炸)。
 */
public class BackpackMenu extends AbstractCraftingMenu {

	public static final int RESULT_SLOT = 0;
	public static final int CRAFT_START = 1;
	public static final int CRAFT_END = 5;
	public static final int ARMOR_START = 5;
	public static final int ARMOR_END = 9;
	public static final int EQUIP_SLOT = 9;
	public static final int INV_START = 10;
	public static final int INV_END = 46;
	public static final int CONTENT_START = 46;

	private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	public final BackpackType type;
	public final int contentEnd;
	private final Player owner;
	private final BackpackSlotContainer equipContainer;
	private final BackpackContainer content;

	public BackpackMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, BackpackType type, boolean serverSide) {
		super(menuType, containerId, 2, 2);
		this.type = type;
		this.owner = playerInventory.player;
		this.contentEnd = CONTENT_START + type.slots;
		this.equipContainer = new BackpackSlotContainer(serverSide ? this.owner : null, this::onEquippedChanged);
		this.content = new BackpackContainer(type, serverSide ? this.owner : null);

		// 与原版生存物品栏完全相同的布局
		this.addResultSlot(this.owner, 154, 28);
		this.addCraftingGridSlots(98, 18);
		for (int i = 0; i < 4; i++) {
			this.addSlot(new ArmorSlot(playerInventory, this.owner, EQUIPMENT_SLOTS[i],
					39 - i, 8, 8 + 18 * i, Identifier.withDefaultNamespace("container/slot/" + iconPath(i))));
		}
		this.addSlot(new BackpackEquipSlot(this.equipContainer, 0, 77, 26));
		this.addStandardInventorySlots(playerInventory, 8, 84);

		// 背包内容区
		for (int i = 0; i < type.slots; i++) {
			int col = i % type.columns;
			int row = i / type.columns;
			this.addSlot(new BackpackContentSlot(this.content, i,
					BackpackType.CONTENT_X + col * 18, BackpackType.CONTENT_Y + row * 18, type));
		}
	}

	private static String iconPath(int i) {
		return switch (i) {
			case 0 -> "helmet";
			case 1 -> "chestplate";
			case 2 -> "leggings";
			default -> "boots";
		};
	}

	/** 装备位变化(换包/摘包):重载内容视图 */
	private void onEquippedChanged() {
		if (this.owner != null && !this.owner.level().isClientSide()) {
			this.content.reloadFromEquipped();
			this.broadcastChanges();
		}
	}

	@Override
	public void slotsChanged(net.minecraft.world.Container container) {
		if (container == this.craftSlots) {
			// 抄原版 InventoryMenu:服务端重算合成产物
			if (this.owner.level() instanceof ServerLevel serverLevel) {
				CraftingMenu.slotChangedCraftingGrid(this, serverLevel, this.owner, this.craftSlots, this.resultSlots, null);
			}
		} else if (container == this.equipContainer) {
			this.onEquippedChanged();
		} else {
			super.slotsChanged(container);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getItem();
		ItemStack copy = original.copy();

		if (index == RESULT_SLOT) {
			if (this.moveIntoBackpack(original) || this.moveItemStackTo(original, INV_START, INV_END, true)) {
				slot.onQuickCraft(original, copy);
				return copy;
			}
			return ItemStack.EMPTY;
		}
		if (index >= CRAFT_START && index < CRAFT_END) {
			if (this.moveIntoBackpack(original) || this.moveItemStackTo(original, INV_START, INV_END, true)) {
				return copy;
			}
			return ItemStack.EMPTY;
		}
		if ((index >= ARMOR_START && index < ARMOR_END) || index == EQUIP_SLOT) {
			if (this.moveItemStackTo(original, INV_START, INV_END, true)) {
				return copy;
			}
			return ItemStack.EMPTY;
		}
		if (index >= INV_START && index < INV_END) {
			// 1) 盔甲
			EquipmentSlot equipment = this.owner.getEquipmentSlotForItem(original);
			if (equipment.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				int armorIndex = 8 - equipment.getIndex();
				if (!this.slots.get(armorIndex).hasItem() && this.moveItemStackTo(original, armorIndex, armorIndex + 1, true)) {
					return copy;
				}
			} else if (BackpackType.fromItem(original.getItem()) != null
					&& original.getCount() == 1
					&& !this.slots.get(EQUIP_SLOT).hasItem()
					&& this.moveItemStackTo(original, EQUIP_SLOT, EQUIP_SLOT + 1, true)) {
				// 2) 背包本身 → 装备位
				return copy;
			}
			// 3) 背包格
			if (this.moveIntoBackpack(original)) {
				return copy;
			}
			// 4) 主背包 ↔ 快捷栏
			if (index < INV_START + 27) {
				if (this.moveItemStackTo(original, INV_START + 27, INV_END, true)) {
					return copy;
				}
			} else if (this.moveItemStackTo(original, INV_START, INV_START + 27, false)) {
				return copy;
			}
			return ItemStack.EMPTY;
		}
		// 背包格 → 玩家背包(大堆按原版安全数量拆着搬)
		int cap = takeCap(original);
		if (original.getCount() > cap) {
			ItemStack chunk = original.copyWithCount(cap);
			if (this.moveItemStackTo(chunk, INV_START, INV_END, true)) {
				int moved = cap - chunk.getCount();
				if (moved > 0) {
					original.shrink(moved);
					slot.set(original);
					return copy.copyWithCount(moved);
				}
			}
			return ItemStack.EMPTY;
		}
		if (this.moveItemStackTo(original, INV_START, INV_END, true)) {
			return copy;
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 数字键交换会把整堆直接塞进快捷栏,对 >99 的大堆必须在菜单层拦下:
	 * 快捷栏为空时一次只交换出原版安全数量;快捷栏有东西则不合并不交换。
	 */
	@Override
	public void clicked(int index, int button, ContainerInput input, Player player) {
		if (input == ContainerInput.SWAP && this.isValidSlotIndex(index) && index >= CONTENT_START && index < this.contentEnd) {
			Slot slot = this.slots.get(index);
			ItemStack stack = slot.getItem();
			if (!stack.isEmpty() && stack.getCount() > takeCap(stack)) {
				if (button >= 0 && button < 9 && player.getInventory().getItem(button).isEmpty()) {
					ItemStack taken = slot.remove(takeCap(stack));
					player.getInventory().setItem(button, taken);
					this.broadcastChanges();
				}
				return; // 其他交换情形(副手/非空快捷栏)对大堆一律拒绝
			}
		}
		super.clicked(index, button, input, player);
	}

	/** 装进背包内容区(合并 → 空格),终端遵循"不可堆叠但同耐久可堆叠"规则 */
	private boolean moveIntoBackpack(ItemStack stack) {
		if (BackpackType.fromItem(stack.getItem()) != null) {
			return false; // 背包不能装背包
		}
		int before = stack.getCount();
		for (int i = CONTENT_START; i < this.contentEnd; i++) {
			Slot slot = this.slots.get(i);
			ItemStack current = slot.getItem();
			if (!current.isEmpty() && slot.mayPlace(stack) && canBackpackMerge(current, stack)) {
				int room = this.type.unlimited()
						? Integer.MAX_VALUE - current.getCount()
						: current.getMaxStackSize() - current.getCount();
				if (room > 0) {
					int add = Math.min(room, stack.getCount());
					current.grow(add);
					stack.shrink(add);
					slot.set(current);
					if (stack.isEmpty()) {
						return true;
					}
				}
			}
		}
		for (int i = CONTENT_START; i < this.contentEnd; i++) {
			Slot slot = this.slots.get(i);
			if (slot.getItem().isEmpty() && slot.mayPlace(stack)) {
				int amount = this.type.unlimited() ? stack.getCount() : Math.min(stack.getCount(), stack.getMaxStackSize());
				slot.set(stack.split(amount));
				if (stack.isEmpty()) {
					return true;
				}
			}
		}
		return stack.getCount() < before;
	}

	/** 背包格内的两堆能否合并 */
	public boolean canBackpackMerge(ItemStack inSlot, ItemStack incoming) {
		if (!ItemStack.isSameItemSameComponents(inSlot, incoming)) {
			return false;
		}
		if (this.type.unlimited()) {
			// 终端:可堆叠物品随意合;不可堆叠物品耐久相同(即组件相同)可合,非损耗型不合
			return incoming.getMaxStackSize() > 1 || incoming.isDamageableItem();
		}
		return incoming.getMaxStackSize() > 1;
	}

	/** 从背包格取出的原版安全数量(不可堆叠的合并堆一次只能拿 1 个) */
	public static int takeCap(ItemStack stack) {
		return stack.getMaxStackSize() > 1 ? stack.getMaxStackSize() : 1;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.resultSlots.clearContent();
		if (!player.level().isClientSide()) {
			this.clearContainer(player, this.craftSlots);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public Slot getResultSlot() {
		return this.slots.get(RESULT_SLOT);
	}

	@Override
	public List<Slot> getInputGridSlots() {
		return this.slots.subList(CRAFT_START, CRAFT_END);
	}

	@Override
	protected Player owner() {
		return this.owner;
	}

	@Override
	public RecipeBookType getRecipeBookType() {
		return RecipeBookType.CRAFTING;
	}

	/** 背包内容槽:终端无堆叠上限,取出数量截到原版安全值 */
	public static class BackpackContentSlot extends Slot {
		private final boolean unlimited;

		public BackpackContentSlot(BackpackContainer container, int index, int x, int y, BackpackType type) {
			super(container, index, x, y);
			this.unlimited = type.unlimited();
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return BackpackType.fromItem(stack.getItem()) == null;
		}

		@Override
		public int getMaxStackSize() {
			return this.unlimited ? Integer.MAX_VALUE : 64;
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			return this.unlimited ? Integer.MAX_VALUE : stack.getMaxStackSize();
		}

		@Override
		public ItemStack remove(int amount) {
			if (this.unlimited) {
				ItemStack current = this.getItem();
				return super.remove(Math.min(amount, takeCap(current)));
			}
			return super.remove(amount);
		}
	}

	/** 背包装备槽:只收背包物品,一格一个 */
	public static class BackpackEquipSlot extends Slot {
		public BackpackEquipSlot(BackpackSlotContainer container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return BackpackType.fromItem(stack.getItem()) != null;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			return 1;
		}

		@Override
		public Identifier getNoItemIcon() {
			return Identifier.fromNamespaceAndPath("backpack", "container/slot/backpack");
		}
	}
}
