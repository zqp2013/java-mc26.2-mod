package net.mile.highlightdisplay.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
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

	private static void openConfigScreen() {
		// 注意:不能用 setScreenAndShow——它会立刻重渲染一帧,在游戏内输入处理中途重入渲染器会原生崩溃;
		// 游戏内换界面要走 Gui.setScreen(下一帧自然渲染)
		Minecraft.getInstance().gui.setScreen(new HighlightConfigScreen());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("highlightdisplay", path);
	}
}
