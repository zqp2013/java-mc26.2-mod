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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 背包物品栏菜单:复刻原版生存物品栏(合成 2x2 + 盔甲 + 玩家背包),
 * 额外加一个背包装备栏位和背包内容区(18/45/72/900 格,布局随档位)。
 * 合成终端变体 = 3x3 合成格(每格不限数量,shift 点产物一口气全合成完)。
 * 终端类内容槽没有堆叠上限;超级储存终端的带耐久物品每格上限 100,合并时耐久取平均;
 * 所有取出的路径都会被截到原版安全数量,防止 >99 的堆进入玩家背包/掉落物(磁盘编解码会炸)。
 */
public class BackpackMenu extends AbstractCraftingMenu {

	private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	/** 结果槽永远是 0,合成格从 1 开始;后面区段随合成格宽度浮动 */
	public static final int RESULT_SLOT = 0;
	public static final int CRAFT_START = 1;

	public final BackpackType type;
	/** 是否 3x3 合成格(合成终端) */
	public final boolean craftingGrid;
	/** 面板尺寸(屏幕渲染用) */
	public final int panelWidth;
	public final int panelHeight;

	public final int craftEnd;
	public final int armorStart;
	public final int armorEnd;
	public final int equipSlot;
	public final int invStart;
	public final int invEnd;
	public final int contentStart;
	public final int contentEnd;

	private final Player owner;
	private final BackpackSlotContainer equipContainer;
	private final BackpackContainer content;

	// ---- 分页/搜索视图(仅客户端屏幕调用;服务端从不激活,所有槽位恒可用) ----
	private boolean viewActive = false;
	private final int[] viewPositionOfSlot;

