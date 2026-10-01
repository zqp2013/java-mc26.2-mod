package net.mile.superdiamond;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 超级下界合金护甲:tooltip 里展示最终减伤效果。
 */
public class SuperArmorItem extends Item {
	public SuperArmorItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(Component.translatable("item.superdiamond.tooltip.final_reduction",
				String.format("%.1f%%", SuperDamageReduction.REDUCTION_PER_PIECE * 100.0F),
				String.format("%.1f%%", (SuperDamageReduction.REDUCTION_PER_PIECE * SuperDamageReduction.FULL_SET_PIECES
						+ SuperDamageReduction.FULL_SET_BONUS) * 100.0F)).withStyle(ChatFormatting.GOLD));
	}
}
