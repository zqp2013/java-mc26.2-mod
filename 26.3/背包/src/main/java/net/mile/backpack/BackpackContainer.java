package net.mile.backpack;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 背包内容容器:一个随时可变的槽位列表。
 * 服务端(owner != null)所有写操作都即时回写到已装备背包物品的 contents 组件里
 * (内容随物品走,像潜影盒一样,摘下背包不会丢东西);客户端(owner == null)只是
 * 接收菜单同步的假容器,不回写。
 */
public class BackpackContainer implements Container {

	private final BackpackType type;
	private final Player owner;
	private NonNullList<ItemStack> stacks;

	public BackpackContainer(BackpackType type, Player owner) {
		this.type = type;
		this.owner = owner;
		this.stacks = NonNullList.withSize(type.slots, ItemStack.EMPTY);
		if (owner != null) {
			reloadFromEquipped();
		}
	}

	/** 从已装备的背包物品重新载入(换包/摘包后由菜单回调) */
	public void reloadFromEquipped() {
		if (owner == null) {
			return;
		}
		NonNullList<ItemStack> fresh = NonNullList.withSize(type.slots, ItemStack.EMPTY);
		List<ItemStack> saved = BackpackContents.read(equipped());
		for (int i = 0; i < saved.size() && i < fresh.size(); i++) {
			fresh.set(i, saved.get(i));
		}
		this.stacks = fresh;
	}

	private ItemStack equipped() {
		if (owner == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = owner.getAttachedOrElse(BackpackMod.EQUIPPED_BACKPACK, ItemStack.EMPTY);
		// 只在装备位上还是本类型背包时才认
		return BackpackType.fromItem(stack.getItem()) == type ? stack : ItemStack.EMPTY;
	}

	/** 把当前槽位列表写回装备位的背包物品(换副本写,确保附件同步能感知变化) */
	private void persist() {
		ItemStack equipped = equipped();
		if (equipped.isEmpty()) {
			return; // 背包已被摘下:内容都在物品自己的组件里,无需回写
		}
		ItemStack copy = equipped.copy();
		copy.set(BackpackMod.BACKPACK_CONTENTS, new BackpackContents(List.of(this.stacks.toArray(new ItemStack[0]))));
		owner.setAttached(BackpackMod.EQUIPPED_BACKPACK, copy);
	}

	@Override
	public int getContainerSize() {
		return stacks.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int index) {
		return stacks.get(index);
	}

	@Override
	public ItemStack removeItem(int index, int amount) {
		ItemStack split = stacks.get(index).split(amount);
		if (!split.isEmpty()) {
			setChanged();
		}
		return split;
	}

	@Override
	public ItemStack removeItemNoUpdate(int index) {
		ItemStack removed = stacks.get(index);
		stacks.set(index, ItemStack.EMPTY);
		if (!removed.isEmpty()) {
			setChanged();
		}
		return removed;
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		stacks.set(index, stack);
		setChanged();
	}

	@Override
	public void setChanged() {
		if (owner != null && !owner.level().isClientSide()) {
			persist();
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		stacks.clear();
		setChanged();
	}

	@Override
	public int getMaxStackSize() {
		return type.unlimited() ? Integer.MAX_VALUE : 64;
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return type.unlimited() ? Integer.MAX_VALUE : stack.getMaxStackSize();
	}
}
