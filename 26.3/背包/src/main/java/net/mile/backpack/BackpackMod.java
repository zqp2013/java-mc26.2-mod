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

	private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("minecraft", "ingredients"));

	static {
		// 菜单类型要在物品之后建(工厂引用枚举回填的 item/menuType)
		BackpackType.NORMAL.menuType(registerMenu("normal_backpack", BackpackType.NORMAL));
		BackpackType.ADVANCED.menuType(registerMenu("advanced_backpack", BackpackType.ADVANCED));
		BackpackType.TERMINAL.menuType(registerMenu("storage_terminal", BackpackType.TERMINAL));
	}

	@Override
	public void onInitialize() {
		// 客户端按 E → 服务端开菜单
		PayloadTypeRegistry.serverboundPlay().register(OpenBackpackPayload.TYPE, OpenBackpackPayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenBackpackPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			ItemStack equipped = player.getAttachedOrElse(EQUIPPED_BACKPACK, ItemStack.EMPTY);
			BackpackType type = BackpackType.fromItem(equipped.getItem());
			if (type == null) {
				return;
			}
			player.openMenu(new SimpleMenuProvider(
					(id, inv, p) -> new BackpackMenu(type.menuType(), id, inv, type, true),
					type.title));
		});

		// 手持背包右键 = 装备到背部栏位(旧背包回背包)
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide()) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (BackpackType.fromItem(held.getItem()) == null) {
				return InteractionResult.PASS;
			}
			equip(player, hand);
			return InteractionResult.SUCCESS;
		});

		// 死亡掉落背包(内容在物品里,不丢);keepInventory 时随附件带到重生
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			ItemStack equipped = player.getAttachedOrElse(EQUIPPED_BACKPACK, ItemStack.EMPTY);
			if (equipped.isEmpty()) {
				return;
			}
			if (!Boolean.TRUE.equals(player.level().getGameRules().get(GameRules.KEEP_INVENTORY))) {
				player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), equipped);
			}
			player.setAttached(EQUIPPED_BACKPACK, ItemStack.EMPTY);
		});

		// 创造栏
		CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS_TAB).register(output -> {
			output.accept(NORMAL_BACKPACK);
			output.accept(ADVANCED_BACKPACK);
			output.accept(STORAGE_TERMINAL);
		});

		LOGGER.info("[背包] zym制造 已加载");
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
			player.getInventory().placeItemBackInInventory(previous, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.setAttached(EQUIPPED_BACKPACK, backpack);
		player.sendOverlayMessage(Component.translatable(backpack.getItem().getDescriptionId())
				.append(Component.literal(" 已装备").withStyle(ChatFormatting.GREEN)));
	}

	private static Item register(String path, BackpackType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		Item item = new BackpackItem(type, new Item.Properties().stacksTo(1).setId(key));
		return net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	private static MenuType<BackpackMenu> registerMenu(String path, BackpackType type) {
		MenuType<BackpackMenu>[] box = new MenuType[1];
		MenuType<BackpackMenu> menuType = new MenuType<>((menuId, inv) ->
				new BackpackMenu(box[0], menuId, inv, type, false), FeatureFlags.VANILLA_SET);
		box[0] = menuType;
		return net.minecraft.core.Registry.register(BuiltInRegistries.MENU, id(path), menuType);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
