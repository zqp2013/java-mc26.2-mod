package net.mile.backpack;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 其他容器界面(箱子/熔炉等)侧边终端面板的服务端逻辑:面板点击走
 * TerminalClickPayload 过来,这里对着已装备终端的 contents 组件操作。
 * 一次最多把原版安全数量拿到手上(可堆叠 64/不可堆叠 1)——这些界面的关闭
 * 清理是原版的,丢不出超 99 的掉落物;要整堆超 64 拿请开终端自己的界面。
 * 合并规则与 BackpackMenu.canBackpackMerge / contentCap 一致(复用 TerminalPickup 的静态方法)。
 */
public final class TerminalTransfer {

	private TerminalTransfer() {
	}

	public static void handle(ServerPlayer player, int slot, boolean right, boolean shift) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu == null || menu instanceof BackpackMenu) {
			return; // 终端自己的界面有自己的交互
		}
		ItemStack equipped = player.getAttachedOrElse(BackpackMod.EQUIPPED_BACKPACK, ItemStack.EMPTY);
		BackpackType type = BackpackType.fromItem(equipped.getItem());
		if (type == null || !type.unlimited() || slot < 0 || slot >= type.slots) {
			return;
		}

		List<ItemStack> saved = BackpackContents.read(equipped);
		List<ItemStack> stacks = new ArrayList<>(saved);
		while (stacks.size() < type.slots) {
			stacks.add(ItemStack.EMPTY);
		}
		ItemStack target = stacks.get(slot);
		ItemStack carried = menu.getCarried();
		boolean changed = false;

		if (shift && !right && carried.isEmpty() && !target.isEmpty()) {
			// shift+左键:整堆分块塞进玩家背包(每格原版上限),装不下的留在终端
			changed = moveIntoPlayerInventory(player, target);
		} else if (carried.isEmpty()) {
			if (!target.isEmpty()) {
				int take = Math.min(target.getCount(), BackpackMenu.takeCap(target));
				if (right) {
					take = Math.min(take, (target.getCount() + 1) / 2);
				}
				menu.setCarried(target.copyWithCount(take));
				target.shrink(take);
				changed = true;
			}
		} else if (BackpackType.fromItem(carried.getItem()) == null) {
			// 手上有东西:放进终端(右键只放 1 个)
			int amount = right ? 1 : carried.getCount();
			if (target.isEmpty()) {
				int add = Math.min(TerminalPickup.contentCap(type, carried), amount);
				if (add > 0) {
					stacks.set(slot, carried.split(add));
					changed = true;
				}
			} else if (TerminalPickup.canMerge(type, target, carried)) {
				int room = TerminalPickup.contentCap(type, target) - target.getCount();
				int add = Math.min(room, amount);
				if (add > 0) {
					if (type == BackpackType.SUPER && carried.isDamageableItem()
							&& target.getDamageValue() != carried.getDamageValue()) {
						// 超级终端:耐久取加权平均
						int total = target.getCount() + add;
						int avg = Math.round(((float) (target.getCount() * target.getDamageValue()
								+ add * carried.getDamageValue())) / total);
						target.setDamageValue(Math.min(avg, target.getMaxDamage()));
					}
					target.grow(add);
					carried.shrink(add);
					changed = true;
				}
			}
			if (carried.getCount() <= 0) {
				menu.setCarried(ItemStack.EMPTY);
			}
		}

		if (changed) {
			for (int i = 0; i < stacks.size(); i++) {
				ItemStack stack = stacks.get(i);
				if (stack == null || stack.getCount() <= 0) {
					// 取空的格子归一成 EMPTY:count=0 的真物品写盘会被读回成 1 个(凭空多东西)
					stacks.set(i, ItemStack.EMPTY);
				}
			}
			ItemStack copy = equipped.copy();
			copy.set(BackpackMod.BACKPACK_CONTENTS, new BackpackContents(List.copyOf(stacks)));
			player.setAttached(BackpackMod.EQUIPPED_BACKPACK, copy);
			menu.broadcastChanges();
		}
	}

	/** 整堆塞进玩家背包:先并已有同类堆,再放空格;返回是否搬动了东西 */
	private static boolean moveIntoPlayerInventory(ServerPlayer player, ItemStack target) {
		Inventory inv = player.getInventory();
		int size = inv.getContainerSize();
		boolean moved = false;
		for (int i = 0; i < size && !target.isEmpty(); i++) {
			ItemStack cur = inv.getItem(i);
			if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, target)) {
				int add = Math.min(cur.getMaxStackSize() - cur.getCount(), target.getCount());
				if (add > 0) {
					cur.grow(add);
					inv.setItem(i, cur);
					target.shrink(add);
					moved = true;
				}
			}
		}
		for (int i = 0; i < size && !target.isEmpty(); i++) {
			if (inv.getItem(i).isEmpty()) {
				int add = Math.min(target.getCount(), target.getMaxStackSize());
				inv.setItem(i, target.copyWithCount(add));
				target.shrink(add);
				moved = true;
			}
		}
		return moved;
	}
}
