package net.mile.superfurnace;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 熔炉升级配方:8 个升级材料(金锭/钻石/下界合金锭)+ 1 个对应熔炉,
 * 任意摆成环形(MMM/MFM/MMM)→ 熔炉等级 +1。
 * 无状态单例配方,等级判断全在 matches 里动态做,所以一个配方覆盖所有等级。
 * 满级后不再匹配;每次合成恰好消耗 8 材料 + 1 熔炉(每格各扣 1)。
 */
public class FurnaceUpgradeRecipe implements CraftingRecipe {
	public static final FurnaceUpgradeRecipe INSTANCE = new FurnaceUpgradeRecipe();
	public static final RecipeSerializer<FurnaceUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(
			MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

	/** 熔炉升级一次要的材料格数(也是材料个数) */
	private static final int MATERIAL_SLOTS = 8;

	@Override
	public boolean matches(CraftingInput input, Level level) {
		// 第一遍:找到熔炉(必须恰好 1 格)
		FurnaceTier tier = null;
		ItemStack furnace = ItemStack.EMPTY;
		int furnaceSlots = 0;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty()) {
				continue;
			}
			FurnaceTier st = furnaceTierOf(stack);
			if (st != null) {
				furnaceSlots++;
				tier = st;
				furnace = stack;
			}
		}
		if (furnaceSlots != 1 || tier == null || tier.upgradeMaterial == null) {
			return false;
		}
		if (currentLevel(furnace) >= tier.maxLevel) {
			return false; // 已满级
		}
		// 第二遍:其余格子必须恰好 8 格本等级升级材料
		int materialSlots = 0;
		for (ItemStack stack : input.items()) {
			if (stack.isEmpty() || furnaceTierOf(stack) != null) {
				continue;
			}
			if (stack.getItem() != tier.upgradeMaterial) {
				return false;
			}
			materialSlots++;
		}
		return materialSlots == MATERIAL_SLOTS;
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		for (ItemStack stack : input.items()) {
			if (!stack.isEmpty() && furnaceTierOf(stack) != null) {
				ItemStack result = stack.copy();
				result.setCount(1);
				result.set(SuperFurnaceMod.LEVEL, currentLevel(stack) + 1);
				return result;
			}
		}
		return ItemStack.EMPTY;
	}

	private static FurnaceTier furnaceTierOf(ItemStack stack) {
		for (FurnaceTier tier : FurnaceTier.values()) {
			if (stack.getItem() == tier.item) {
				return tier;
			}
		}
		return null;
	}

	/** 物品上的等级组件(没写就是 1 级) */
	public static int currentLevel(ItemStack stack) {
		Integer level = stack.get(SuperFurnaceMod.LEVEL);
		return level == null ? 1 : level;
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
	public RecipeSerializer<FurnaceUpgradeRecipe> getSerializer() {
		return SERIALIZER;
	}
}
