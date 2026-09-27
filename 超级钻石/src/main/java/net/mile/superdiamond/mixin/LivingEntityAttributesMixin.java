package net.mile.superdiamond.mixin;

import net.mile.superdiamond.SuperAttributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 purple_armor 属性加进所有生物(含玩家)的属性表。
 * 只在 ATTRIBUTE 注册表里注册还不够——属性表(AttributeSupplier)里没有的话,
 * 穿上护甲后 getAttributeValue 会抛 "Can't find attribute superdiamond:purple_armor" 直接崩客户端。
 * Player.createAttributes / Monster.createMonsterAttributes 等全都从
 * createLivingAttributes() 起步,注入这一处即可全覆盖,且每张表只加一次不会重复 key。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAttributesMixin {
	@Inject(method = "createLivingAttributes", at = @At("RETURN"))
	private static void superdiamond$addPurpleArmorAttribute(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
		cir.getReturnValue().add(SuperAttributes.PURPLE_ARMOR);
	}
}
