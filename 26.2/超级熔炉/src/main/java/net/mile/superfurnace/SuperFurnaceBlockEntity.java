package net.mile.superfurnace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;

/**
 * 超级熔炉方块实体:燃料池共享(burnTime 一个池子),等级 L = 同时烧 L 个物品,
 * "炉口"轮流分配到各个有料可烧的输入槽上。
 * 全批共用一条烧炼进度,一批完成时一起出成品。
 * 燃料:单个物品的烧炼成本恒为 200 刻(原版 BURN_TIME_STANDARD),
 * 炉子再快、同时烧得再多,一根木头也只烧 1.5 个、一个煤只烧 8 个。
 */
public class SuperFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	private final FurnaceTier tier;
	private NonNullList<ItemStack> items;
	/** 各面漏斗能访问的槽位(原版熔炉语义):上面=输入槽、侧面=燃料槽、下面=成品槽(+燃料槽给空桶) */
	private final int[] slotsForUp;
	private final int[] slotsForDown;
	private final int[] slotsForSides;
	/** 剩余燃烧时间(刻,按并行物品数倍速消耗) */
	private int burnTime;
	/** 当前这份燃料的总燃烧时间(用于火焰比例显示) */
	private int burnDuration;
	/** 本批(同时烧的几个物品)的共用烧炼进度(刻) */
	private int progress;
	/** 熔炉等级:同时烧 level 个物品,燃料也按倍数消耗 */
	private int furnaceLevel;
	/** 当前实际同时烧的个数(料不够等级时小于等级,同步给界面显示) */
	private int burningCount;
	/** 燃料小数消耗的余数累加器(每刻应耗 burning*200/cookTicks,整数化后漏的部分记在这里) */
	private int fuelRemainder;

	private final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> burnTime;
				case 1 -> burnDuration;
				case 2 -> furnaceLevel;
				case 3 -> progress;
				default -> burningCount;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> burnTime = value;
				case 1 -> burnDuration = value;
				case 2 -> furnaceLevel = value;
				case 3 -> progress = value;
				default -> burningCount = value;
			}
		}

		@Override
		public int getCount() {
			return 5;
		}
	};

	public SuperFurnaceBlockEntity(FurnaceTier tier, BlockPos pos, BlockState state) {
		super(tier.blockEntityType, pos, state);
		this.tier = tier;
		this.items = NonNullList.withSize(tier.totalSlots, ItemStack.EMPTY);
		this.furnaceLevel = 1;
		this.slotsForUp = slotRange(tier.inputBase(), tier.inputSlots);
		this.slotsForSides = slotRange(tier.fuelBase(), tier.fuelSlots);
		// 下面除了成品槽,还带上燃料槽:岩浆桶烧完留下的空桶能被下面的漏斗抽走(原版行为)
		int[] outputs = slotRange(tier.outputBase(), tier.outputSlots);
		this.slotsForDown = java.util.stream.IntStream.concat(
				java.util.stream.IntStream.of(outputs),
				java.util.stream.IntStream.of(slotsForSides)).toArray();
	}

	public FurnaceTier tier() {
		return tier;
	}

	public int getFurnaceLevel() {
		return furnaceLevel;
	}

	public void setFurnaceLevel(int level) {
		this.furnaceLevel = net.minecraft.util.Mth.clamp(level, 1, tier.maxLevel);
		setChanged();
	}

	// ---- Container 实现 ----

	@Override
	public int getContainerSize() {
		return tier.totalSlots;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public ItemStack getItem(int index) {
		return items.get(index);
	}

	@Override
	public ItemStack removeItem(int index, int count) {
		return net.minecraft.world.ContainerHelper.removeItem(items, index, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int index) {
		return net.minecraft.world.ContainerHelper.takeItem(items, index);
	}

	@Override
	public void setItem(int index, ItemStack stack) {
		items.set(index, stack);
		stack.limitSize(getMaxStackSize());
		setChanged();
	}

	@Override
	public void clearContent() {
		items.clear();
	}

	// ---- WorldlyContainer:漏斗/发射器自动交互 ----

	@Override
	public int[] getSlotsForFace(Direction side) {
		if (side == Direction.DOWN) {
			return slotsForDown;
		}
		return side == Direction.UP ? slotsForUp : slotsForSides;
	}

	/** 漏斗塞东西:上面进输入槽(随便什么),侧面进燃料槽(只收燃料),下面不许塞(成品槽拒收) */
	@Override
	public boolean canPlaceItem(int index, ItemStack stack) {
		if (index >= tier.outputBase()) {
			return false;
		}
		if (index < tier.inputBase()) {
			return this.level != null && this.level.fuelValues().isFuel(stack);
		}
		return true;
	}

	@Override
	public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) {
		return canPlaceItem(index, stack);
	}

	/** 漏斗吸东西:只有成品(以及燃料槽里岩浆桶留下的空桶)能被吸走,原料和燃料都吸不走 */
	@Override
	public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) {
		if (index >= tier.outputBase()) {
			return true;
		}
		// 燃料槽只放行空桶(岩浆桶烧完的剩余物),煤、木板、水桶这些统统不许漏斗碰
		return index < tier.inputBase() && stack.is(Items.BUCKET);
	}

	/** [start, start+count) 的槽位下标数组 */
	private static int[] slotRange(int start, int count) {
		int[] slots = new int[count];
		for (int i = 0; i < count; i++) {
			slots[i] = start + i;
		}
		return slots;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable(Identifier.fromNamespaceAndPath(SuperFurnaceMod.MOD_ID, tier.name)
				.toLanguageKey("block"));
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		// 打开界面时按炉子等级发进度
		if (inventory.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			switch (tier) {
				case GOLD -> Advancements.grant(serverPlayer, "gold_open");
				case DIAMOND -> {
					if (furnaceLevel >= 5) {
						Advancements.grant(serverPlayer, "diamond_lv5");
					}
				}
				case NETHERITE -> {
					if (furnaceLevel >= 10) {
						Advancements.grant(serverPlayer, "netherite_lv10");
					}
				}
				default -> {
				}
			}
		}
		return new SuperFurnaceMenu(tier.menuType, id, inventory, this, dataAccess, tier,
				net.minecraft.world.inventory.ContainerLevelAccess.create(this.getLevel(), this.worldPosition));
	}

	// ---- 存档 ----

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		net.minecraft.world.ContainerHelper.saveAllItems(output, items);
		output.putInt("BurnTime", burnTime);
		output.putInt("BurnDuration", burnDuration);
		output.putInt("FurnaceLevel", furnaceLevel);
		output.putInt("Progress", progress);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		net.minecraft.world.ContainerHelper.loadAllItems(input, items);
		burnTime = input.getIntOr("BurnTime", 0);
		burnDuration = input.getIntOr("BurnDuration", 0);
		furnaceLevel = net.minecraft.util.Mth.clamp(input.getIntOr("FurnaceLevel", 1), 1, tier.maxLevel);
		progress = input.getIntOr("Progress", 0);
	}

	// ---- 服务端烧炼逻辑 ----

	/** 服务端烧炼 tick(ticker 首参是 Level,进方法再转 ServerLevel) */
	public static void serverTick(Level level, BlockPos pos, BlockState state,
			SuperFurnaceBlockEntity furnace) {
		ServerLevel serverLevel = (ServerLevel) level;
		FurnaceTier tier = furnace.tier;
		FuelValues fuels = level.fuelValues();
		boolean changed = false;

		// 1. 查每个非空输入槽的配方和成品
		@SuppressWarnings("unchecked")
		Optional<RecipeHolder<SmeltingRecipe>>[] recipes = new Optional[tier.inputSlots];
		ItemStack[] results = new ItemStack[tier.inputSlots];
		for (int i = 0; i < tier.inputSlots; i++) {
			ItemStack input = furnace.items.get(tier.inputBase() + i);
			if (input.isEmpty()) {
				recipes[i] = Optional.empty();
			} else {
				recipes[i] = findRecipe(serverLevel, input);
				results[i] = recipes[i].map(r -> r.value().assemble(new SingleRecipeInput(input))).orElse(null);
			}
		}

		// 2. 分配"炉口":等级 L = 同时烧 L 个,轮流从各输入槽取
		//    (4 个口 4 种料就一样烧一个;只剩一种料就一槽占多个口;
		//    料不够等级时有多少烧多少,比如 10 级炉里只剩 8 个就同时烧 8 个)
		int[] take = new int[tier.inputSlots];
		int burning = 0;
		while (burning < furnace.furnaceLevel) {
			boolean added = false;
			for (int i = 0; i < tier.inputSlots && burning < furnace.furnaceLevel; i++) {
				ItemStack input = furnace.items.get(tier.inputBase() + i);
				if (results[i] == null || take[i] >= input.getCount()) {
					continue;
				}
				// 多个槽烧同一种成品时,剩余容量要合并计算,防止超发丢物品
				int claimed = 0;
				for (int j = 0; j < tier.inputSlots; j++) {
					if (take[j] > 0 && results[j] != null
							&& ItemStack.isSameItemSameComponents(results[j], results[i])) {
						claimed += take[j];
					}
				}
				if (!furnace.hasOutputRoom(results[i], claimed + 1)) {
					continue;
				}
				take[i]++;
				burning++;
				added = true;
			}
			if (!added) {
				break;
			}
		}
		furnace.burningCount = burning;

		// 3. 没燃料在烧且现在需要烧 → 从燃料槽吃一个燃料
		if (burning > 0 && furnace.burnTime <= 0) {
			for (int f = 0; f < tier.fuelSlots; f++) {
				ItemStack fuel = furnace.items.get(tier.fuelBase() + f);
				if (!fuel.isEmpty() && fuels.isFuel(fuel)) {
					furnace.burnDuration = fuels.burnDuration(fuel);
					furnace.burnTime = furnace.burnDuration;
					// 26.2: 剩余物 API 在 Item 上,返回模板;没有剩余物(如煤)返回 null!
					ItemStackTemplate remainder = fuel.getItem().getCraftingRemainder();
					if (remainder != null && remainder.count() > 0) {
						furnace.items.set(tier.fuelBase() + f, remainder.create());
					} else {
						fuel.shrink(1);
						if (fuel.isEmpty()) {
							furnace.items.set(tier.fuelBase() + f, ItemStack.EMPTY);
						}
					}
					changed = true;
					break;
				}
			}
		}

		// 4. 推进烧炼(全批共用一条进度,一批同时完成)
		boolean hasFuel = furnace.burnTime > 0;
		if (hasFuel) {
			// 单物品烧炼成本恒为 200 刻(原版标准):同时烧 N 个 → 每刻耗 N*200/cookTicks。
			// 整数除法会漏耗(如铜炉 200/160=1.25),用余数累加器补齐,平均速率才精确
			int drain;
			if (burning > 0) {
				furnace.fuelRemainder += burning * 200;
				drain = furnace.fuelRemainder / tier.cookTicks;
				furnace.fuelRemainder %= tier.cookTicks;
			} else {
				drain = 1; // 空烧也照原版 1/刻 慢慢耗
			}
			furnace.burnTime = Math.max(0, furnace.burnTime - drain);
			if (burning > 0) {
				furnace.progress++;
				if (furnace.progress >= tier.cookTicks) {
					furnace.progress = 0;
					// 一批完成:每个参与槽位按分配数消耗并产出
					int smelted = 0;
					for (int i = 0; i < tier.inputSlots; i++) {
						if (take[i] <= 0) {
							continue;
						}
						ItemStack input = furnace.items.get(tier.inputBase() + i);
						input.shrink(take[i]);
						if (input.isEmpty()) {
							furnace.items.set(tier.inputBase() + i, ItemStack.EMPTY);
						}
						for (int n = 0; n < take[i]; n++) {
							furnace.pushResult(results[i]);
						}
						smelted += take[i];
					}
					// 铜熔炉烧出第一批成品 → "快速熔炉"
					if (tier == FurnaceTier.COPPER && smelted > 0) {
						for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
							if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 8.0 * 8.0) {
								Advancements.grant(player, "copper_smelt");
							}
						}
					}
				}
				changed = true;
			} else if (furnace.progress > 0) {
				furnace.progress = Math.max(0, furnace.progress - 2);
				changed = true;
			}
		} else {
			furnace.burnDuration = 0;
			if (furnace.progress > 0) {
				furnace.progress = Math.max(0, furnace.progress - 2);
				changed = true;
			}
		}

		if (changed) {
			furnace.setChanged();
		}

		// 4. 点火状态同步给方块(换亮贴图)
		boolean lit = furnace.burnTime > 0;
		if (lit != state.getValue(SuperFurnaceBlock.LIT)) {
			level.setBlockAndUpdate(pos, state.setValue(SuperFurnaceBlock.LIT, lit));
		}
	}

	private static Optional<RecipeHolder<SmeltingRecipe>> findRecipe(ServerLevel level, ItemStack input) {
		return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level);
	}

	/** 成品槽还能装下 times 份这种成品吗(同种可叠/空槽都算容量) */
	private boolean hasOutputRoom(ItemStack result, int times) {
		int free = 0;
		for (int o = 0; o < tier.outputSlots; o++) {
			ItemStack out = items.get(tier.outputBase() + o);
			if (out.isEmpty()) {
				free += result.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(out, result)) {
				free += out.getMaxStackSize() - out.getCount();
			}
			if (free >= times) {
				return true;
			}
		}
		return false;
	}

	/** 把成品放进第一个能放的成品槽(调用前已用 hasOutputRoom 确认) */
	private void pushResult(ItemStack result) {
		for (int o = 0; o < tier.outputSlots; o++) {
			int index = tier.outputBase() + o;
			ItemStack out = items.get(index);
			if (out.isEmpty()) {
				items.set(index, result.copy());
				return;
			}
			if (ItemStack.isSameItemSameComponents(out, result)
					&& out.getCount() + result.getCount() <= out.getMaxStackSize()) {
				out.grow(result.getCount());
				return;
			}
		}
	}
}
