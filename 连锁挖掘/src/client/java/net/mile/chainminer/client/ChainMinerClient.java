package net.mile.chainminer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.client.gui.screens.Screen;
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

		// 开屏推迟到 tick 末尾执行:在鼠标右键回调里直接 setScreen 会被 fabric-screen-api
		// 的拖拽包装器撞上"未初始化"状态直接崩溃(2026-09-30 实测崩溃过);
		// tick 上下文开屏+滑条拖动是安全的(同小地图 MapScreen 的打开方式)
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingScreen != null) {
				Screen screen = pendingScreen;
				pendingScreen = null;
				if (client.player != null) {
					client.gui.setScreen(screen);
				}
			}
		});

		// 服务端同步来的当前设置
		ClientPlayNetworking.registerGlobalReceiver(SyncSettingsPayload.TYPE, (payload, context) -> {
			ChainMinerClientData.setMax(payload.max());
			ChainMinerClientData.setVacuumToPlayer(payload.vacuumToPlayer());
			ChainMinerClientData.setWarnWrongTier(payload.warnWrongTier());
		});

		// Ctrl+右键(对准方块):打开连锁挖掘设置界面
		// (按 Shift 时放行——Ctrl+Shift+右键留给高亮显示的设置界面,两个模组同手势会打架)
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand == InteractionHand.MAIN_HAND && isCtrlKeyDown() && !isShiftKeyDown()) {
				openConfigScreen();
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// Ctrl+右键(对准空气):同上
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand == InteractionHand.MAIN_HAND && isCtrlKeyDown() && !isShiftKeyDown()) {
				openConfigScreen();
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean isShiftKeyDown() {
		Window window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
				|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	private static boolean isCtrlKeyDown() {
		Window window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
				|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
	}

	/** 等下一 tick 再打开的界面(见 onInitializeClient 里的说明) */
	private static Screen pendingScreen;

	private static void openConfigScreen() {
		pendingScreen = new ChainMinerConfigScreen();
	}

	static void sendSettings(int max, boolean vacuumToPlayer, boolean warnWrongTier) {
		ClientPlayNetworking.send(new UpdateSettingsPayload(max, vacuumToPlayer, warnWrongTier));
	}

	static void sendUndo() {
		ClientPlayNetworking.send(UndoPayload.INSTANCE);
	}
}
