package net.mile.backpack.mixin;

import net.mile.backpack.client.TerminalOverlay;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 箱子/熔炉等所有容器界面:右侧渲染终端面板,直接存取身上的储存终端。
 * 渲染挂在 extractContents 尾部(此时 translate 矩阵已 pop,用绝对屏幕坐标);
 * 输入在头部拦截,面板区域自己消费(与原版槽位重叠时让原版优先)。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

	@Shadow
	protected int leftPos;
	@Shadow
	protected int topPos;
	@Shadow
	protected int imageWidth;

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void backpack$renderTerminalPanel(GuiGraphicsExtractor gui, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
		// font 不能 @Shadow(不在本类,26.2 实测开局崩);26.3 里 Screen.font 还是 protected,
		// 界面渲染时拿 Minecraft 全局的 font 就是同一个实例
		TerminalOverlay.render(self, gui, net.minecraft.client.Minecraft.getInstance().font,
				this.leftPos, this.topPos, this.imageWidth, self.width, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void backpack$terminalPanelClick(MouseButtonEvent event, boolean doubled,
			CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
		if (TerminalOverlay.handleClick(self, event, this.leftPos, this.topPos, this.imageWidth, self.width)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void backpack$terminalPanelKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (TerminalOverlay.keyPressed(event)) {
			cir.setReturnValue(true);
		}
	}

	// 26.3: AbstractContainerScreen 不再声明 charTyped(只剩 GuiEventListener 的 default 实现),
	// 没有可 @Inject 的目标 —— 直接在 mixin 里覆写;默认实现就是返回 false(未消费),语义一致
	public boolean charTyped(CharacterEvent event) {
		return TerminalOverlay.charTyped(event);
	}
}
