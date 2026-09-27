package net.mile.superdiamond;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/**
 * 超级下界合金套装的最终减伤:每件 5%,集齐全四件再额外 +10%(满套共 30%)。
 * 在护甲/药水等所有减免计算完之后,对最终伤害按比例削减。
 */
public final class SuperDamageReduction {
	/** 每件护甲提供的最终减伤比例(未集齐全套时) */
	public static final float REDUCTION_PER_PIECE = 0.05F;
	/** 集齐全套(4 件)后的额外减伤 */
	public static final float FULL_SET_BONUS = 0.10F;
	/** 满套件数 */
	public static final int FULL_SET_PIECES = 4;

	public static float applyFinalReduction(LivingEntity entity, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount; // 虚空、/kill 等无视一切的伤害不减免
		}
		int pieces = countSuperNetheritePieces(entity);
		if (pieces <= 0) {
			return amount;
		}
		float reduction = REDUCTION_PER_PIECE * pieces;
		if (pieces >= FULL_SET_PIECES) {
			reduction += FULL_SET_BONUS;
		}
		return amount * (1.0F - reduction);
	}

	/** 统计身上穿着的超级下界合金护甲件数(盔/甲/腿/靴,最多 4)。 */
	public static int countSuperNetheritePieces(LivingEntity entity) {
		int pieces = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!slot.isArmor()) {
				continue;
			}
			Item item = entity.getItemBySlot(slot).getItem();
			if (item == SuperItems.SUPER_NETHERITE_HELMET
					|| item == SuperItems.SUPER_NETHERITE_CHESTPLATE
					|| item == SuperItems.SUPER_NETHERITE_LEGGINGS
					|| item == SuperItems.SUPER_NETHERITE_BOOTS) {
				pieces++;
			}
		}
		return pieces;
	}

	private SuperDamageReduction() {
	}
}
