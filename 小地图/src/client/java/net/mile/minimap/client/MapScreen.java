package net.mile.minimap.client;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** P 键打开的大地图:地形 + 50 格刻度线 + 标签 + 死亡点,大小 50~1500 可调 */
public class MapScreen extends Screen {
	private static final int MIN_SIZE = 50;
	private static final int MAX_SIZE = 1500;

	private int mapX;
	private int mapY;
	private int mapPx;
	private int panelX;
	private int panelW;

	private EditBox nameBox;
	private SizeSlider sizeSlider;
	private String pendingName = "";
	private boolean needsRebuild = true;
	private int tickCounter;

	public MapScreen() {
		super(Component.literal("小地图"));
	}

	@Override
	protected void init() {
		// 开地图时把存档里的地形尽量灌进来(玩家走远了触发补扫)
		RegionScanner.requestScan(Minecraft.getInstance());

		mapPx = Mth.clamp(Math.min(this.width - 240, this.height - 80), 180, MinimapTextures.BIGMAP_MAX);
		mapX = 12;
		mapY = Math.max(20, (this.height - mapPx) / 2);
		panelX = mapX + mapPx + 12;
		panelW = Math.max(140, this.width - panelX - 8);

		int y = mapY;
		nameBox = new EditBox(this.font, panelX, y, panelW, 18, Component.literal("标签名"));
		nameBox.setMaxLength(32);
		nameBox.setHint(Component.literal("标签名(留空自动编号)"));
		nameBox.setValue(pendingName);
		addRenderableWidget(nameBox);
		y += 24;

		sizeSlider = new SizeSlider(panelX, y, panelW, 20);
		addRenderableWidget(sizeSlider);
		y += 26;

		addRenderableWidget(Button.builder(Component.literal("标记当前位置"), b -> addAtPlayer())
				.bounds(panelX, y, panelW, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("清除死亡点"), b -> {
			if (MinimapStore.deathPoint != null) {
				MinimapStore.clearDeath();
				msg("已清除死亡点");
			} else {
				msg("没有记录过死亡点");
			}
		}).bounds(panelX, y, panelW, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("清空全部标签"), b -> {
			if (!MinimapStore.waypoints.isEmpty()) {
				MinimapStore.clearWaypoints();
				msg("已清空全部标签");
			}
		}).bounds(panelX, y, panelW, 20).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal("关闭"), b -> onClose())
				.bounds(panelX, y, panelW, 20).build());

