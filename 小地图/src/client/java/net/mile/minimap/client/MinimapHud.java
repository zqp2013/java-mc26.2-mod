package net.mile.minimap.client;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 右上角小地图 HUD:128×128 像素,每像素 0.5 格(共显示 64×64 格),北朝上,玩家箭头随视角旋转 */
public final class MinimapHud {
	private static final int SIZE = MinimapTextures.MINIMAP_SIZE;
	/** 每格占多少像素 */
	private static final float PX_PER_BLOCK = 2.0F;

	private static boolean needsRebuild = true;
	private static int tickCounter;

	private MinimapHud() {
	}

	/** 每 tick 由入口调用:判断是否需要重绘地形 */
	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			needsRebuild = false;
			return;
		}
		tickCounter++;
		// 每秒兜底刷一次(方块被挖/放的动态);位置变化由渲染侧对比触发
		if (tickCounter % 20 == 0) {
			needsRebuild = true;
		}
	}

	public static void reset() {
		needsRebuild = true;
		lastX = Double.NaN;
		lastZ = Double.NaN;
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		// 有界面(背包/聊天等)时不显示
		if (mc.gui.screen() != null) {
			return;
		}

		Vec3 pos = player.getPosition(delta.getGameTimeDeltaPartialTick(true));
		if (needsRebuild || moved(pos)) {
			rebuild(mc, pos);
		}

		Font font = mc.font;
		int x = mc.getWindow().getGuiScaledWidth() - SIZE - 4;
		int y = 4;

		// 黑色描边底
		g.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xCC000000);
		g.blit(RenderPipelines.GUI_TEXTURED, MinimapTextures.MINIMAP_ID, x, y, 0.0F, 0.0F, SIZE, SIZE, SIZE, SIZE);

		int cx = x + SIZE / 2;
		int cy = y + SIZE / 2;
		String dim = mc.level.dimension().identifier().toString();

		// 死亡点(红 X)
		if (MinimapStore.deathPoint != null && MinimapStore.deathPoint.dimension().equals(dim)) {
			int[] p = clampOffset(MinimapStore.deathPoint.x() - pos.x, MinimapStore.deathPoint.z() - pos.z);
			if (p != null) {
				g.text(font, "✖", cx + p[0] - 4, cy + p[1] - 5, MinimapStore.DEATH_COLOR, true);
			}
		}
		// 标签(彩点)
		for (Waypoint wp : MinimapStore.waypoints) {
			if (!wp.dimension().equals(dim)) {
				continue;
			}
			int[] p = clampOffset(wp.x() + 0.5 - pos.x, wp.z() + 0.5 - pos.z);
			if (p == null) {
				continue;
			}
			int dx = cx + p[0];
			int dy = cy + p[1];
			g.fill(dx - 2, dy - 2, dx + 2, dy + 2, 0xFF000000);
			g.fill(dx - 1, dy - 1, dx + 1, dy + 1, wp.color());
		}

		// 玩家箭头:贴图箭头朝上,旋转角 = atan2(dx, -dz)(屏幕坐标顺时针为正)
		float yaw = player.getYRot(delta.getGameTimeDeltaPartialTick(true));
		float dx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
		float dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Mth.atan2(dx, -dz));
		g.blit(RenderPipelines.GUI_TEXTURED, MinimapTextures.ARROW_ID, -6, -6, 0.0F, 0.0F, 12, 12, 16, 16);
		g.pose().popMatrix();

		// 北向标记
		g.text(font, "N", cx - font.width("N") / 2 + 1, y + 1, 0xFFFFFF66, true);
	}

	private static boolean moved(Vec3 pos) {
		return Math.abs(pos.x - lastX) >= 0.5F || Math.abs(pos.z - lastZ) >= 0.5F;
	}

	private static double lastX = Double.NaN;
	private static double lastZ = Double.NaN;

	private static void rebuild(Minecraft mc, Vec3 pos) {
		NativeImage img = MinimapTextures.minimap();
		int baseX = Mth.floor(pos.x);
		int baseZ = Mth.floor(pos.z);
		for (int py = 0; py < SIZE; py++) {
			for (int px = 0; px < SIZE; px++) {
				int bx = baseX + (px - SIZE / 2 + 1) / 2;
				int bz = baseZ + (py - SIZE / 2 + 1) / 2;
				img.setPixel(px, py, MapData.color(mc.level, bx, bz));
			}
		}
		MinimapTextures.uploadMinimap();
		needsRebuild = false;
		lastX = pos.x;
		lastZ = pos.z;
	}

	/** 把相对玩家的世界偏移换算成小图像素偏移,超出显示范围时钳到边缘 */
	private static int[] clampOffset(double worldX, double worldZ) {
		int ox = (int) Math.round(worldX * PX_PER_BLOCK);
		int oz = (int) Math.round(worldZ * PX_PER_BLOCK);
		int half = SIZE / 2 - 3;
		return new int[] { Mth.clamp(ox, -half, half), Mth.clamp(oz, -half, half) };
	}
}
