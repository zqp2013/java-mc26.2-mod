package net.mile.superfurnace;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * 材质升级配方(钻→下界合金,锻造台):升级模板 + 钻石熔炉 + 下界合金锭。
 * 只收 1 级(没升过等级)的钻石熔炉——升过级的熔炉不能再升材质,
 * 防止等级在锻造转换里意外继承或丢失。
 */
public class NetheriteSmithingRecipe implements SmithingRecipe {
	public static final NetheriteSmithingRecipe INSTANCE = new NetheriteSmithingRecipe();
	public static final RecipeSerializer<NetheriteSmithingRecipe> SERIALIZER = new RecipeSerializer<>(
			MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

	private final Ingredient template = Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
	private final Ingredient base = Ingredient.of(FurnaceTier.DIAMOND.item);
	private final Ingredient addition = Ingredient.of(Items.NETHERITE_INGOT);

	@Override
	public boolean matches(SmithingRecipeInput input, Level level) {
		// 材质对得上,且钻石熔炉必须还是 1 级(升过级的不能转)
		return template.test(input.template()) && base.test(input.base())
				&& addition.test(input.addition())
				&& FurnaceUpgradeRecipe.currentLevel(input.base()) == 1;
	}

	@Override
	public ItemStack assemble(SmithingRecipeInput input) {
		return new ItemStack(FurnaceTier.NETHERITE.item);
	}

	@Override
	public Optional<Ingredient> templateIngredient() {
		return Optional.of(template);
	}

	@Override
	public Ingredient baseIngredient() {
		return base;
	}

	@Override
	public Optional<Ingredient> additionIngredient() {
		return Optional.of(addition);
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
	public PlacementInfo placementInfo() {
		return PlacementInfo.NOT_PLACEABLE;
	}

	@Override
	public RecipeSerializer<NetheriteSmithingRecipe> getSerializer() {
		return SERIALIZER;
	}
}
