package net.mile.superfurnace;

import com.mojang.serialization.Codec;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 超级熔炉:铜熔炉 8s → 铁熔炉 5s → 金熔炉 5s(2燃料/4输入/5成品)
 * → 钻石熔炉 4s(3燃料/5输入/8成品) → 下界合金熔炉 3.5s(锻造台升级,5燃料/10输入/15成品)。
 * 铜熔炉 = 8 铜锭 + 熔炉;铁 = 8 铁锭 + 铜熔炉;金 = 8 金锭 + 铁熔炉;钻 = 8 钻石 + 金熔炉。
 */
public class SuperFurnaceMod implements ModInitializer {
	public static final String MOD_ID = "superfurnace";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 熔炉等级组件(物品/方块实体通用,没写默认 1 级) */
	public static final DataComponentType<Integer> LEVEL = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE, id("level"),
			new DataComponentType.Builder<Integer>()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "functional_blocks"));

	@Override
	public void onInitialize() {
		// 熔炉升级配方(代码实现的单例特殊配方,JSON 里只写 type)
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("furnace_upgrade"),
				FurnaceUpgradeRecipe.SERIALIZER);

		for (FurnaceTier tier : FurnaceTier.values()) {
			registerTier(tier);
		}

		// 材质升级配方(金→钻 走合成台;钻→下界合金 走锻造台),都只收 1 级熔炉。
		// 必须放在 registerTier 之后:配方构造时要拿 tier.item,注册太早还是 null
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("tier_upgrade"),
				TierUpgradeRecipe.SERIALIZER);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("netherite_smithing"),
				NetheriteSmithingRecipe.SERIALIZER);

		// 功能方块栏(和原版熔炉同栏)
		CreativeModeTabEvents.modifyOutputEvent(FUNCTIONAL_TAB).register(output -> {
			for (FurnaceTier tier : FurnaceTier.values()) {
				output.accept(tier.item);
			}
		});

		LOGGER.info("超级熔炉加载完毕:铜→铁→金→钻石→下界合金 五级熔炉已就绪!");
	}

	private static void registerTier(FurnaceTier tier) {
		DyeColor color = switch (tier) {
			case COPPER -> DyeColor.ORANGE;
			case IRON -> DyeColor.LIGHT_GRAY;
			case GOLD -> DyeColor.YELLOW;
			case DIAMOND -> DyeColor.LIGHT_BLUE;
			case NETHERITE -> DyeColor.GRAY;
		};

		// 方块
		ResourceKey<net.minecraft.world.level.block.Block> blockKey =
				ResourceKey.create(Registries.BLOCK, id(tier.name));
		tier.block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new SuperFurnaceBlock(tier,
				BlockBehaviour.Properties.of()
						.mapColor(color)
						.strength(3.5F)
						.requiresCorrectToolForDrops()
						.sound(SoundType.METAL)
						.setId(blockKey)));

		// 方块物品(26.2 要显式走 block.* 命名,lang 才生效)
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id(tier.name));
		tier.item = Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(tier.block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

		// 方块实体
		tier.blockEntityType = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id(tier.name),
				new BlockEntityType<>((pos, state) -> new SuperFurnaceBlockEntity(tier, pos, state),
						Set.of(tier.block)));

		// 菜单类型(客户端用假容器按同套布局构造,内容随后由服务端同步)
		MenuType<SuperFurnaceMenu>[] box = new MenuType[1];
		box[0] = Registry.register(BuiltInRegistries.MENU, id(tier.name),
				new MenuType<>((menuId, inventory) -> new SuperFurnaceMenu(box[0], menuId, inventory,
						new net.minecraft.world.SimpleContainer(tier.totalSlots),
						new net.minecraft.world.inventory.SimpleContainerData(5),
						tier, net.minecraft.world.inventory.ContainerLevelAccess.NULL), FeatureFlags.VANILLA_SET));
		tier.menuType = box[0];
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
