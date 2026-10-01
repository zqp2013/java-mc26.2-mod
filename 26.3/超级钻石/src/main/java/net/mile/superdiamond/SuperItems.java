package net.mile.superdiamond;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * 超级钻石 / 超级下界合金 全部物品与材料。
 *
 * 数值设计:
 *  - 护甲的 ARMOR 属性 = 白 + 紫(紫色部分同样真实参与减伤),
 *    紫色部分额外记录在 SuperAttributes.PURPLE_ARMOR 供 HUD 显示。
 *  - 超级钻石:白 3/8/6/3,紫 2/4/3/2(共 11),韧性 3/件。
 *  - 超级下界合金:白 3/8/6/3,紫 3/6/4/3(共 16),韧性 4/件,击退抗性 0.1/件。
 */
public final class SuperItems {
	// ---- 修复用物品标签(data/superdiamond/tags/item/) ----
	private static final TagKey<Item> REPAIRS_SUPER_DIAMOND = TagKey.create(Registries.ITEM, SuperDiamondMod.id("repairs_super_diamond"));
	private static final TagKey<Item> REPAIRS_SUPER_NETHERITE = TagKey.create(Registries.ITEM, SuperDiamondMod.id("repairs_super_netherite"));

	// ---- 掉落物免疫的伤害类型标签(data/superdiamond/tags/damage_type/) ----
	// 超级下界合金制品的掉落物免疫火焰与爆炸(TNT/苦力怕/烟花/床爆炸等),不会消失
	// (SuperBlocks 里的超级下界合金块也要用,包内可见)
	static final TagKey<DamageType> SUPER_NETHERITE_IMMUNE =
			TagKey.create(Registries.DAMAGE_TYPE, SuperDiamondMod.id("super_netherite_immune"));

