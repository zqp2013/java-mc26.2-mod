package net.mile.backpack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 背包模组成就的服务端授予逻辑。
 * 获得类成就(造出各档背包/合成终端)走数据包 inventory_changed 触发器自动解锁;
 * 这里只处理代码判定的:批量合成、填满终端、超级终端里的工具收藏系列。
 */
public final class BackpackAdvancements {

	private BackpackAdvancements() {
	}

	/** 直接授予一个成就(遍历它全部条件并点亮) */
	public static void award(Player player, String path) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		MinecraftServer server = serverPlayer.level().getServer();
		if (server == null) {
			return;
		}
		AdvancementHolder holder = server.getAdvancements().get(BackpackMod.id(path));
		if (holder == null) {
			return;
		}
		for (String criterion : holder.value().criteria().keySet()) {
			serverPlayer.getAdvancements().award(holder, criterion);
		}
	}

	/** 关闭背包界面时检查:填满终端 / 超级终端的工具收藏系列 */
	public static void checkTerminal(ServerPlayer player) {
		ItemStack equipped = player.getAttachedOrElse(BackpackMod.EQUIPPED_BACKPACK, ItemStack.EMPTY);
		BackpackType type = BackpackType.fromItem(equipped.getItem());
		if (type == null) {
			return;
		}
		List<ItemStack> stacks = BackpackContents.read(equipped);
		int filled = 0;
		int damageable = 0;
		Map<Item, Integer> counts = new HashMap<>();
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			filled++;
			if (stack.isDamageableItem()) {
				damageable += stack.getCount();
			}
			counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		if (type == BackpackType.TERMINAL) {
			if (filled >= 72) {
				award(player, "fill_terminal");
			}
		} else if (type == BackpackType.SUPER) {
			if (filled >= 900) {
				award(player, "fill_super");
			}
			if (damageable >= 1800) {
				award(player, "many_tools");
			}
			if (count(counts, Items.DIAMOND_AXE) >= 100) {
				award(player, "lumberjack");
			}
			if (count(counts, Items.NETHERITE_AXE) >= 100) {
				award(player, "lumberjack_pro");
			}
			if (count(counts, Items.DIAMOND_SWORD) >= 100) {
				award(player, "sword_master");
			}
			if (count(counts, Items.NETHERITE_SWORD) >= 100) {
				award(player, "sword_master_pro");
			}
			if (count(counts, Items.DIAMOND_SHOVEL) >= 100 && count(counts, Items.DIAMOND_PICKAXE) >= 100) {
				award(player, "dig_through_earth");
			}
			if (count(counts, Items.NETHERITE_SHOVEL) >= 100 && count(counts, Items.NETHERITE_PICKAXE) >= 100) {
				award(player, "dig_through_universe");
			}
			if (count(counts, Items.DIAMOND_HOE) >= 100) {
				award(player, "farmer");
			}
			if (count(counts, Items.NETHERITE_HOE) >= 100) {
				award(player, "ultimate_dedication");
			}
		}
	}

	private static int count(Map<Item, Integer> counts, Item item) {
		return counts.getOrDefault(item, 0);
	}
}
