package net.mile.minimap.client;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** 两个动态贴图(小地图按最大边长分配 / 大地图 640×640),懒创建,常驻复用 */
public final class MinimapTextures {
	public static final Identifier MINIMAP_ID = MinimapClient.id("dynamic/minimap");
	public static final Identifier BIGMAP_ID = MinimapClient.id("dynamic/bigmap");

	/** 玩家方向箭头贴图(箭头朝上,渲染时按朝向旋转) */
	public static final Identifier ARROW_ID = MinimapClient.id("textures/gui/arrow.png");

	/** 小地图贴图的最大边长(实际绘制 hudSize 见 MinimapStore,贴图一次分配永不重建) */
	public static final int MINIMAP_MAX = 160;
	/** 大地图贴图的最大边长(实际绘制区域 mapPx <= 这个值,贴图一次分配永不重建) */
	public static final int BIGMAP_MAX = 640;

	private static DynamicTexture minimap;
	private static NativeImage minimapImage;
	private static DynamicTexture bigmap;
	private static NativeImage bigmapImage;

	private MinimapTextures() {
	}

	/** 必须在渲染线程调用(HUD/Screen 渲染路径里) */
	public static NativeImage minimap() {
		if (minimap == null) {
			minimapImage = new NativeImage(MINIMAP_MAX, MINIMAP_MAX, false);
			minimap = new DynamicTexture(() -> "mile minimap", minimapImage);
			Minecraft.getInstance().getTextureManager().register(MINIMAP_ID, minimap);
		}
		return minimapImage;
	}

	public static void uploadMinimap() {
		if (minimap != null) {
			minimap.upload();
		}
	}

	public static NativeImage bigmap() {
		if (bigmap == null) {
			bigmapImage = new NativeImage(BIGMAP_MAX, BIGMAP_MAX, false);
			bigmap = new DynamicTexture(() -> "mile bigmap", bigmapImage);
			Minecraft.getInstance().getTextureManager().register(BIGMAP_ID, bigmap);
		}
		return bigmapImage;
	}

	public static void uploadBigmap() {
		if (bigmap != null) {
			bigmap.upload();
		}
	}
}
