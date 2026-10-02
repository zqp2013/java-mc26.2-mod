package net.mile.backpack;

import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 单格穿戴容器:直接映射到玩家身上的某个物品附件(EQUIPPED_BACKPACK 或 EQUIPPED_CRAFTING)。
 * 服务端读写附件;客户端只是接收菜单同步的假容器。
 * 装备变化通过 changeListener 通知菜单(换包后重载内容视图)。
 */
public class BackpackSlotContainer implements Container {

	private final Player owner;
	private final AttachmentType<ItemStack> attachment;
	private final Runnable changeListener;
	/** 客户端本地缓存 */
	private ItemStack clientStack = ItemStack.EMPTY;

	public BackpackSlotContainer(Player owner, Runnable changeListener) {
		this(owner, BackpackMod.EQUIPPED_BACKPACK, changeListener);
	}

	public BackpackSlotContainer(Player owner, AttachmentType<ItemStack> attachment, Runnable changeListener) {
		this.owner = owner;
		this.attachment = attachment;
		this.changeListener = changeListener;
	}

	@Override
	public int getContainerSize() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return getItem(0).isEmpty();
	}

	@Override
	public ItemStack getItem(int index) {
		if (owner == null || owner.level().isClientSide()) {
			return clientStack;
		}
		return owner.getAttachedOrElse(this.attachment, ItemStack.EMPTY);
	}

	@Override
	public ItemStack removeItem(int index, int amount) {
		ItemStack current = getItem(0);
		if (current.isEmpty() || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack split = current.split(Math.min(amount, current.getCount()));
		setItem(0, current.isEmpty() ? ItemStack.EMPTY : current);
		return split;
	}

	@Override
	public ItemStack removeItemNoUpdate(int index) {
		ItemStack removed = getItem(0);
		setItem(0, ItemStack.EMPTY);
		return removed;
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		if (owner != null && !owner.level().isClientSide()) {
			owner.setAttached(this.attachment, stack);
		} else {
			clientStack = stack;
		}
		if (changeListener != null) {
			changeListener.run();
		}
	}

	@Override
	public void setChanged() {
		if (changeListener != null) {
			changeListener.run();
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clearContent() {
		setItem(0, ItemStack.EMPTY);
	}
}
