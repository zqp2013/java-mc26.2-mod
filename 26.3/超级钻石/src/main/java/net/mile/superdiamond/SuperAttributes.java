package net.mile.superdiamond;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * 模组自定义属性:紫色护甲值。
 * 紫色护甲同时计入普通护甲(ARMOR 属性,真正参与减伤),
 * 这条自定义属性只负责把"其中属于紫色的部分"同步到客户端用于 HUD 渲染。
 */
public final class SuperAttributes {
	public static final ResourceKey<Attribute> PURPLE_ARMOR_KEY =
			ResourceKey.create(Registries.ATTRIBUTE, SuperDiamondMod.id("purple_armor"));

	public static final Holder<Attribute> PURPLE_ARMOR = Registry.registerForHolder(
			BuiltInRegistries.ATTRIBUTE,
			PURPLE_ARMOR_KEY,
			new RangedAttribute("attribute.name.superdiamond.purple_armor", 0.0, 0.0, 30.0).setSyncable(true));

	public static void init() {
		// 触发类加载,完成属性注册
	}

	private SuperAttributes() {
	}
}
