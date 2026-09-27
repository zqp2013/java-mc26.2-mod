package net.mile.chainminer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.mile.chainminer.payload.SyncSettingsPayload;
import net.mile.chainminer.payload.UndoPayload;
import net.mile.chainminer.payload.UpdateSettingsPayload;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import org.lwjgl.glfw.GLFW;

public class ChainMinerClient implements ClientModInitializer {
	/** 进世界时给玩家的署名提示 */
	private static final Component GREETING = Component.literal("[连锁挖掘] zym制造")
			.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)));

	@Override
	public void onInitializeClient() {
		// 进世界时提示 zym制造
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.player != null) {
				client.player.sendSystemMessage(GREETING);
			}
		});

		// 服务端同步来的当前设置
		ClientPlayNetworking.registerGlobalReceiver(SyncSettingsPayload.TYPE, (payload, context) -> {
			ChainMinerClientData.setMax(payload.max());
			ChainMinerClientData.setVacuumToPlayer(payload.vacuumToPlayer());
		});

		// Ctrl+右键(对准方块):打开连锁挖掘设置界面
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand == InteractionHand.MAIN_HAND && isCtrlKeyDown()) {
				openConfigScreen();
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// Ctrl+右键(对准空气):同上
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand == InteractionHand.MAIN_HAND && isCtrlKeyDown()) {
				openConfigScreen();
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean isCtrlKeyDown() {
		Window window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
				|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
	}

	private static void openConfigScreen() {
		// 注意:不能用 setScreenAndShow——它会立刻重渲染一帧,在游戏内输入处理中途重入渲染器会原生崩溃;
		// 游戏内换界面要走 Gui.setScreen(下一帧自然渲染)
		Minecraft.getInstance().gui.setScreen(new ChainMinerConfigScreen());
	}

	static void sendSettings(int max, boolean vacuumToPlayer) {
		ClientPlayNetworking.send(new UpdateSettingsPayload(max, vacuumToPlayer));
	}

	static void sendUndo() {
		ClientPlayNetworking.send(UndoPayload.INSTANCE);
	}
}
