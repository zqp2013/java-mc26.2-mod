package net.mile.chainminer.client;

import net.mile.chainminer.ChainMinerConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 连锁挖掘设置界面:滑条调整每次最多连锁数,开关控制掉落物是否自动掉到脚下。 */
public class ChainMinerConfigScreen extends Screen {
	private static final int WHITE = 0xFFFFFF;
	private static final int GRAY = 0xA8A8A8;

	private ConfigSlider slider;
	private boolean vacuum;

	public ChainMinerConfigScreen() {
		super(Component.literal("连锁挖掘设置"));
		this.vacuum = ChainMinerClientData.isVacuumToPlayer();
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		this.slider = new ConfigSlider(centerX - 100, 40, 200, 20, ChainMinerClientData.getMax());
		addRenderableWidget(this.slider);
		addRenderableWidget(Button.builder(vacuumLabel(), button -> {
			this.vacuum = !this.vacuum;
			button.setMessage(vacuumLabel());
		}).bounds(centerX - 100, 68, 200, 20).build());
		addRenderableWidget(Button.builder(Component.literal("撤销上次连锁"), button -> {
			ChainMinerClient.sendUndo();
			this.onClose();
		}).bounds(centerX - 100, 96, 200, 20).build());
		addRenderableWidget(Button.builder(Component.literal("完成"), button -> this.onClose())
				.bounds(centerX - 100, 124, 200, 20).build());
	}

	private Component vacuumLabel() {
		return Component.literal("掉落物自动掉到脚下:" + (this.vacuum ? "开" : "关"));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int centerX = this.width / 2;
		graphics.centeredText(this.font, this.getTitle(), centerX, 22, WHITE);
		graphics.centeredText(this.font, "潜行挖掘时连锁破坏同类方块", centerX, 150, GRAY);
		graphics.centeredText(this.font, "Ctrl+右键打开本界面", centerX, 162, GRAY);
		graphics.centeredText(this.font, "连锁会为工具保留至少 1 点耐久,撤销可恢复方块和掉落", centerX, 174, GRAY);
	}

	@Override
	public void onClose() {
		int value = this.slider != null ? this.slider.currentValue() : ChainMinerClientData.getMax();
		ChainMinerClientData.setMax(value);
		ChainMinerClientData.setVacuumToPlayer(this.vacuum);
		ChainMinerClient.sendSettings(value, this.vacuum);
		super.onClose();
	}

	/** 滑条:1 ~ 上限 线性映射。 */
	private static class ConfigSlider extends AbstractSliderButton {
		private int current;

		ConfigSlider(int x, int y, int width, int height, int initialValue) {
			super(x, y, width, height, Component.empty(), toSliderValue(initialValue));
			this.current = initialValue;
			updateMessage();
		}

		private static double toSliderValue(int value) {
			return (double) (value - ChainMinerConfig.MIN) / (ChainMinerConfig.MAX - ChainMinerConfig.MIN);
		}

		private static int fromSliderValue(double slider) {
			int value = ChainMinerConfig.MIN
					+ (int) Math.round(slider * (ChainMinerConfig.MAX - ChainMinerConfig.MIN));
			return Math.max(ChainMinerConfig.MIN, Math.min(ChainMinerConfig.MAX, value));
		}

		int currentValue() {
			return this.current;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal("每次最多连锁:" + this.current + " 个方块"));
		}

		@Override
		protected void applyValue() {
			this.current = fromSliderValue(this.value);
		}
	}
}
