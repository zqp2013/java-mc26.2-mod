package net.mile.highlightdisplay.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 高亮显示设置界面:勾选注视目标面板上要显示哪些信息。 */
public class HighlightConfigScreen extends Screen {
	private static final int WHITE = 0xFFFFFF;
	private static final int GRAY = 0xA8A8A8;

	private boolean showId;
	private boolean showState;
	private boolean showInfo;
	private boolean showTier;
	private boolean showProgress;

	public HighlightConfigScreen() {
		super(Component.literal("高亮显示设置"));
		HighlightConfig.load();
		HighlightConfig.Toggles toggles = HighlightConfig.current();
		this.showId = toggles.id();
		this.showState = toggles.state();
		this.showInfo = toggles.info();
		this.showTier = toggles.tier();
		this.showProgress = toggles.progress();
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		addRenderableWidget(Button.builder(idLabel(), button -> {
			this.showId = !this.showId;
			button.setMessage(idLabel());
		}).bounds(centerX - 100, 40, 200, 20).build());
		addRenderableWidget(Button.builder(stateLabel(), button -> {
			this.showState = !this.showState;
			button.setMessage(stateLabel());
		}).bounds(centerX - 100, 68, 200, 20).build());
		addRenderableWidget(Button.builder(infoLabel(), button -> {
			this.showInfo = !this.showInfo;
			button.setMessage(infoLabel());
		}).bounds(centerX - 100, 96, 200, 20).build());
		addRenderableWidget(Button.builder(tierLabel(), button -> {
			this.showTier = !this.showTier;
			button.setMessage(tierLabel());
		}).bounds(centerX - 100, 124, 200, 20).build());
		addRenderableWidget(Button.builder(progressLabel(), button -> {
			this.showProgress = !this.showProgress;
			button.setMessage(progressLabel());
		}).bounds(centerX - 100, 152, 200, 20).build());
		addRenderableWidget(Button.builder(Component.literal("完成"), button -> this.onClose())
				.bounds(centerX - 100, 180, 200, 20).build());
	}

	private Component idLabel() {
		return Component.literal("显示方块ID:" + (this.showId ? "开" : "关"));
	}

	private Component stateLabel() {
		return Component.literal("显示方块状态:" + (this.showState ? "开" : "关"));
	}

	private Component infoLabel() {
		return Component.literal("显示坐标/红石等信息:" + (this.showInfo ? "开" : "关"));
	}

	private Component tierLabel() {
		return Component.literal("显示挖掘等级:" + (this.showTier ? "开" : "关"));
	}

	private Component progressLabel() {
		return Component.literal("显示挖掘进度条:" + (this.showProgress ? "开" : "关"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int centerX = this.width / 2;
		graphics.centeredText(this.font, this.getTitle(), centerX, 22, WHITE);
		graphics.centeredText(this.font, "Ctrl+右键打开本界面", centerX, 206, GRAY);
	}

	@Override
	public void onClose() {
		HighlightConfig.apply(new HighlightConfig.Toggles(showId, showState, showInfo, showTier, showProgress));
		super.onClose();
	}
}