		needsRebuild = true;
	}

	@Override
	public void tick() {
		tickCounter++;
		if (tickCounter % 10 == 0) {
			needsRebuild = true;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		pendingName = nameBox.getValue();

		double px = player.getX();
		double pz = player.getZ();
		if (needsRebuild || Math.abs(px - lastRebuildX) >= 0.5 || Math.abs(pz - lastRebuildZ) >= 0.5) {
			rebuild(mc, px, pz);
		}

		double pxPerBlock = (double) mapPx / MinimapStore.mapSize;
		double cx = mapX + mapPx / 2.0;
		double cy = mapY + mapPx / 2.0;
		String dim = mc.level.dimension().identifier().toString();

		// 地图底图 + 描边
		g.fill(mapX - 1, mapY - 1, mapX + mapPx + 1, mapY + mapPx + 1, 0xCC000000);
		g.blit(RenderPipelines.GUI_TEXTURED, MinimapTextures.BIGMAP_ID, mapX, mapY,
				0.0F, 0.0F, mapPx, mapPx, MinimapTextures.BIGMAP_MAX, MinimapTextures.BIGMAP_MAX);

		// 50 格刻度线(文字标签按像素间距自动降频)
		int labelStep = 50;
		while (labelStep * pxPerBlock < 26 && labelStep < 500) {
			labelStep *= labelStep >= 250 ? 2 : labelStep >= 100 ? 5 : 2;
		}
		double left = px - MinimapStore.mapSize / 2.0;
		double right = px + MinimapStore.mapSize / 2.0;
		for (long gx = Mth.ceil(left / 50.0) * 50; gx <= right; gx += 50) {
			int sx = (int) Math.round(cx + (gx - px) * pxPerBlock);
			g.fill(sx, mapY, sx + 1, mapY + mapPx, 0x50000000);
			if (gx % labelStep == 0) {
				g.text(this.font, String.valueOf(gx), sx + 2, mapY + 2, 0xFFD8D8D8, true);
			}
		}
		double top = pz - MinimapStore.mapSize / 2.0;
		double bottom = pz + MinimapStore.mapSize / 2.0;
		for (long gz = Mth.ceil(top / 50.0) * 50; gz <= bottom; gz += 50) {
			int sy = (int) Math.round(cy + (gz - pz) * pxPerBlock);
			g.fill(mapX, sy, mapX + mapPx, sy + 1, 0x50000000);
			if (gz % labelStep == 0) {
				g.text(this.font, String.valueOf(gz), mapX + 2, sy + 2, 0xFFD8D8D8, true);
			}
		}

		// 标签
		int idx = 0;
		for (Waypoint wp : MinimapStore.waypoints) {
			if (!wp.dimension().equals(dim)) {
				continue;
			}
			int sx = (int) Math.round(cx + (wp.x() + 0.5 - px) * pxPerBlock);
			int sy = (int) Math.round(cy + (wp.z() + 0.5 - pz) * pxPerBlock);
			boolean inside = sx >= mapX + 5 && sx <= mapX + mapPx - 5 && sy >= mapY + 5 && sy <= mapY + mapPx - 5;
			int csx = Mth.clamp(sx, mapX + 5, mapX + mapPx - 5);
			int csy = Mth.clamp(sy, mapY + 5, mapY + mapPx - 5);
			g.fill(csx - 3, csy - 3, csx + 3, csy + 3, 0xFF000000);
			g.fill(csx - 2, csy - 2, csx + 2, csy + 2, wp.color());
			if (inside) {
				int dist = Mth.floor(Mth.sqrt((float) ((wp.x() + 0.5 - px) * (wp.x() + 0.5 - px)
						+ (wp.z() + 0.5 - pz) * (wp.z() + 0.5 - pz))));
				String line = wp.name() + " (" + dist + "格)";
				int ty = csy - 14 - (idx % 2) * 11; // 相邻标签上下错开防遮挡
				g.text(this.font, line, csx - this.font.width(line) / 2, ty, 0xFFFFFFFF, true);
			}
			idx++;
		}

		// 死亡点(红 X + 距离)
		Waypoint death = MinimapStore.deathPoint;
		if (death != null && death.dimension().equals(dim)) {
			int sx = (int) Math.round(cx + (death.x() + 0.5 - px) * pxPerBlock);
			int sy = (int) Math.round(cy + (death.z() + 0.5 - pz) * pxPerBlock);
			boolean inside = sx >= mapX + 5 && sx <= mapX + mapPx - 5 && sy >= mapY + 5 && sy <= mapY + mapPx - 5;
			int csx = Mth.clamp(sx, mapX + 5, mapX + mapPx - 5);
			int csy = Mth.clamp(sy, mapY + 5, mapY + mapPx - 5);
			g.text(this.font, "✖", csx - 4, csy - 5, MinimapStore.DEATH_COLOR, true);
			if (inside) {
				int dist = Mth.floor(Mth.sqrt((float) ((death.x() + 0.5 - px) * (death.x() + 0.5 - px)
						+ (death.z() + 0.5 - pz) * (death.z() + 0.5 - pz))));
				String line = death.name() + " (" + dist + "格)";
				g.text(this.font, line, csx - this.font.width(line) / 2, csy + 5, 0xFFFF8080, true);
			}
		}

		// 玩家箭头(旋转)
		float yaw = player.getYRot(partialTick);
		float dx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
		float dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
		g.pose().pushMatrix();
		g.pose().translate((float) cx, (float) cy);
		g.pose().rotate((float) Mth.atan2(dx, -dz));
		g.blit(RenderPipelines.GUI_TEXTURED, MinimapTextures.ARROW_ID, -7, -7, 0.0F, 0.0F, 14, 14, 16, 16);
		g.pose().popMatrix();

		// 北向标记
		g.text(this.font, "N ↑", (int) cx - this.font.width("N ↑") / 2, mapY + 2 - 12, 0xFFFFFF66, true);

		// 侧栏信息
		int infoY = mapY + 150;
		g.text(this.font, "玩家: " + Mth.floor(px) + ", " + player.getBlockY() + ", " + Mth.floor(pz),
				panelX, infoY, 0xFFE0E0E0, true);
		g.text(this.font, "维度: " + dimName(dim), panelX, infoY + 11, 0xFFE0E0E0, true);
		g.text(this.font, "标签: " + MinimapStore.waypoints.size() + " 个", panelX, infoY + 22, 0xFFE0E0E0, true);
		g.text(this.font, "比例: 1像素 ≈ " + String.format("%.1f", MinimapStore.mapSize / (double) mapPx) + "格",
				panelX, infoY + 33, 0xFFE0E0E0, true);

		// 底部提示 + 鼠标悬停坐标
		String hover = "";
		if (mouseX >= mapX && mouseX < mapX + mapPx && mouseY >= mapY && mouseY < mapY + mapPx) {
			int wx = Mth.floor(px + (mouseX - cx) / pxPerBlock);
			int wz = Mth.floor(pz + (mouseY - cy) / pxPerBlock);
			hover = "  |  鼠标: " + wx + ", " + wz;
		}
		String hint = "左键:添加标签  右键点标签:删除  滚轮/滑条:调大小" + hover;
		g.text(this.font, hint, mapX, Math.min(this.height - 10, mapY + mapPx + 6), 0xFFA0A0A0, true);
	}

	private double lastRebuildX = Double.NaN;
	private double lastRebuildZ = Double.NaN;

	private void rebuild(Minecraft mc, double px, double pz) {
		NativeImage img = MinimapTextures.bigmap();
		double pxPerBlock = (double) mapPx / MinimapStore.mapSize;
		for (int yy = 0; yy < mapPx; yy++) {
			for (int xx = 0; xx < mapPx; xx++) {
				int bx = Mth.floor(px + (xx - mapPx / 2.0) / pxPerBlock);
				int bz = Mth.floor(pz + (yy - mapPx / 2.0) / pxPerBlock);
				img.setPixel(xx, yy, MapData.color(mc.level, bx, bz));
			}
		}
		MinimapTextures.uploadBigmap();
		needsRebuild = false;
		lastRebuildX = px;
		lastRebuildZ = pz;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = event.x();
		double my = event.y();
		if (mx < mapX || mx >= mapX + mapPx || my < mapY || my >= mapY + mapPx) {
			return super.mouseClicked(event, doubleClick);
		}
		String dim = mc.level.dimension().identifier().toString();
		double pxPerBlock = (double) mapPx / MinimapStore.mapSize;
		int wx = Mth.floor(mc.player.getX() + (mx - (mapX + mapPx / 2.0)) / pxPerBlock);
		int wz = Mth.floor(mc.player.getZ() + (my - (mapY + mapPx / 2.0)) / pxPerBlock);

		if (event.button() == 1) {
			// 右键:删除点击处附近的标签(按屏幕像素找最近的)
			Waypoint target = findNearby(mc, mx, my, 10);
			if (target != null) {
				MinimapStore.remove(target);
				msg("已删除标签: " + target.name());
			}
			return true;
		}
		if (event.button() == 0) {
			if (findNearby(mc, mx, my, 8) == null) {
				String name = nameBox.getValue().strip();
				Waypoint wp = MinimapStore.addWaypoint(dim, wx, mc.player.getBlockY(), wz, name);
				msg("已添加标签: " + wp.name() + " (" + wx + ", " + wz + ")");
				if (!name.isBlank()) {
					nameBox.setValue("");
				}
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && mouseX >= mapX && mouseX < mapX + mapPx && mouseY >= mapY && mouseY < mapY + mapPx) {
			MinimapStore.setMapSize(MinimapStore.mapSize + (scrollY > 0 ? -50 : 50));
			sizeSlider.syncFromStore();
			needsRebuild = true;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// 再按一次地图键(P)也能关
		if (MinimapClient.openMapKey != null && MinimapClient.openMapKey.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	private Waypoint findNearby(Minecraft mc, double mx, double my, int radius) {
		String dim = mc.level.dimension().identifier().toString();
		double pxPerBlock = (double) mapPx / MinimapStore.mapSize;
		double cx = mapX + mapPx / 2.0;
		double cy = mapY + mapPx / 2.0;
		Waypoint best = null;
		double bestD2 = radius * (double) radius;
		for (Waypoint wp : MinimapStore.waypoints) {
			if (!wp.dimension().equals(dim)) {
				continue;
			}
			double sx = cx + (wp.x() + 0.5 - mc.player.getX()) * pxPerBlock;
			double sy = cy + (wp.z() + 0.5 - mc.player.getZ()) * pxPerBlock;
			double d2 = (sx - mx) * (sx - mx) + (sy - my) * (sy - my);
			if (d2 <= bestD2) {
				bestD2 = d2;
				best = wp;
			}
		}
		return best;
	}

	private void addAtPlayer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		String name = nameBox.getValue().strip();
		Waypoint wp = MinimapStore.addWaypoint(
				mc.level.dimension().identifier().toString(),
				mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), name);
		msg("已标记当前位置: " + wp.name());
		if (!name.isBlank()) {
			nameBox.setValue("");
		}
	}

	private void msg(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("[小地图] " + text));
		}
	}

	public static String dimName(String dim) {
		return switch (dim) {
			case "minecraft:overworld" -> "主世界";
			case "minecraft:the_nether" -> "下界";
			case "minecraft:the_end" -> "末地";
			default -> dim;
		};
	}

	/** 大小滑条:50~1500,吸附到 50 的倍数 */
	private class SizeSlider extends AbstractSliderButton {
		SizeSlider(int x, int y, int w, int h) {
			super(x, y, w, h, Component.empty(), (MinimapStore.mapSize - MIN_SIZE) / (double) (MAX_SIZE - MIN_SIZE));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal("地图大小: " + MinimapStore.mapSize + "×" + MinimapStore.mapSize + "格"));
		}

		@Override
		protected void applyValue() {
			int v = (int) Math.round(MIN_SIZE + value * (MAX_SIZE - MIN_SIZE));
			MinimapStore.setMapSize(Math.round(v / 50.0F) * 50);
			updateMessage();
			needsRebuild = true;
		}

		void syncFromStore() {
			value = (MinimapStore.mapSize - MIN_SIZE) / (double) (MAX_SIZE - MIN_SIZE);
			updateMessage();
		}
	}
}
