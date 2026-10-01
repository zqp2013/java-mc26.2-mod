package net.mile.superfurnace;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 熔炉等级:烧一个物品的秒数 = cookTicks/20。
 * 铜熔炉 8s → 铁熔炉 5s → 金熔炉 5s(槽多) → 钻石熔炉 4s → 下界合金熔炉 3.5s(槽最多)。
 * 多个输入槽放不同物品;熔炉等级 L = 同时烧 L 个物品(轮流从各输入槽取料),
 * 燃料也按 L 倍速消耗。金/钻/下界合金熔炉可升级,用 8 个升级材料围着 1 个熔炉合成。
 */
public enum FurnaceTier {
	COPPER("copper_furnace", 1, 1, 1, 160, 1, null),
	IRON("iron_furnace", 1, 1, 1, 100, 1, null),
	GOLD("gold_furnace", 2, 4, 5, 100, 4, Items.GOLD_INGOT),
	DIAMOND("diamond_furnace", 3, 5, 8, 80, 5, Items.DIAMOND),
	NETHERITE("netherite_furnace", 5, 10, 15, 70, 10, Items.NETHERITE_INGOT);

	public final String name;
	/** 燃料槽数 */
	public final int fuelSlots;
	/** 输入槽数(并行烧炼) */
	public final int inputSlots;
	/** 成品槽数 */
	public final int outputSlots;
	/** 烧一个物品需要的刻 */
	public final int cookTicks;
	/** 可升到的最高等级(等级 L = 每槽一次烧 L 个) */
	public final int maxLevel;
	/** 升级材料(8个+熔炉合成升级);null = 不可升级 */
	public final Item upgradeMaterial;
	public final int totalSlots;

	// 由注册流程回填
	public SuperFurnaceBlock block;
	public Item item;
	public BlockEntityType<SuperFurnaceBlockEntity> blockEntityType;
	public MenuType<SuperFurnaceMenu> menuType;

	FurnaceTier(String name, int fuelSlots, int inputSlots, int outputSlots, int cookTicks,
			int maxLevel, Item upgradeMaterial) {
		this.name = name;
		this.fuelSlots = fuelSlots;
		this.inputSlots = inputSlots;
		this.outputSlots = outputSlots;
		this.cookTicks = cookTicks;
		this.maxLevel = maxLevel;
		this.upgradeMaterial = upgradeMaterial;
		this.totalSlots = fuelSlots + inputSlots + outputSlots;
	}

	/** 容器里燃料槽的起始下标 */
	public int fuelBase() {
		return 0;
	}

	/** 容器里输入槽的起始下标 */
	public int inputBase() {
		return fuelSlots;
	}

	/** 容器里成品槽的起始下标 */
	public int outputBase() {
		return fuelSlots + inputSlots;
	}
}
