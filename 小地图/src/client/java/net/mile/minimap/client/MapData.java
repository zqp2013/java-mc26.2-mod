package net.mile.minimap.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * 地形颜色缓存:按区块存 int[256](列颜色,x*16+z 与原版 Heightmap 一致)。
 * 已加载区块实时采样(每区块最多 1 秒刷新一次),未加载区块用 RegionScanner 从存档读出的颜色。
 */
public final class MapData {
	/** 未探索/存档里也没有的区域的底色(深蓝灰) */
	public static final int UNEXPLORED = 0xFF14141E;
	/** 亮度兜底色(空气/未知) */
	private static final int VOID = 0xFF0A0A0A;
	/** 同一区块实时重采样的最小间隔(毫秒) */
	private static final long FRESH_MS = 1000;

	/** chunkKey -> int[256] 列颜色 */
	private static final ConcurrentHashMap<Long, int[]> CHUNK_COLORS = new ConcurrentHashMap<>();
	/** chunkKey -> 上次实时刷新时刻(毫秒) */
	private static final ConcurrentHashMap<Long, Long> FRESH_AT = new ConcurrentHashMap<>();
	/** 当前维度是否有顶盖(下界):顶盖维度改用 OCEAN_FLOOR 高度图,否则看到的全是基岩顶 */
	private static volatile boolean ceilingDim = false;
	private static volatile String currentDim = "";

	private MapData() {
	}

	public static void clear() {
		CHUNK_COLORS.clear();
		FRESH_AT.clear();
	}

	public static void updateDimension(ClientLevel level) {
		ceilingDim = level.dimensionType().hasCeiling();
		currentDim = level.dimension().identifier().toString();
	}

	/** 传送门切换维度时需要清缓存(颜色缓存按维度算) */
	public static boolean dimensionChanged(ClientLevel level) {
		return !currentDim.equals(level.dimension().identifier().toString());
	}

	static long chunkKey(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	static int colIndex(int x, int z) {
		return (x & 15) * 16 + (z & 15);
	}

	/** 后台存档扫描线程发布一个区块的列颜色(不覆盖已加载区块的实时数据) */
	static void publishDiskChunk(int cx, int cz, int[] colors) {
		CHUNK_COLORS.putIfAbsent(chunkKey(cx, cz), colors);
	}

	/** 取 (x,z) 列的地形色;已加载区块实时采样,否则查缓存,再没有返回 UNEXPLORED */
	public static int color(ClientLevel level, int x, int z) {
		int cx = x >> 4;
		int cz = z >> 4;
		long key = chunkKey(cx, cz);
		if (level.hasChunk(cx, cz)) {
			long now = System.currentTimeMillis();
			Long last = FRESH_AT.get(key);
			if (last == null || now - last >= FRESH_MS) {
				FRESH_AT.put(key, now);
				int c = sampleFresh(level, x, z);
				int[] arr = CHUNK_COLORS.computeIfAbsent(key, k -> new int[256]);
				arr[colIndex(x, z)] = c;
				return c;
			}
		}
		int[] arr = CHUNK_COLORS.get(key);
		return arr != null ? arr[colIndex(x, z)] : UNEXPLORED;
	}

	private static int sampleFresh(ClientLevel level, int x, int z) {
		Heightmap.Types type = ceilingDim ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.MOTION_BLOCKING;
		int h = level.getHeight(type, x, z);
		if (h <= level.getMinY()) {
			return VOID;
		}
		// 北邻区块没加载就不做明暗着色(跨区块 getHeight 会炸)
		int hn = level.hasChunk(x >> 4, (z - 1) >> 4) ? level.getHeight(type, x, z - 1) : h;
		int col = level.getBlockState(new BlockPos(x, h - 1, z)).getBlock().defaultMapColor().col;
		return shade(col, h - hn);
	}

	/** 明暗立体着色:比北边邻列高就更亮,低就更暗 */
	static int shade(int col, int dh) {
		if (col == 0) {
			return VOID;
		}
		float f = Mth.clamp(1.0F + dh * 0.10F, 0.55F, 1.45F);
		int r = Mth.clamp((int) (((col >> 16) & 0xFF) * f), 0, 255);
		int g = Mth.clamp((int) (((col >> 8) & 0xFF) * f), 0, 255);
		int b = Mth.clamp((int) ((col & 0xFF) * f), 0, 255);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	// 供 RegionScanner 读取维度参数
	static boolean isCeilingDim() {
		return ceilingDim;
	}
}
