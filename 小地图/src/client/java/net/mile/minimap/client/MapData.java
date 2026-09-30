package net.mile.minimap.client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 地形颜色缓存,分两层:
 * 地表层 SURFACE = 磁盘扫描(存档) + 已加载区块实时采样;
 * 洞穴层 CAVE = 仅实时采样(玩家所在 Y 层往下找地板,磁盘数据没有分层信息)。
 * 玩家钻到地表下(矿洞)自动切洞穴层,回到地表切回。
 */
public final class MapData {
	/** 未探索/该层没有数据的区域底色(暗蓝灰"迷雾",比纯黑柔和,一眼能看出是没数据而不是坏图) */
	public static final int UNEXPLORED = 0xFF28303E;
	/** 同一区块实时重采样的最小间隔(毫秒) */
	private static final long FRESH_MS = 1000;
	/** 每 tick 最多整块刷新多少个区块(把采样开销摊到多帧,免得卡顿) */
	private static final int REFRESH_BUDGET_PER_TICK = 12;
	/** 洞穴层往下找地板的最深范围(格) */
	private static final int CAVE_DEPTH = 48;
	/** 玩家低于地表多少格算进洞/高于多少格算出洞(带迟滞防抖) */
	private static final int CAVE_ENTER = 8;
	private static final int CAVE_EXIT = 5;

	private static final ConcurrentHashMap<Long, int[]> SURFACE = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Long, int[]> CAVE = new ConcurrentHashMap<>();
	/** 各区块列高(给跨区块明暗着色用),与上面两层对应 */
	private static final ConcurrentHashMap<Long, int[]> SURFACE_H = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Long, int[]> CAVE_H = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Long, Long> FRESH_AT = new ConcurrentHashMap<>();
	private static final AtomicInteger refreshBudget = new AtomicInteger();

	/** 当前维度是否有顶盖(下界):顶盖维度固定用地表 OCEAN_FLOOR 图,不做洞穴切换 */
	private static volatile boolean ceilingDim = false;
	private static volatile String currentDim = "";
	private static volatile boolean caveMode = false;
	private static volatile int caveRefY = 0;

	private MapData() {
	}

	public static void clear() {
		SURFACE.clear();
		CAVE.clear();
		SURFACE_H.clear();
		CAVE_H.clear();
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

	/** 后台存档扫描线程发布一个区块的地表颜色(不覆盖已有的实时数据) */
	static void publishDiskChunk(int cx, int cz, int[] colors) {
		SURFACE.putIfAbsent(chunkKey(cx, cz), colors);
	}

	/**
	 * 每 tick 调用:重置刷新额度 + 检测玩家是否在地表之下(进矿洞自动切洞穴层)。
	 * 返回 true 表示模式刚切换,调用方应触发重绘。
	 */
	public static boolean tickCaveMode(ClientLevel level, LocalPlayer player) {
		refreshBudget.set(REFRESH_BUDGET_PER_TICK);
		if (player == null || level == null) {
			return false;
		}
		int px = Mth.floor(player.getX());
		int pz = Mth.floor(player.getZ());
		caveRefY = player.getBlockY();
		if (ceilingDim || !level.hasChunk(px >> 4, pz >> 4)) {
			return false; // 下界本来就是"洞",维持 OCEAN_FLOOR 地表图
		}
		int surf = level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz);
		int py = player.getBlockY();
		boolean was = caveMode;
		if (caveMode ? py >= surf - CAVE_EXIT : py <= surf - CAVE_ENTER) {
			caveMode = !caveMode;
			FRESH_AT.clear(); // 切层:让已加载区块立刻按新层重刷
			return was != caveMode;
		}
		return false;
	}

	static boolean isCaveMode() {
		return caveMode;
	}

