package net.mile.superdiamond;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuperDiamondMod implements ModInitializer {
	public static final String MOD_ID = "superdiamond";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ResourceKey<CreativeModeTab> COMBAT_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "combat"));
	private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "ingredients"));
	private static final ResourceKey<CreativeModeTab> BUILDING_BLOCKS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "building_blocks"));

	@Override
	public void onInitialize() {
		SuperAttributes.init();
		SuperItems.init();
		SuperBlocks.init();

		// 战斗栏:全部装备
		CreativeModeTabEvents.modifyOutputEvent(COMBAT_TAB).register(output -> {
			output.accept(SuperItems.SUPER_DIAMOND_SWORD);
			output.accept(SuperItems.SUPER_DIAMOND_PICKAXE);
			output.accept(SuperItems.SUPER_DIAMOND_AXE);
			output.accept(SuperItems.SUPER_DIAMOND_SHOVEL);
			output.accept(SuperItems.SUPER_DIAMOND_HOE);
			output.accept(SuperItems.SUPER_DIAMOND_SPEAR);
			output.accept(SuperItems.SUPER_DIAMOND_HELMET);
			output.accept(SuperItems.SUPER_DIAMOND_CHESTPLATE);
			output.accept(SuperItems.SUPER_DIAMOND_LEGGINGS);
			output.accept(SuperItems.SUPER_DIAMOND_BOOTS);
			output.accept(SuperItems.SUPER_NETHERITE_SWORD);
			output.accept(SuperItems.SUPER_NETHERITE_PICKAXE);
			output.accept(SuperItems.SUPER_NETHERITE_AXE);
			output.accept(SuperItems.SUPER_NETHERITE_SHOVEL);
			output.accept(SuperItems.SUPER_NETHERITE_HOE);
			output.accept(SuperItems.SUPER_NETHERITE_SPEAR);
			output.accept(SuperItems.SUPER_NETHERITE_HELMET);
			output.accept(SuperItems.SUPER_NETHERITE_CHESTPLATE);
			output.accept(SuperItems.SUPER_NETHERITE_LEGGINGS);
			output.accept(SuperItems.SUPER_NETHERITE_BOOTS);
		});

		// 材料栏:材料与锻造模板
		CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS_TAB).register(output -> {
			output.accept(SuperItems.SUPER_DIAMOND);
			output.accept(SuperItems.SUPER_NETHERITE_INGOT);
			output.accept(SuperItems.SUPER_NETHERITE_UPGRADE_SMITHING_TEMPLATE);
		});

		// 建筑方块栏:两种存储方块(与原版钻石块同栏)
		CreativeModeTabEvents.modifyOutputEvent(BUILDING_BLOCKS_TAB).register(output -> {
			output.accept(SuperBlocks.SUPER_DIAMOND_BLOCK);
			output.accept(SuperBlocks.SUPER_NETHERITE_BLOCK);
		});

		LOGGER.info("超级钻石模组加载完毕:超级钻石、超级下界合金与紫色护甲已就绪!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
