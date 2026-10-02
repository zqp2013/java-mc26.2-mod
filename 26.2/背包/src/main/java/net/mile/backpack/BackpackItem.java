package net.mile.backpack;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class BackpackItem extends Item {

	private final BackpackType type;

	public BackpackItem(BackpackType type, Properties properties) {
		super(properties);
		this.type = type;
		type.item(this);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		if (this.type == BackpackType.CRAFTING) {
			tooltip.accept(Component.translatable("tooltip.backpack.crafting_1").withStyle(ChatFormatting.GRAY));
			tooltip.accept(Component.translatable("tooltip.backpack.crafting_2").withStyle(ChatFormatting.GRAY));
			return;
		}
		tooltip.accept(Component.translatable("tooltip.backpack.slots", this.type.slots).withStyle(ChatFormatting.GRAY));
		if (this.type == BackpackType.SUPER) {
			tooltip.accept(Component.translatable("tooltip.backpack.paged").withStyle(ChatFormatting.GRAY));
			tooltip.accept(Component.translatable("tooltip.backpack.damage_cap").withStyle(ChatFormatting.GRAY));
		} else if (this.type.unlimited()) {
			tooltip.accept(Component.translatable("tooltip.backpack.unlimited").withStyle(ChatFormatting.GRAY));
		}
	}
}
