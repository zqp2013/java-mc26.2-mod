package net.mile.superdiamond;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 超级钻石块 / 超级下界合金块。
 * 属性对齐原版钻石块(硬度 5 / 爆炸抗性 1200,需要正确工具才会掉落)。
 */
public final class SuperBlocks {
	public static final Block SUPER_DIAMOND_BLOCK = register("super_diamond_block", DyeColor.LIGHT_BLUE, false);
	public static final Block SUPER_NETHERITE_BLOCK = register("super_netherite_block", DyeColor.PURPLE, true);

	private static Block register(String name, DyeColor mapColor, boolean fireProof) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, SuperDiamondMod.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new Block(BlockBehaviour.Properties.of()
				.mapColor(mapColor)
				.strength(5.0F, 1200.0F)
				.requiresCorrectToolForDrops()
				.sound(SoundType.METAL)
				.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, SuperDiamondMod.id(name));
		// 26.2:BlockItem 名字默认走 item.<ns>.<path>,要用 block.<ns>.<path>
		// 的原版方块命名约定必须显式声明,否则 lang 里的 block.* 键不生效
		Item.Properties properties = new Item.Properties().setId(itemKey).useBlockDescriptionPrefix();
		if (fireProof) {
			// 超级下界合金块掉落物同样免疫火焰与爆炸
			properties.delayedComponent(DataComponents.DAMAGE_RESISTANT,
					provider -> new DamageResistant(provider.getOrThrow(SuperItems.SUPER_NETHERITE_IMMUNE)));
		}
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, properties));
		return block;
	}

	public static void init() {
		// 触发类加载,完成方块与方块物品注册
	}

	private SuperBlocks() {
	}
}