	/** 取 (x,z) 列的地形色:当前层已加载区块实时采样(节流+额度),否则查缓存,再没有 UNEXPLORED */
	public static int color(ClientLevel level, int x, int z) {
		int cx = x >> 4;
		int cz = z >> 4;
		long key = chunkKey(cx, cz);
		if (level.hasChunk(cx, cz)) {
			maybeRefresh(level, key, cx, cz);
		} else if (caveMode) {
			// 洞穴层只信实时数据(磁盘扫描是地表层),没加载就没图
			return UNEXPLORED;
		}
		int[] arr = (caveMode ? CAVE : SURFACE).get(key);
		return arr != null ? arr[colIndex(x, z)] : UNEXPLORED;
	}

	private static void maybeRefresh(ClientLevel level, long key, int cx, int cz) {
		long now = System.currentTimeMillis();
		Long last = FRESH_AT.get(key);
		if (last != null && now - last < FRESH_MS) {
			return;
		}
		if (refreshBudget.decrementAndGet() < 0) {
			return; // 本 tick 额度用完,保持旧数据下 tick 再来
		}
		FRESH_AT.put(key, now);
		if (caveMode) {
			CAVE.put(key, sampleCaveChunk(level, cx, cz));
		} else {
			SURFACE.put(key, sampleSurfaceChunk(level, cx, cz));
		}
	}

	/** 地表层:按高度图取顶面方块色(透明/空气时往下探 4 格,与磁盘扫描一致) */
	private static int[] sampleSurfaceChunk(ClientLevel level, int cx, int cz) {
		Heightmap.Types type = ceilingDim ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.MOTION_BLOCKING;
		int minY = level.getMinY();
		int bx = cx << 4;
		int bz = cz << 4;
		int[] heights = new int[256];
		int[] base = new int[256];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int i = x * 16 + z;
				int h = level.getHeight(type, bx + x, bz + z);
				int top = h - 1;
				int col = 0;
				if (h > minY) {
					col = blockColor(level, bx + x, top, bz + z);
					int down = 0;
					while (col == 0 && down < 4 && top - 1 >= minY) {
						top--;
						down++;
						col = blockColor(level, bx + x, top, bz + z);
					}
				}
				heights[i] = top;
				base[i] = col;
			}
		}
		return shadeChunk(cx, cz, heights, base, SURFACE_H);
	}

	/** 洞穴层:从玩家头部高度往下找第一个非空方块(通道地板/墙壁),范围 48 格 */
	private static int[] sampleCaveChunk(ClientLevel level, int cx, int cz) {
		int top = caveRefY + 1;
		int bottom = Math.max(level.getMinY(), caveRefY - CAVE_DEPTH);
		int bx = cx << 4;
		int bz = cz << 4;
		int[] heights = new int[256];
		int[] base = new int[256];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int col = 0;
				int y = top;
				for (; y >= bottom; y--) {
					col = blockColor(level, bx + x, y, bz + z);
					if (col != 0) {
						break;
					}
				}
				int i = x * 16 + z;
				heights[i] = col != 0 ? y : bottom - 1;
				base[i] = col;
			}
		}
		return shadeChunk(cx, cz, heights, base, CAVE_H);
	}

	private static int blockColor(ClientLevel level, int x, int y, int z) {
		return level.getBlockState(new BlockPos(x, y, z)).getBlock().defaultMapColor().col;
	}

	/** 用本区块与北邻区块的列高做明暗着色(与磁盘扫描一致),并登记列高 */
	private static int[] shadeChunk(int cx, int cz, int[] heights, int[] base, ConcurrentHashMap<Long, int[]> heightsStore) {
		int[] north = heightsStore.get(chunkKey(cx, cz - 1));
		int[] out = new int[256];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int i = x * 16 + z;
				int col = base[i];
				if (col == 0) {
					out[i] = UNEXPLORED;
					continue;
				}
				int hn = z > 0 ? heights[i - 1] : (north != null ? north[i] : heights[i]);
				out[i] = shade(col, heights[i] - hn);
			}
		}
		heightsStore.put(chunkKey(cx, cz), heights);
		return out;
	}

	/** 明暗立体着色:比北边邻列高就更亮,低就更暗 */
	static int shade(int col, int dh) {
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
