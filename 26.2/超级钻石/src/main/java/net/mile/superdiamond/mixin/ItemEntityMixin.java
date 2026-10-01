package net.mile.superdiamond.mixin;

import net.mile.superdiamond.Advancements;
import net.mile.superdiamond.SuperItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 用爆炸去炸"掉落物形态"的超级下界合金物品:物品本体免疫伤害(原组件就抗炸),
 * 但爆炸尝试本身被记下来 → 给附近玩家发"真抗炸"。
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void superdiamond$blastProof(ServerLevel serverLevel, DamageSource source, float amount,
			CallbackInfoReturnable<Boolean> cir) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
			return;
		}
		Item item = self.getItem().getItem();
		if (item != SuperItems.SUPER_NETHERITE_INGOT
				&& item != SuperItems.SUPER_NETHERITE_UPGRADE_SMITHING_TEMPLATE
				&& !superdiamond$isNetheriteGear(item)) {
			return;
		}
		// 发给离掉落物最近的玩家(就是引爆炸弹的那位)
		ServerPlayer nearest = null;
		double bestDist = 32.0 * 32.0;
		for (ServerPlayer player : serverLevel.players()) {
			double dist = player.distanceToSqr(self);
			if (dist <= bestDist) {
				nearest = player;
				bestDist = dist;
			}
		}
		if (nearest != null) {
			Advancements.grant(nearest, "blast_proof");
		}
	}

	@Unique
	private static boolean superdiamond$isNetheriteGear(Item item) {
		return item == SuperItems.SUPER_NETHERITE_SWORD
				|| item == SuperItems.SUPER_NETHERITE_AXE
				|| item == SuperItems.SUPER_NETHERITE_SHOVEL
				|| item == SuperItems.SUPER_NETHERITE_HOE
				|| item == SuperItems.SUPER_NETHERITE_SPEAR
				|| item == SuperItems.SUPER_NETHERITE_PICKAXE
				|| item == SuperItems.SUPER_NETHERITE_HELMET
				|| item == SuperItems.SUPER_NETHERITE_CHESTPLATE
				|| item == SuperItems.SUPER_NETHERITE_LEGGINGS
				|| item == SuperItems.SUPER_NETHERITE_BOOTS;
	}
}
