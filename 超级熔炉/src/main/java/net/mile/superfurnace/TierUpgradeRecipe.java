package net.mile.superfurnace;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 材质升级配方(金→钻):8 颗钻石围 1 个金熔炉 → 钻石熔炉。
 * 只收 1 级(没升过等级)的金熔炉——升过级的熔炉不能再升材质,
 * 防止辛苦升上去的等级在合成里被清空。
 */
public class TierUpgradeRecipe implements CraftingRecipe {
	public static final TierUpgradeRecipe INSTANCE = new TierUpgradeRecipe();
	public static final RecipeSerializer<TierUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(
			MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

	private static final int MATERIAL_SLOTS = 8;

	@Override
	public boolean matches(CraftingInput input, Level level) {
		int furnaceSlots = 0;
		int diamondSlots = 0;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() == FurnaceTier.GOLD.item) {
				furnaceSlots++;
			} else if (stack.getItem() == Items.DIAMOND) {
				diamondSlots++;
			} else {
				return false; // 只认钻石和金熔炉
			}
		}
		// 恰好 1 个金熔炉 + 8 格钻石,且金熔炉必须还是 1 级
		return furnaceSlots == 1 && diamondSlots == MATERIAL_SLOTS && freshGoldFurnace(input) != null;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return new ItemStack(FurnaceTier.DIAMOND.item);
	}

	/** 找到网格里那个没升过级的金熔炉(等级非 1 返回 null) */
	private static ItemStack freshGoldFurnace(CraftingInput input) {
		for (ItemStack stack : input.items()) {
			if (!stack.isEmpty() && stack.getItem() == FurnaceTier.GOLD.item
					&& FurnaceUpgradeRecipe.currentLevel(stack) == 1) {
				return stack;
			}
		}
		return null;
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	@Override
	public boolean showNotification() {
		return false;
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public CraftingBookCategory category() {
		return CraftingBookCategory.MISC;
	}

	@Override
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public RecipeSerializer<TierUpgradeRecipe> getSerializer() {
		return SERIALIZER;
	}
}
