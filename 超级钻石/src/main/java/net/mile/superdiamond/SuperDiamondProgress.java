package net.mile.superdiamond;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

/**
 * 每秒扫一遍玩家背包,按持有物品发进度:
 * 超级钻石 / 首件制品 / 全套超级钻石 / 合金锭 / 模板 / 首件升级 / 全套下界合金。
 * (锻造台升级的产物只有进背包这一条统一入口,扫背包最省事也最准)
 */
public final class SuperDiamondProgress {
	private static final Set<Item> DIAMOND_SET = Set.of(
			SuperItems.SUPER_DIAMOND_SWORD,
			SuperItems.SUPER_DIAMOND_AXE,
			SuperItems.SUPER_DIAMOND_SHOVEL,
			SuperItems.SUPER_DIAMOND_HOE,
			SuperItems.SUPER_DIAMOND_SPEAR,
			SuperItems.SUPER_DIAMOND_PICKAXE,
			SuperItems.SUPER_DIAMOND_HELMET,
			SuperItems.SUPER_DIAMOND_CHESTPLATE,
			SuperItems.SUPER_DIAMOND_LEGGINGS,
			SuperItems.SUPER_DIAMOND_BOOTS);

	private static final Set<Item> NETHERITE_SET = Set.of(
			SuperItems.SUPER_NETHERITE_SWORD,
			SuperItems.SUPER_NETHERITE_AXE,
			SuperItems.SUPER_NETHERITE_SHOVEL,
			SuperItems.SUPER_NETHERITE_HOE,
			SuperItems.SUPER_NETHERITE_SPEAR,
			SuperItems.SUPER_NETHERITE_PICKAXE,
			SuperItems.SUPER_NETHERITE_HELMET,
			SuperItems.SUPER_NETHERITE_CHESTPLATE,
			SuperItems.SUPER_NETHERITE_LEGGINGS,
			SuperItems.SUPER_NETHERITE_BOOTS);

	private SuperDiamondProgress() {
	}

	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			scan(player);
		}
	}

	private static void scan(ServerPlayer player) {
		boolean hasSuperDiamond = false;
		boolean hasIngot = false;
		boolean hasTemplate = false;
		Set<Item> diamondPieces = new HashSet<>();
		Set<Item> netheritePieces = new HashSet<>();

		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			Item item = inventory.getItem(i).getItem();
			if (item == SuperItems.SUPER_DIAMOND) {
				hasSuperDiamond = true;
			} else if (DIAMOND_SET.contains(item)) {
				diamondPieces.add(item);
			} else if (item == SuperItems.SUPER_NETHERITE_INGOT) {
				hasIngot = true;
			} else if (item == SuperItems.SUPER_NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
				hasTemplate = true;
			} else if (NETHERITE_SET.contains(item)) {
				netheritePieces.add(item);
			}
		}

		if (hasSuperDiamond) {
			Advancements.grant(player, "get_super_diamond");
		}
		if (!diamondPieces.isEmpty()) {
			Advancements.grant(player, "first_product");
		}
		if (diamondPieces.size() >= DIAMOND_SET.size()) {
			Advancements.grant(player, "full_diamond_set");
		}
		if (hasIngot) {
			Advancements.grant(player, "get_ingot");
		}
		if (hasTemplate) {
			Advancements.grant(player, "get_template");
		}
		if (!netheritePieces.isEmpty()) {
			Advancements.grant(player, "upgrade_one");
		}
		if (netheritePieces.size() >= NETHERITE_SET.size()) {
			Advancements.grant(player, "full_netherite_set");
		}
	}
}