	// ---- 装备资源 key(assets/superdiamond/equipment/) ----
	private static final ResourceKey<EquipmentAsset> SUPER_DIAMOND_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, SuperDiamondMod.id("super_diamond"));
	private static final ResourceKey<EquipmentAsset> SUPER_NETHERITE_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, SuperDiamondMod.id("super_netherite"));

	// ---- 护甲材料(durability, 防御, 附魔值, 装备音效, 韧性, 击退抗性, 修复标签, 装备资源) ----
	public static final ArmorMaterial SUPER_DIAMOND_ARMOR = new ArmorMaterial(
			40,
			Map.of(
					ArmorType.HELMET, 3,
					ArmorType.CHESTPLATE, 8,
					ArmorType.LEGGINGS, 6,
					ArmorType.BOOTS, 3,
					ArmorType.BODY, 5),
			18,
			SoundEvents.ARMOR_EQUIP_DIAMOND,
			3.0F,
			0.0F,
			REPAIRS_SUPER_DIAMOND,
			SUPER_DIAMOND_ASSET);

	public static final ArmorMaterial SUPER_NETHERITE_ARMOR = new ArmorMaterial(
			48,
			Map.of(
					ArmorType.HELMET, 3,
					ArmorType.CHESTPLATE, 8,
					ArmorType.LEGGINGS, 6,
					ArmorType.BOOTS, 3,
					ArmorType.BODY, 5),
			20,
			SoundEvents.ARMOR_EQUIP_NETHERITE,
			4.0F,
			0.1F,
			REPAIRS_SUPER_NETHERITE,
			SUPER_NETHERITE_ASSET);

	// ---- 工具材料(错误方块标签, 耐久, 挖掘速度, 攻击加成, 附魔值, 修复标签) ----
	// 镐/斧/铲另有 *_FAST 变体:挖掘速度对齐金工具(超级钻石 12.0=金工具速度,
	// 超级下界合金 14.4=金工具的 120%),其余参数不变。
	public static final ToolMaterial SUPER_DIAMOND_TOOL =
			new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2500, 9.0F, 4.5F, 18, REPAIRS_SUPER_DIAMOND);
	public static final ToolMaterial SUPER_DIAMOND_TOOL_FAST =
			new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2500, 12.0F, 4.5F, 18, REPAIRS_SUPER_DIAMOND);
	public static final ToolMaterial SUPER_NETHERITE_TOOL =
			new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3500, 10.0F, 5.5F, 20, REPAIRS_SUPER_NETHERITE);
	public static final ToolMaterial SUPER_NETHERITE_TOOL_FAST =
			new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3500, 14.4F, 5.5F, 20, REPAIRS_SUPER_NETHERITE);

	// ---- 材料 ----
	public static final Item SUPER_DIAMOND = register("super_diamond", Item::new,
			new Item.Properties().rarity(Rarity.RARE));
	public static final Item SUPER_NETHERITE_INGOT = register("super_netherite_ingot", Item::new,
			new Item.Properties().delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE))).rarity(Rarity.EPIC));

	// ---- 超级下界合金升级锻造模板 ----
	public static final Item SUPER_NETHERITE_UPGRADE_SMITHING_TEMPLATE = register("super_netherite_upgrade_smithing_template",
			p -> new SmithingTemplateItem(
					Component.translatable("smithing_template.applies_to"),
					Component.translatable("smithing_template.ingredients"),
					Component.translatable("superdiamond.smithing_template.super_netherite_upgrade.applies_to"),
					Component.translatable("superdiamond.smithing_template.super_netherite_upgrade.ingredients"),
					List.of(
							vanillaId("container/slot/sword"),
							vanillaId("container/slot/pickaxe"),
							vanillaId("container/slot/axe"),
							vanillaId("container/slot/shovel"),
							vanillaId("container/slot/hoe"),
							vanillaId("container/slot/spear")),
					List.of(vanillaId("container/slot/ingot")),
					p),
			new Item.Properties().delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE))).rarity(Rarity.EPIC));

	// ---- 超级钻石装备 ----
	public static final Item SUPER_DIAMOND_SWORD = register("super_diamond_sword", Item::new,
			new Item.Properties().sword(SUPER_DIAMOND_TOOL, 3.5F, -2.4F).rarity(Rarity.RARE));
	public static final Item SUPER_DIAMOND_PICKAXE = register("super_diamond_pickaxe", Item::new,
			new Item.Properties().pickaxe(SUPER_DIAMOND_TOOL_FAST, 1.5F, -2.8F).rarity(Rarity.RARE));
	public static final Item SUPER_DIAMOND_AXE = register("super_diamond_axe", Item::new,
			new Item.Properties().axe(SUPER_DIAMOND_TOOL_FAST, 6.0F, -3.0F).rarity(Rarity.RARE));
	public static final Item SUPER_DIAMOND_SHOVEL = register("super_diamond_shovel", Item::new,
			new Item.Properties().shovel(SUPER_DIAMOND_TOOL_FAST, 1.5F, -3.0F).rarity(Rarity.RARE));
	public static final Item SUPER_DIAMOND_HOE = register("super_diamond_hoe", Item::new,
			new Item.Properties().hoe(SUPER_DIAMOND_TOOL, -3.0F, 0.0F).rarity(Rarity.RARE));
	// 矛:9 个浮点参数与原版钻石矛完全一致(投掷/伤害物理参数)
	public static final Item SUPER_DIAMOND_SPEAR = register("super_diamond_spear", Item::new,
			new Item.Properties().spear(SUPER_DIAMOND_TOOL, 1.05F, 1.075F, 0.5F, 3.0F, 10.0F, 6.5F, 5.1F, 10.0F, 4.6F).rarity(Rarity.RARE));

	public static final Item SUPER_DIAMOND_HELMET = register("super_diamond_helmet", Item::new,
			new Item.Properties()
					.humanoidArmor(SUPER_DIAMOND_ARMOR, ArmorType.HELMET)
					.attributes(superArmorAttributes(ArmorType.HELMET, 3, 2, 3.0F, 0.0F))
					.rarity(Rarity.RARE));
	public static final Item SUPER_DIAMOND_CHESTPLATE = register("super_diamond_chestplate", Item::new,
			new Item.Properties()
					.humanoidArmor(SUPER_DIAMOND_ARMOR, ArmorType.CHESTPLATE)
					.attributes(superArmorAttributes(ArmorType.CHESTPLATE, 8, 4, 3.0F, 0.0F))
					.rarity(Rarity.RARE));
	public static final Item SUPER_DIAMOND_LEGGINGS = register("super_diamond_leggings", Item::new,
			new Item.Properties()
					.humanoidArmor(SUPER_DIAMOND_ARMOR, ArmorType.LEGGINGS)
					.attributes(superArmorAttributes(ArmorType.LEGGINGS, 6, 3, 3.0F, 0.0F))
					.rarity(Rarity.RARE));
	public static final Item SUPER_DIAMOND_BOOTS = register("super_diamond_boots", Item::new,
			new Item.Properties()
					.humanoidArmor(SUPER_DIAMOND_ARMOR, ArmorType.BOOTS)
					.attributes(superArmorAttributes(ArmorType.BOOTS, 3, 2, 3.0F, 0.0F))
					.rarity(Rarity.RARE));

	// ---- 超级下界合金装备(锻造台升级获得) ----
	public static final Item SUPER_NETHERITE_SWORD = register("super_netherite_sword", Item::new,
			new Item.Properties().sword(SUPER_NETHERITE_TOOL, 4.0F, -2.4F).delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE))).rarity(Rarity.EPIC));
	public static final Item SUPER_NETHERITE_PICKAXE = register("super_netherite_pickaxe", Item::new,
			new Item.Properties().pickaxe(SUPER_NETHERITE_TOOL_FAST, 2.0F, -2.8F).delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE))).rarity(Rarity.EPIC));
	public static final Item SUPER_NETHERITE_AXE = register("super_netherite_axe", Item::new,
			new Item.Properties().axe(SUPER_NETHERITE_TOOL_FAST, 7.0F, -3.0F).delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE))).rarity(Rarity.EPIC));
	public static final Item SUPER_NETHERITE_SHOVEL = register("super_netherite_shovel", Item::new,
			new Item.Properties().shovel(SUPER_NETHERITE_TOOL_FAST, 2.0F, -3.0F).delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE))).rarity(Rarity.EPIC));
	public static final Item SUPER_NETHERITE_HOE = register("super_netherite_hoe", Item::new,
			new Item.Properties().hoe(SUPER_NETHERITE_TOOL, -3.0F, 0.0F).delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE))).rarity(Rarity.EPIC));
	// 矛:9 个浮点参数与原版下界合金矛完全一致
	public static final Item SUPER_NETHERITE_SPEAR = register("super_netherite_spear", Item::new,
			new Item.Properties().spear(SUPER_NETHERITE_TOOL, 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F).delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE))).rarity(Rarity.EPIC));

	public static final Item SUPER_NETHERITE_HELMET = register("super_netherite_helmet", SuperArmorItem::new,
			new Item.Properties()
					.humanoidArmor(SUPER_NETHERITE_ARMOR, ArmorType.HELMET)
					.attributes(superArmorAttributes(ArmorType.HELMET, 3, 3, 4.0F, 0.1F))
					.delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE)))
					.rarity(Rarity.EPIC));
	public static final Item SUPER_NETHERITE_CHESTPLATE = register("super_netherite_chestplate", SuperArmorItem::new,
			new Item.Properties()
					.humanoidArmor(SUPER_NETHERITE_ARMOR, ArmorType.CHESTPLATE)
					.attributes(superArmorAttributes(ArmorType.CHESTPLATE, 8, 6, 4.0F, 0.1F))
					.delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE)))
					.rarity(Rarity.EPIC));
	public static final Item SUPER_NETHERITE_LEGGINGS = register("super_netherite_leggings", SuperArmorItem::new,
			new Item.Properties()
					.humanoidArmor(SUPER_NETHERITE_ARMOR, ArmorType.LEGGINGS)
					.attributes(superArmorAttributes(ArmorType.LEGGINGS, 6, 4, 4.0F, 0.1F))
					.delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE)))
					.rarity(Rarity.EPIC));
	public static final Item SUPER_NETHERITE_BOOTS = register("super_netherite_boots", SuperArmorItem::new,
			new Item.Properties()
					.humanoidArmor(SUPER_NETHERITE_ARMOR, ArmorType.BOOTS)
					.attributes(superArmorAttributes(ArmorType.BOOTS, 3, 3, 4.0F, 0.1F))
					.delayedComponent(DataComponents.DAMAGE_RESISTANT, provider -> new DamageResistant(provider.getOrThrow(SUPER_NETHERITE_IMMUNE)))
					.rarity(Rarity.EPIC));

	/**
	 * 26.2 注册物品必须先在 Properties 上 setId,再构造 Item,最后注册。
	 */
	private static Item register(String name, Function<Item.Properties, ? extends Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SuperDiamondMod.id(name));
		properties.setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties));
	}

	private static Identifier vanillaId(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	/**
	 * 自定义护甲属性:总护甲(白+紫) + 韧性 + 紫色护甲(仅 HUD 用) (+击退抗性)。
	 * 会覆盖 humanoidArmor() 里由材料生成的默认属性。
	 * 修改器 id 必须按部位区分(同原版 "armor."+name 的做法):AttributeInstance
	 * 里 modifierById 是按 id 的 Map,四件共用一个 id 会互相顶掉,全穿只算一件。
	 */
	private static ItemAttributeModifiers superArmorAttributes(ArmorType type, int white, int purple, float toughness, float knockbackResistance) {
		EquipmentSlotGroup group = switch (type) {
			case HELMET -> EquipmentSlotGroup.HEAD;
			case CHESTPLATE -> EquipmentSlotGroup.CHEST;
			case LEGGINGS -> EquipmentSlotGroup.LEGS;
			case BOOTS -> EquipmentSlotGroup.FEET;
			case BODY -> EquipmentSlotGroup.BODY;
		};
		String slot = type.getName();
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR,
						new AttributeModifier(SuperDiamondMod.id("super_armor." + slot), white + purple, AttributeModifier.Operation.ADD_VALUE),
						group)
				.add(Attributes.ARMOR_TOUGHNESS,
						new AttributeModifier(SuperDiamondMod.id("super_armor_toughness." + slot), toughness, AttributeModifier.Operation.ADD_VALUE),
						group)
				.add(SuperAttributes.PURPLE_ARMOR,
						new AttributeModifier(SuperDiamondMod.id("purple_armor." + slot), purple, AttributeModifier.Operation.ADD_VALUE),
						group);
		if (knockbackResistance > 0.0F) {
			builder.add(Attributes.KNOCKBACK_RESISTANCE,
					new AttributeModifier(SuperDiamondMod.id("super_knockback_resistance." + slot), knockbackResistance, AttributeModifier.Operation.ADD_VALUE),
					group);
		}
		return builder.build();
	}

	public static void init() {
		// 触发类加载,完成物品注册
	}

	private SuperItems() {
	}
}
