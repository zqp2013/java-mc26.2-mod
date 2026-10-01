package net.mile.backpack.mixin;

import java.util.Locale;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 储存终端的超大堆叠数量缩写:
 * >=1000 显示 1.2k,>=100 万显示 3.4m,>=10 亿显示 5.6b(保留一位小数)。
 * 正常玩法里只有终端能出现 >=1000 的单格数量,其它界面完全不受影响。
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

	@Inject(method = "itemCount", at = @At("HEAD"), cancellable = true)
	private void backpack$abbreviateBigCounts(Font font, ItemStack stack, int x, int y, String textOverride,
			CallbackInfo ci) {
		if (textOverride != null || stack.getCount() < 1000) {
			return;
		}
		String text = abbreviate(stack.getCount());
		// 完全照抄原版 itemCount 的画法:右下角白字带阴影
		((GuiGraphicsExtractor) (Object) this).text(font, text,
				x + 19 - 2 - font.width(text), y + 6 + 3, -1, true);
		ci.cancel();
	}

	private static String abbreviate(int count) {
		double value;
		String unit;
		if (count >= 1_000_000_000) {
			value = count / 1_000_000_000.0;
			unit = "b";
		} else if (count >= 1_000_000) {
			value = count / 1_000_000.0;
			unit = "m";
		} else {
			value = count / 1_000.0;
			unit = "k";
		}
		// 999.95k 之类四舍五入会变 1000.0k,进位到下一档好看点
		if (value >= 999.95 && !"b".equals(unit)) {
			value /= 1000.0;
			unit = "k".equals(unit) ? "m" : "b";
		}
		return String.format(Locale.ROOT, "%.1f%s", value, unit);
	}
}