	public BackpackMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, BackpackType type,
			boolean serverSide, boolean craftingGrid) {
		super(menuType, containerId, craftingGrid ? 3 : 2, craftingGrid ? 3 : 2);
		this.type = type;
		this.craftingGrid = craftingGrid;
		this.owner = playerInventory.player;
		int grid = craftingGrid ? 3 : 2;
		this.craftEnd = CRAFT_START + grid * grid;
		this.armorStart = this.craftEnd;
		this.armorEnd = this.armorStart + 4;
		this.equipSlot = this.armorEnd;
		this.invStart = this.equipSlot + 1;
		this.invEnd = this.invStart + 36;
		this.contentStart = this.invEnd;
		this.contentEnd = this.contentStart + type.slots;
		this.viewPositionOfSlot = new int[type.slots];
		Arrays.fill(this.viewPositionOfSlot, -1);
		this.panelWidth = type.imageWidth();
		this.panelHeight = type.imageHeight();
		this.equipContainer = new BackpackSlotContainer(serverSide ? this.owner : null,
				craftingGrid ? BackpackMod.EQUIPPED_CRAFTING : BackpackMod.EQUIPPED_BACKPACK, this::onEquippedChanged);
		this.content = new BackpackContainer(type, serverSide ? this.owner : null);

		if (craftingGrid) {
			// 合成终端:3x3 格 + 产物在右,装备位在产物上方,没有玩家模型
			this.addResultSlot(this.owner, 98, 35);
			this.addCraftingGridSlots(30, 17);
			replaceWithUnlimitedCraftSlots(30, 17, grid);
		} else {
			// 与原版生存物品栏完全相同的布局
			this.addResultSlot(this.owner, 154, 28);
			this.addCraftingGridSlots(98, 18);
		}
		for (int i = 0; i < 4; i++) {
			this.addSlot(new ArmorSlot(playerInventory, this.owner, EQUIPMENT_SLOTS[i],
					39 - i, 8, 8 + 18 * i, Identifier.withDefaultNamespace("container/slot/" + iconPath(i))));
		}
		this.addSlot(new BackpackEquipSlot(this.equipContainer, 0, craftingGrid ? 98 : 77, craftingGrid ? 8 : 26, craftingGrid));
		this.addStandardInventorySlots(playerInventory, 8, 84);

		// 背包内容区
		for (int i = 0; i < type.slots; i++) {
			int col = i % type.columns;
			int row = i / type.columns;
			this.addSlot(new BackpackContentSlot(this, this.content, i,
					BackpackType.CONTENT_X + col * 18, type.contentY() + row * 18, type));
		}
	}

	/** 合成终端的合成格:每格数量无上限,方便一次塞几千个材料批量合成 */
	private void replaceWithUnlimitedCraftSlots(int baseX, int baseY, int grid) {
		for (int i = 0; i < grid * grid; i++) {
			int x = baseX + (i % grid) * 18;
			int y = baseY + (i / grid) * 18;
			this.slots.set(CRAFT_START + i, new UnlimitedCraftSlot(this.craftSlots, i, x, y));
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

	/** 装备位变化(换包/摘包):重载内容视图;换成非终端背包时自动摘下合成终端 */
	private void onEquippedChanged() {
		if (this.owner != null && !this.owner.level().isClientSide()) {
			BackpackMod.unequipCraftingIfOrphaned(this.owner);
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
			if (this.craftingGrid) {
				// 合成终端:shift 点产物 = 一直合成到装不下或材料用完,顺便统计数量给成就
				int totalCrafted = 0;
				while (slot.hasItem()) {
					ItemStack live = slot.getItem();
					int before = live.getCount();
					boolean moved = this.moveIntoBackpack(live) || this.moveItemStackTo(live, this.invStart, this.invEnd, true);
					int movedCount = before - live.getCount();
					if (!moved || movedCount <= 0) {
						break;
					}
					totalCrafted += movedCount;
					slot.onTake(player, copy.copyWithCount(movedCount));
					this.slotsChanged(this.craftSlots); // 消耗材料后重算产物,空了就停
				}
				if (totalCrafted > 0) {
					if (!player.level().isClientSide() && totalCrafted >= 5000) {
						BackpackAdvancements.award(player, "mass_crafting");
					}
					return copy.copyWithCount(Math.min(totalCrafted, copy.getCount()));
				}
				return ItemStack.EMPTY;
			}
			int before = original.getCount();
			if (this.moveIntoBackpack(original) || this.moveItemStackTo(original, this.invStart, this.invEnd, true)) {
				// 必须走 onTake 消耗合成格材料并重算产物,否则外层循环会把没消耗材料的产物又搬一次(复制)
				int movedCount = before - original.getCount();
				if (movedCount > 0) {
					slot.onTake(player, copy.copyWithCount(movedCount));
					this.slotsChanged(this.craftSlots);
				}
				return copy.copyWithCount(Math.max(movedCount, 1));
			}
			return ItemStack.EMPTY;
		}
		if (index >= CRAFT_START && index < this.craftEnd) {
			if (this.moveIntoBackpack(original) || this.moveItemStackTo(original, this.invStart, this.invEnd, true)) {
				return copy;
			}
			return ItemStack.EMPTY;
		}
		if ((index >= this.armorStart && index < this.armorEnd) || index == this.equipSlot) {
			if (this.moveItemStackTo(original, this.invStart, this.invEnd, true)) {
				return copy;
			}
			return ItemStack.EMPTY;
		}
		if (index >= this.invStart && index < this.invEnd) {
			// 1) 盔甲(EquipmentSlot.getIndex(): HEAD=5/CHEST=4/LEGS=3/FEET=2,armorStart 对应 HEAD)
			EquipmentSlot equipment = this.owner.getEquipmentSlotForItem(original);
			if (equipment.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
				int armorIndex = this.armorStart + (5 - equipment.getIndex());
				if (!this.slots.get(armorIndex).hasItem() && this.moveItemStackTo(original, armorIndex, armorIndex + 1, true)) {
					return copy;
				}
			} else if (BackpackType.fromItem(original.getItem()) != null
					&& BackpackType.fromItem(original.getItem()) != BackpackType.CRAFTING
					&& original.getCount() == 1
					&& !this.slots.get(this.equipSlot).hasItem()
					&& this.moveItemStackTo(original, this.equipSlot, this.equipSlot + 1, true)) {
				// 2) 背包本身 → 装备位
				return copy;
			}
			// 3) 背包格
			if (this.moveIntoBackpack(original)) {
				return copy;
			}
			// 4) 主背包 ↔ 快捷栏
			if (index < this.invStart + 27) {
				if (this.moveItemStackTo(original, this.invStart + 27, this.invEnd, true)) {
					return copy;
				}
			} else if (this.moveItemStackTo(original, this.invStart, this.invStart + 27, false)) {
				return copy;
			}
			return ItemStack.EMPTY;
		}
		// 背包格 → 玩家背包(大堆按原版安全数量拆着搬)
		int cap = takeCap(original);
		if (original.getCount() > cap) {
			ItemStack chunk = original.copyWithCount(cap);
			if (this.moveItemStackTo(chunk, this.invStart, this.invEnd, true)) {
				int moved = cap - chunk.getCount();
				if (moved > 0) {
					original.shrink(moved);
					slot.set(original);
					return copy.copyWithCount(moved);
				}
			}
			return ItemStack.EMPTY;
		}
		if (this.moveItemStackTo(original, this.invStart, this.invEnd, true)) {
			return copy;
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 数字键交换会把整堆直接塞进快捷栏,对 >99 的大堆必须在菜单层拦下:
	 * 快捷栏为空时一次只交换出原版安全数量;快捷栏有东西则不合并不交换。
	 * 超级终端的带耐久物品在放进已有同类堆时,这里做"耐久取平均"合并。
	 */
	@Override
	public void clicked(int index, int button, ContainerInput input, Player player) {
		// 翻页/搜索瞬间的竞态防护:客户端显示的页和服务端视图可能短暂不一致,服务端忽略不可见内容槽的点击
		if (this.type.paged() && this.isValidSlotIndex(index)
				&& index >= this.contentStart && index < this.contentEnd
				&& !this.isContentVisible(index - this.contentStart)) {
			return;
		}
		if (this.type == BackpackType.SUPER
				&& input == ContainerInput.PICKUP
				&& this.isValidSlotIndex(index) && index >= this.contentStart && index < this.contentEnd) {
			ItemStack carried = this.getCarried();
			Slot slot = this.slots.get(index);
			ItemStack current = slot.getItem();
			if (!carried.isEmpty() && carried.isDamageableItem() && !current.isEmpty()
					&& slot.mayPlace(carried) && sameItemAndModifiers(current, carried)) {
				int room = superContentCap(current) - current.getCount();
				if (room > 0) {
					int add = Math.min(room, carried.getCount());
					int total = current.getCount() + add;
					int avg = Math.round(((float) (current.getCount() * current.getDamageValue()
							+ add * carried.getDamageValue())) / total);
					current.grow(add);
					current.setDamageValue(Math.min(avg, current.getMaxDamage()));
					slot.set(current);
					carried.shrink(add);
					this.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
					this.broadcastChanges();
				}
				return;
			}
		}
		if (input == ContainerInput.SWAP && this.isValidSlotIndex(index) && index >= this.contentStart && index < this.contentEnd) {
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

	/** 装进背包内容区(合并 → 空格),终端遵循各自的合并规则 */
	private boolean moveIntoBackpack(ItemStack stack) {
		if (BackpackType.fromItem(stack.getItem()) != null) {
			return false; // 背包不能装背包
		}
		int cap = contentCap(stack);
		int before = stack.getCount();
		for (int i = this.contentStart; i < this.contentEnd; i++) {
			Slot slot = this.slots.get(i);
			ItemStack current = slot.getItem();
			if (!current.isEmpty() && slot.mayPlace(stack) && canBackpackMerge(current, stack)) {
				int room = cap - current.getCount();
				if (room > 0) {
					int add = Math.min(room, stack.getCount());
					if (stack.isDamageableItem() && current.getDamageValue() != stack.getDamageValue()) {
						// 超级终端:耐久取加权平均
						int total = current.getCount() + add;
						int avg = Math.round(((float) (current.getCount() * current.getDamageValue()
								+ add * stack.getDamageValue())) / total);
						current.setDamageValue(Math.min(avg, current.getMaxDamage()));
					}
					current.grow(add);
					stack.shrink(add);
					slot.set(current);
					if (stack.isEmpty()) {
						return true;
					}
				}
			}
		}
		for (int i = this.contentStart; i < this.contentEnd; i++) {
			Slot slot = this.slots.get(i);
			if (slot.getItem().isEmpty() && slot.mayPlace(stack)) {
				int amount = this.type.unlimited() ? Math.min(contentCap(stack), stack.getCount())
						: Math.min(stack.getCount(), stack.getMaxStackSize());
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
		if (this.type == BackpackType.SUPER) {
			if (incoming.isDamageableItem()) {
				// 超级终端:同物品且除耐久外组件一致(附魔等)即可,耐久合并时取平均
				return sameItemAndModifiers(inSlot, incoming);
			}
			return ItemStack.isSameItemSameComponents(inSlot, incoming);
		}
		if (!ItemStack.isSameItemSameComponents(inSlot, incoming)) {
			return false;
		}
		if (this.type.unlimited()) {
			// 终端:可堆叠物品随意合;不可堆叠物品耐久相同(即组件相同)可合,非损耗型不合
			return incoming.getMaxStackSize() > 1 || incoming.isDamageableItem();
		}
		return incoming.getMaxStackSize() > 1;
	}

	/** 同物品且除耐久外的组件完全一致(附魔不同就过不了这关) */
	private static boolean sameItemAndModifiers(ItemStack a, ItemStack b) {
		if (a.getItem() != b.getItem()) {
			return false;
		}
		ItemStack ca = a.copy();
		ca.setDamageValue(0);
		ItemStack cb = b.copy();
		cb.setDamageValue(0);
		return ItemStack.isSameItemSameComponents(ca, cb);
	}

	/** 内容格里这类物品的堆叠上限 */
	private int contentCap(ItemStack stack) {
		if (!this.type.unlimited()) {
			return stack.getMaxStackSize();
		}
		return this.type == BackpackType.SUPER && stack.isDamageableItem() ? 100 : Integer.MAX_VALUE;
	}

	private static int superContentCap(ItemStack stack) {
		return stack.isDamageableItem() ? 100 : Integer.MAX_VALUE;
	}

	/** 从背包格取出的原版安全数量(不可堆叠的合并堆一次只能拿 1 个) */
	public static int takeCap(ItemStack stack) {
		return stack.getMaxStackSize() > 1 ? stack.getMaxStackSize() : 1;
	}

	// ---- 分页/搜索视图(客户端专用) ----

	/** 搜索模式下最多展示多少匹配项(500 页足够) */
	public int pageCount(String search) {
		String q = normalize(search);
		if (q.isEmpty()) {
			return (this.type.slots + BackpackType.PAGE_SLOTS - 1) / BackpackType.PAGE_SLOTS;
		}
		return (matchIndices(q).size() + BackpackType.PAGE_SLOTS - 1) / BackpackType.PAGE_SLOTS;
	}

	/** 应用当前视图:标记本页/搜索命中的内容槽为可见(其余槽 isActive=false,不渲染不可点)。坐标不动——渲染和命中都会跳过非激活槽。 */
	public void applyView(int page, String search) {
		if (!this.type.paged()) {
			return;
		}
		this.viewActive = true;
		Arrays.fill(this.viewPositionOfSlot, -1);
		String q = normalize(search);
		List<Integer> shown = new ArrayList<>(BackpackType.PAGE_SLOTS);
		if (q.isEmpty()) {
			int start = Math.max(0, page) * BackpackType.PAGE_SLOTS;
			for (int i = 0; i < BackpackType.PAGE_SLOTS && start + i < this.type.slots; i++) {
				shown.add(start + i);
			}
		} else {
			List<Integer> matches = matchIndices(q);
			int start = Math.max(0, page) * BackpackType.PAGE_SLOTS;
			for (int i = 0; i < BackpackType.PAGE_SLOTS && start + i < matches.size(); i++) {
				shown.add(matches.get(start + i));
			}
		}
		for (int real : shown) {
			this.viewPositionOfSlot[real] = 1;
		}
	}

	private static String normalize(String s) {
		return s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private List<Integer> matchIndices(String query) {
		List<Integer> matches = new ArrayList<>();
		for (int i = 0; i < this.type.slots; i++) {
			ItemStack stack = this.content.getItem(i);
			if (!stack.isEmpty()) {
				String name = stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
				if (name.contains(query)) {
					matches.add(i);
				}
			}
		}
		return matches;
	}

	/** 内容槽是否在当前视图里(服务端未激活视图 → 恒可见) */
	public boolean isContentVisible(int contentIndex) {
		if (!this.viewActive) {
			return true;
		}
		return contentIndex >= 0 && contentIndex < this.viewPositionOfSlot.length
				&& this.viewPositionOfSlot[contentIndex] >= 0;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.resultSlots.clearContent();
		if (!player.level().isClientSide()) {
			this.clearContainer(player, this.craftSlots);
			if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
				BackpackAdvancements.checkTerminal(serverPlayer);
			}
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
		return this.slots.subList(CRAFT_START, this.craftEnd);
	}

	@Override
	protected Player owner() {
		return this.owner;
	}

	@Override
	public RecipeBookType getRecipeBookType() {
		return RecipeBookType.CRAFTING;
	}

	/** 合成终端的合成格:不限数量 */
	public static class UnlimitedCraftSlot extends Slot {
		public UnlimitedCraftSlot(net.minecraft.world.Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public int getMaxStackSize() {
			return Integer.MAX_VALUE;
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			return Integer.MAX_VALUE;
		}
	}

	/** 背包内容槽:终端无堆叠上限(超级终端带耐久物品 100),取出数量截到原版安全值 */
	public static class BackpackContentSlot extends Slot {
		private final BackpackMenu menu;
		private final BackpackType type;

		public BackpackContentSlot(BackpackMenu menu, BackpackContainer container, int index, int x, int y,
				BackpackType type) {
			super(container, index, x, y);
			this.menu = menu;
			this.type = type;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return BackpackType.fromItem(stack.getItem()) == null;
		}

		@Override
		public boolean isActive() {
			return this.menu.isContentVisible(this.getContainerSlot());
		}

		@Override
		public int getMaxStackSize() {
			return this.type.unlimited() ? Integer.MAX_VALUE : 64;
		}

		@Override
		public int getMaxStackSize(ItemStack stack) {
			if (!this.type.unlimited()) {
				return stack.getMaxStackSize();
			}
			return this.type == BackpackType.SUPER && stack.isDamageableItem() ? 100 : Integer.MAX_VALUE;
		}

		@Override
		public ItemStack remove(int amount) {
			if (this.type.unlimited()) {
				ItemStack current = this.getItem();
				return super.remove(Math.min(amount, takeCap(current)));
			}
			return super.remove(amount);
		}
	}

	/** 穿戴槽:普通界面收背包,合成终端界面收合成终端,一格一个 */
	public static class BackpackEquipSlot extends Slot {
		private final boolean crafting;

		public BackpackEquipSlot(BackpackSlotContainer container, int index, int x, int y, boolean crafting) {
			super(container, index, x, y);
			this.crafting = crafting;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			BackpackType type = BackpackType.fromItem(stack.getItem());
			return type != null && (this.crafting ? type == BackpackType.CRAFTING : type != BackpackType.CRAFTING);
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
