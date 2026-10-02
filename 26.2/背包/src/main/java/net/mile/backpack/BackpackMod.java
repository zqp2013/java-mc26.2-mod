package net.mile.backpack;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.mile.backpack.payload.OpenBackpackPayload;
import net.mile.backpack.payload.SetBackpackViewPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackpackMod implements ModInitializer {

	public static final String MOD_ID = "backpack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 已装备的背包(存在玩家数据附件里,死亡不清档、随 keepInventory 规则掉落) */
	public static final AttachmentType<ItemStack> EQUIPPED_BACKPACK = AttachmentRegistry.<ItemStack>builder()
			.persistent(ItemStack.OPTIONAL_CODEC)
			.copyOnDeath()
			.syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(id("equipped_backpack"));

	/** 已穿戴的合成终端(要求身上装备的是终端类背包;换下终端时自动脱下) */
	public static final AttachmentType<ItemStack> EQUIPPED_CRAFTING = AttachmentRegistry.<ItemStack>builder()
			.persistent(ItemStack.OPTIONAL_CODEC)
			.copyOnDeath()
			.syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.buildAndRegister(id("equipped_crafting"));

	/** 背包内容(挂在背包物品上,像潜影盒一样随物品走;数量用裸 int,不受 99 上限) */
	public static final DataComponentType<BackpackContents> BACKPACK_CONTENTS =
			Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("contents"),
					new DataComponentType.Builder<BackpackContents>()
							.persistent(BackpackContents.CODEC)
							.networkSynchronized(BackpackContents.STREAM_CODEC)
							.build());

	public static final Item NORMAL_BACKPACK = register("normal_backpack", BackpackType.NORMAL);
	public static final Item ADVANCED_BACKPACK = register("advanced_backpack", BackpackType.ADVANCED);
	public static final Item STORAGE_TERMINAL = register("storage_terminal", BackpackType.TERMINAL);
	public static final Item SUPER_STORAGE_TERMINAL = register("super_storage_terminal", BackpackType.SUPER);
	public static final Item CRAFTING_TERMINAL = register("crafting_terminal", BackpackType.CRAFTING);

	/** 合成终端 + 储存终端 / 超级储存终端 组合出的两种 3x3 菜单 */
	public static final MenuType<BackpackMenu> SUPER_MENU = registerMenu("super_storage_terminal", BackpackType.SUPER, false);
	public static final MenuType<BackpackMenu> CRAFTING_STORAGE_MENU = registerMenu("crafting_terminal_storage", BackpackType.TERMINAL, true);
	public static final MenuType<BackpackMenu> CRAFTING_SUPER_MENU = registerMenu("crafting_terminal_super", BackpackType.SUPER, true);

	private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "ingredients"));

	static {
		// 菜单类型要在物品之后建(工厂引用枚举回填的 item/menuType)
		BackpackType.NORMAL.menuType(registerMenu("normal_backpack", BackpackType.NORMAL, false));
		BackpackType.ADVANCED.menuType(registerMenu("advanced_backpack", BackpackType.ADVANCED, false));
		BackpackType.TERMINAL.menuType(registerMenu("storage_terminal", BackpackType.TERMINAL, false));
		BackpackType.SUPER.menuType(SUPER_MENU);
	}

	@Override
	public void onInitialize() {
		// 客户端按 E → 服务端开菜单(穿戴着合成终端且背包是终端 → 3x3 合成界面)
		PayloadTypeRegistry.serverboundPlay().register(OpenBackpackPayload.TYPE, OpenBackpackPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenBackpackPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			ItemStack equipped = player.getAttachedOrElse(EQUIPPED_BACKPACK, ItemStack.EMPTY);
			BackpackType type = BackpackType.fromItem(equipped.getItem());
			if (type == null) {
				return;
			}
			ItemStack crafting = player.getAttachedOrElse(EQUIPPED_CRAFTING, ItemStack.EMPTY);
			boolean useCrafting = type.isTerminal() && !crafting.isEmpty()
					&& BackpackType.fromItem(crafting.getItem()) == BackpackType.CRAFTING;
			MenuType<BackpackMenu> menuType = useCrafting
					? (type == BackpackType.SUPER ? CRAFTING_SUPER_MENU : CRAFTING_STORAGE_MENU)
					: type.menuType();
			player.openMenu(new SimpleMenuProvider(
					(id, inv, p) -> new BackpackMenu(menuType, id, inv, type, true, useCrafting),
					useCrafting ? crafting.getHoverName() : type.title));
			if (useCrafting && type == BackpackType.SUPER) {
				BackpackAdvancements.award(player, "storage_and_crafting");
			}
		});

		// 超级终端翻页/搜索:客户端视图同步到服务端(服务端按同一规则忽略不可见槽的点击)
		PayloadTypeRegistry.serverboundPlay().register(SetBackpackViewPayload.TYPE, SetBackpackViewPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SetBackpackViewPayload.TYPE, (payload, context) -> {
			if (context.player().containerMenu instanceof BackpackMenu menu && menu.type.paged()) {
				menu.applyView(payload.page(), payload.search());
			}
		});

		// 手持背包/合成终端右键 = 穿戴(旧装备回背包;合成终端要求身上有终端类背包)
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide()) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			BackpackType type = BackpackType.fromItem(held.getItem());
			if (type == null) {
				return InteractionResult.PASS;
			}
			if (type == BackpackType.CRAFTING) {
				equipCrafting(player, hand);
			} else {
				equip(player, hand);
			}
			return InteractionResult.SUCCESS;
		});

		// 死亡掉落背包和合成终端(内容在物品里,不丢);keepInventory 时随附件带到重生
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			boolean keepInventory = Boolean.TRUE.equals(player.level().getGameRules().get(GameRules.KEEP_INVENTORY));
			dropAttachmentIfPresent(player, EQUIPPED_CRAFTING, keepInventory);
			dropAttachmentIfPresent(player, EQUIPPED_BACKPACK, keepInventory);
		});

		// 创造栏
		CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS_TAB).register(output -> {
			output.accept(NORMAL_BACKPACK);
			output.accept(ADVANCED_BACKPACK);
			output.accept(STORAGE_TERMINAL);
			output.accept(SUPER_STORAGE_TERMINAL);
			output.accept(CRAFTING_TERMINAL);
		});

		LOGGER.info("[背包] zym制造 已加载");
	}

	private static void dropAttachmentIfPresent(ServerPlayer player, AttachmentType<ItemStack> attachment,
			boolean keepInventory) {
		ItemStack stack = player.getAttachedOrElse(attachment, ItemStack.EMPTY);
		if (stack.isEmpty()) {
			return;
		}
		if (!keepInventory) {
			player.drop(stack, false);
		}
		player.setAttached(attachment, ItemStack.EMPTY);
	}

	/** 身上的背包不是终端类时,把穿戴的合成终端脱回背包 */
	public static void unequipCraftingIfOrphaned(Player player) {
		ItemStack crafting = player.getAttachedOrElse(EQUIPPED_CRAFTING, ItemStack.EMPTY);
		if (crafting.isEmpty()) {
			return;
		}
		ItemStack equipped = player.getAttachedOrElse(EQUIPPED_BACKPACK, ItemStack.EMPTY);
		BackpackType type = BackpackType.fromItem(equipped.getItem());
		if (type == null || !type.isTerminal()) {
			player.setAttached(EQUIPPED_CRAFTING, ItemStack.EMPTY);
			player.getInventory().placeItemBackInInventory(crafting);
			player.sendSystemMessage(Component.literal("已自动脱下合成终端(身上没有储存终端类背包)")
					.withStyle(ChatFormatting.YELLOW));
		}
	}

	/** 把手上的一个背包装备到背部栏位,原来装备的放回玩家背包 */
	private static void equip(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			return;
		}
		ItemStack backpack = held.copy();
		backpack.setCount(1);
		held.shrink(1);
		player.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);

		ItemStack previous = player.getAttachedOrElse(EQUIPPED_BACKPACK, ItemStack.EMPTY);
		if (!previous.isEmpty()) {
			player.getInventory().placeItemBackInInventory(previous);
		}
		player.setAttached(EQUIPPED_BACKPACK, backpack);
		// 换上的不是终端类背包 → 合成终端失去支撑,自动脱下
		unequipCraftingIfOrphaned(player);
		player.sendOverlayMessage(Component.translatable(backpack.getItem().getDescriptionId())
				.append(Component.literal(" 已装备").withStyle(ChatFormatting.GREEN)));
	}

	/** 把手上的合成终端穿到第二穿戴位(要求身上装备的是储存终端/超级储存终端) */
	private static void equipCrafting(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			return;
		}
		ItemStack equipped = player.getAttachedOrElse(EQUIPPED_BACKPACK, ItemStack.EMPTY);
		BackpackType type = BackpackType.fromItem(equipped.getItem());
		if (type == null || !type.isTerminal()) {
			player.sendOverlayMessage(Component.literal("合成终端需要先装备储存终端或超级储存终端")
					.withStyle(ChatFormatting.RED));
			return;
		}
		ItemStack crafting = held.copy();
		crafting.setCount(1);
		held.shrink(1);
		player.setItemInHand(hand, held.isEmpty() ? ItemStack.EMPTY : held);

		ItemStack previous = player.getAttachedOrElse(EQUIPPED_CRAFTING, ItemStack.EMPTY);
		if (!previous.isEmpty()) {
			player.getInventory().placeItemBackInInventory(previous);
		}
		player.setAttached(EQUIPPED_CRAFTING, crafting);
		player.sendOverlayMessage(Component.translatable(crafting.getItem().getDescriptionId())
				.append(Component.literal(" 已穿戴,按 E 打开 3x3 合成").withStyle(ChatFormatting.GREEN)));
	}

	private static Item register(String path, BackpackType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		Item item = new BackpackItem(type, new Item.Properties().stacksTo(1).setId(key));
		return net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static MenuType<BackpackMenu> registerMenu(String path, BackpackType type, boolean craftingGrid) {
		MenuType<BackpackMenu>[] box = new MenuType[1];
		MenuType<BackpackMenu> menuType = new MenuType<>((menuId, inv) ->
				new BackpackMenu(box[0], menuId, inv, type, false, craftingGrid), FeatureFlags.VANILLA_SET);
		box[0] = menuType;
		return net.minecraft.core.Registry.register(BuiltInRegistries.MENU, id(path), menuType);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
