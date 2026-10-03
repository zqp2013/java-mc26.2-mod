package net.mile.backpack;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 掉落物自动收进终端:玩家碰到掉落物时,如果身上的终端里已经有同类物品,
 * 就直接并进那格堆里(只并已有堆,不开新格——"终端里有这个物品才吸")。
 * 合并规则和菜单里完全一致(同物品同组件;超级终端带耐久物品每格 100、耐久取加权平均),
 * 改 BackpackMenu.canBackpackMerge / contentCap 时这里要同步改。
 */
public final class TerminalPickup {

	private TerminalPickup() {
	}

	/**
	 * 尝试把 stack 吸收进玩家身上终端的已有同类堆里(会真正减少 stack 的数量)。
	 * 返回吸收的数量;0 = 终端里没有可并的同类堆,走原版拾取。
	 */
	public static int absorb(Player player, ItemStack stack) {
		if (player.level().isClientSide() || player.isSpectator() || stack.isEmpty()) {
			return 0;
		}
		ItemStack equipped = player.getAttachedOrElse(BackpackMod.EQUIPPED_BACKPACK, ItemStack.EMPTY);
		BackpackType type = BackpackType.fromItem(equipped.getItem());
		if (type == null || !type.unlimited()) {
			return 0; // 只有终端类背包有这个能力
		}
		if (BackpackType.fromItem(stack.getItem()) != null) {
			return 0; // 背包不能装背包(和内容槽 mayPlace 一致)
		}

		List<ItemStack> saved = BackpackContents.read(equipped);
		List<ItemStack> stacks = new ArrayList<>(saved);
		int cap = contentCap(type, stack);
		int absorbed = 0;
		for (ItemStack current : stacks) {
			if (current.isEmpty() || !canMerge(type, current, stack)) {
				continue;
			}
			int room = cap - current.getCount();
			if (room <= 0) {
				continue;
			}
			int add = Math.min(room, stack.getCount());
			if (type == BackpackType.SUPER && stack.isDamageableItem()
					&& current.getDamageValue() != stack.getDamageValue()) {
				// 超级终端:耐久取加权平均
				int total = current.getCount() + add;
				int avg = Math.round(((float) (current.getCount() * current.getDamageValue()
						+ add * stack.getDamageValue())) / total);
				current.setDamageValue(Math.min(avg, current.getMaxDamage()));
			}
			current.grow(add);
			stack.shrink(add);
			absorbed += add;
			if (stack.isEmpty()) {
				break;
			}
		}
		if (absorbed > 0) {
			// 和 BackpackContainer.persist 一样:换副本写组件,确保附件同步能感知变化
			ItemStack copy = equipped.copy();
			copy.set(BackpackMod.BACKPACK_CONTENTS, new BackpackContents(List.copyOf(stacks)));
			player.setAttached(BackpackMod.EQUIPPED_BACKPACK, copy);
		}
		return absorbed;
	}

	/** 与 BackpackMenu.contentCap 一致(TerminalTransfer 也用) */
	static int contentCap(BackpackType type, ItemStack stack) {
		return type == BackpackType.SUPER && stack.isDamageableItem() ? 100 : Integer.MAX_VALUE;
	}

	/** 与 BackpackMenu.canBackpackMerge 一致(unlimited 分支;TerminalTransfer 也用) */
	static boolean canMerge(BackpackType type, ItemStack inSlot, ItemStack incoming) {
		if (type == BackpackType.SUPER) {
			if (incoming.isDamageableItem()) {
				return sameItemAndModifiers(inSlot, incoming);
			}
			return ItemStack.isSameItemSameComponents(inSlot, incoming);
		}
		if (!ItemStack.isSameItemSameComponents(inSlot, incoming)) {
			return false;
		}
		return incoming.getMaxStackSize() > 1 || incoming.isDamageableItem();
	}

	/** 与 BackpackMenu.sameItemAndModifiers 一致:同物品且除耐久外组件一致 */
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
}
