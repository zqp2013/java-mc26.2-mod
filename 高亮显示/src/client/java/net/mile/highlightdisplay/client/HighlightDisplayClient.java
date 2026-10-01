package net.mile.highlightdisplay.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import org.lwjgl.glfw.GLFW;

public class HighlightDisplayClient implements ClientModInitializer {
	/** 进世界时给玩家的署名提示 */
	private static final Component GREETING = Component.literal("[高亮显示] zym制造")
			.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)));

	@Override
	public void onInitializeClient() {
		// 读取上次保存的显示开关
		HighlightConfig.load();

		// 挂在原版 HUD 之后:注视目标信息面板(屏幕顶部中间)
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.MISC_OVERLAYS,
				id("target_info_panel"),
				new TargetInfoPanel());

		// Ctrl+Shift+右键(对准方块):打开设置界面
		// (纯 Ctrl+右键被连锁挖掘的设置界面占用——它按加载顺序排在前面会先吃掉事件,这里加 Shift 区分)
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (hand == InteractionHand.MAIN_HAND && isConfigGesture()) {
				openConfigScreen();
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// Ctrl+Shift+右键(对准空气):同上
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (hand == InteractionHand.MAIN_HAND && isConfigGesture()) {
				openConfigScreen();
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		// 进世界时提示 zym制造
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (client.player != null) {
				client.player.sendSystemMessage(GREETING);
			}
		});

		// 开屏推迟到 tick 末尾执行:在鼠标右键回调里直接 setScreen 会被 fabric-screen-api
		// 的拖拽包装器撞上"未初始化"状态直接崩溃(连锁挖掘 2026-09-30 实测崩溃过);
		// tick 上下文开屏是安全的(同小地图 MapScreen 的打开方式)
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingScreen != null) {
				Screen screen = pendingScreen;
				pendingScreen = null;
				if (client.player != null) {
					client.gui.setScreen(screen);
				}
			}
		});
	}

	/** 设置手势 = Ctrl+Shift 同按(纯 Ctrl+右键留给连锁挖掘) */
	private static boolean isConfigGesture() {
		return isCtrlKeyDown() && isShiftKeyDown();
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
		pendingScreen = new HighlightConfigScreen();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("highlightdisplay", path);
	}
}
