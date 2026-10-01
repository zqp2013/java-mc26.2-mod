package net.mile.minimap.client;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 后台线程直接读存档 region 文件(.mca),把已生成区块的地形颜色全部灌进 MapData,
 * 让地图不止显示当前视野——存档里有的地形全部画出来。
 * 只在单人模式可用(多人没有存档文件);多人仍退回只显示加载过的区块。
 */
public final class RegionScanner {
	/** 扫描半径(格):覆盖最大 1500 图 + 边距 */
	private static final int RADIUS_BLOCKS = 880;
	private static final int RADIUS_CHUNKS = RADIUS_BLOCKS / 16 + 1;
	/** 玩家离开上次扫描中心超过这么多格才重新扫一轮 */
	private static final int RESCAN_DISTANCE = 256;

	private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
	/** 排查坏区块用:-Dminimap.debugparse=true 打印每个解析失败原因 */
	private static final boolean DEBUG_PARSE = Boolean.getBoolean("minimap.debugparse");

	private static volatile int generation = 0;
	private static Thread worker;
	private static double lastCenterX = Double.NaN;
	private static double lastCenterZ = Double.NaN;
	/** 本维度已解析过的区块(重扫时跳过,免重复 IO) */
	private static final Set<Long> SCANNED = ConcurrentHashMap.newKeySet();

	private RegionScanner() {
	}

	/** 换世界/换维度/离开时调用,终止旧扫描并重置状态 */
	public static void reset() {
		generation++;
		SCANNED.clear();
		lastCenterX = Double.NaN;
		lastCenterZ = Double.NaN;
	}

	/** 主线程调用:需要时启动一轮以玩家为中心的后台扫描 */
	public static void requestScan(Minecraft mc) {
		if (mc.player != null) {
			requestScan(mc, mc.player.getX(), mc.player.getZ());
		}
	}

	/** 主线程调用:围绕指定中心扫描(大地图拖动看远处时,那边不在玩家中心的扫描半径内) */
	public static void requestScan(Minecraft mc, double px, double pz) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (worker != null && worker.isAlive()) {
			return;
		}
		// 单人才有存档文件;多人退回只画已加载区块
		if (!mc.hasSingleplayerServer() || mc.getSingleplayerServer() == null) {
			return;
		}
		if (!Double.isNaN(lastCenterX) && Math.hypot(px - lastCenterX, pz - lastCenterZ) < RESCAN_DISTANCE) {
			return;
		}
		lastCenterX = px;
		lastCenterZ = pz;

		// 全部参数在主线程快照,后台线程不碰 level/server 活对象
		int gen = generation;
		Path worldRoot = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
		ResourceKey<Level> dimKey = mc.level.dimension();
		String dimId = dimKey.identifier().toString();
		int minY = mc.level.getMinY();
		int height = mc.level.getHeight();
		boolean ceiling = MapData.isCeilingDim();
		// 用集成服务端的注册表(26.2 群系数据驱动,服务端一定加载了世界的群系)
		PalettedContainerFactory factory = PalettedContainerFactory.create(mc.getSingleplayerServer().registryAccess());
		int centerCx = Mth.floor(px) >> 4;
		int centerCz = Mth.floor(pz) >> 4;
		String label = worldRoot.getFileName() == null ? "world" : worldRoot.getFileName().toString();

		Thread t = new Thread(() -> run(gen, worldRoot, label, dimKey, dimId, minY, height, ceiling, factory, centerCx, centerCz),
				"minimap-region-scan");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY + 1);
		worker = t;
		t.start();
	}

	private static void run(int gen, Path worldRoot, String label, ResourceKey<Level> dimKey, String dimId,
			int minY, int height, boolean ceiling, PalettedContainerFactory factory, int centerCx, int centerCz) {
		try {
			Path regionDir = regionDirFor(worldRoot, dimId);
			if (!Files.isDirectory(regionDir)) {
				return;
			}
			int boxMinCx = centerCx - RADIUS_CHUNKS;
			int boxMaxCx = centerCx + RADIUS_CHUNKS;
			int boxMinCz = centerCz - RADIUS_CHUNKS;
			int boxMaxCz = centerCz + RADIUS_CHUNKS;

			List<int[]> regions = new ArrayList<>();
			try (Stream<Path> files = Files.list(regionDir)) {
				files.forEach(p -> {
					Matcher m = REGION_NAME.matcher(p.getFileName().toString());
					if (m.matches()) {
						int ra = Integer.parseInt(m.group(1));
						int rb = Integer.parseInt(m.group(2));
						// 区域与扫描框相交才扫
						if (ra * 32 <= boxMaxCx && ra * 32 + 31 >= boxMinCx
								&& rb * 32 <= boxMaxCz && rb * 32 + 31 >= boxMinCz) {
							regions.add(new int[] { ra, rb });
						}
					}
				});
			}
			regions.sort(Comparator.comparingInt(r -> Math.abs(r[0] * 32 + 16 - centerCx) + Math.abs(r[1] * 32 + 16 - centerCz)));

			DimInfo dimInfo = new DimInfo(minY, height);
			Heightmap.Types hmType = ceiling ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.MOTION_BLOCKING;
			// 第一遍:逐区块解析,记录列高与未着色基色
			Map<Long, int[]> heightsMap = new HashMap<>();
			Map<Long, int[]> baseMap = new HashMap<>();
			List<Long> pending = new ArrayList<>(); // 待着色发布的区块 key
			for (int[] r : regions) {
				if (gen != generation) {
					return; // 换世界/换维度了,废弃本轮
				}
				Path file = regionDir.resolve("r." + r[0] + "." + r[1] + ".mca");
				try (RegionFile rf = new RegionFile(new RegionStorageInfo(label, dimKey, "chunk"), file, regionDir, false)) {
					for (int cz = Math.max(boxMinCz, r[1] * 32); cz <= Math.min(boxMaxCz, r[1] * 32 + 31); cz++) {
						for (int cx = Math.max(boxMinCx, r[0] * 32); cx <= Math.min(boxMaxCx, r[0] * 32 + 31); cx++) {
							long key = MapData.chunkKey(cx, cz);
							if (SCANNED.contains(key)) {
								continue;
							}
							parseChunk(rf, cx, cz, dimInfo, hmType, factory, heightsMap, baseMap, pending);
							SCANNED.add(key);
						}
					}
				} catch (IOException e) {
					MinimapClient.LOGGER.warn("读区域文件失败 {}: {}", file.getFileName(), e.toString());
				}
			}
			// 第二遍:用北邻区块高度补明暗着色并发布
			for (long key : pending) {
				if (gen != generation) {
					return;
				}
				shadeAndPublish(key, heightsMap, baseMap);
			}
		} catch (Exception e) {
			MinimapClient.LOGGER.warn("存档扫描异常: {}", e.toString());
		}
	}

	static void parseChunk(RegionFile rf, int cx, int cz, DimInfo dimInfo, Heightmap.Types hmType,
			PalettedContainerFactory factory, Map<Long, int[]> heightsMap, Map<Long, int[]> baseMap, List<Long> pending) {
		try {
			ChunkPos pos = new ChunkPos(cx, cz);
			if (!rf.hasChunk(pos)) {
				return;
			}
			CompoundTag tag;
			try (DataInputStream dis = rf.getChunkDataInputStream(pos)) {
				if (dis == null) {
					return;
				}
				tag = NbtIo.read(dis);
			}
			if (tag == null) {
				return;
			}
			SerializableChunkData data = SerializableChunkData.parse(dimInfo, factory, tag);
			// noise 之前的半成品区块还没有方块,跳过;之后(features/light 未完成)已有地形,照画
			ChunkStatus status = data.chunkStatus();
			if (status == null || status.getIndex() < ChunkStatus.NOISE.getIndex()) {
				if (DEBUG_PARSE) {
					System.out.println("[minimap] 半成品区块跳过 " + cx + "," + cz + " -> " + status);
				}
				return;
			}
			long[] hmLongs = data.heightmaps().get(hmType);
			if (hmLongs == null) {
				hmLongs = data.heightmaps().get(Heightmap.Types.MOTION_BLOCKING);
			}
			if (hmLongs == null) {
				hmLongs = data.heightmaps().get(Heightmap.Types.WORLD_SURFACE);
			}
			if (hmLongs == null || hmLongs.length == 0) {
				return;
			}
			SimpleBitStorage heights = new SimpleBitStorage(Mth.ceillog2(dimInfo.height() + 1), 256, hmLongs);

			Map<Integer, LevelChunkSection> sections = new HashMap<>();
			for (SerializableChunkData.SectionData sd : data.sectionData()) {
				if (sd.chunkSection() != null) {
					sections.put(sd.y(), sd.chunkSection());
				}
			}

			int[] heightArr = new int[256];
			int[] baseArr = new int[256];
			boolean any = false;
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					int i = x * 16 + z; // 与原版 Heightmap.getIndex 一致
					int topY = heights.get(i) + dimInfo.minY() - 1;
					heightArr[i] = topY;
					int col;
					LevelChunkSection sec = topY < dimInfo.minY() ? null : sections.get(topY >> 4);
					if (sec == null) {
						col = 0;
					} else {
						col = sec.getBlockState(x, topY & 15, z).getBlock().defaultMapColor().col;
					}
					if (col == 0) {
						// 高度图说有方块但 section 里取不到(或空气),往下探 4 格
						col = 0;
						for (int dy = 1; dy <= 4 && topY - dy >= dimInfo.minY(); dy++) {
							LevelChunkSection s2 = sections.get((topY - dy) >> 4);
							if (s2 != null) {
								col = s2.getBlockState(x, (topY - dy) & 15, z).getBlock().defaultMapColor().col;
								if (col != 0) {
									heightArr[i] = topY - dy;
									break;
								}
							}
						}
						if (col == 0) {
							baseArr[i] = MapData.UNEXPLORED;
							continue;
						}
					}
					any = true;
					baseArr[i] = 0xFF000000 | col;
				}
			}
			if (!any) {
				return;
			}
			long key = MapData.chunkKey(cx, cz);
			heightsMap.put(key, heightArr);
			baseMap.put(key, baseArr);
			pending.add(key);
		} catch (Exception e) {
			// 坏区块直接跳过
			if (DEBUG_PARSE) {
				System.out.println("[minimap] 区块(" + cx + "," + cz + ")解析失败: " + e);
			}
		}
	}

	/** 用本区块与北邻区块的列高做明暗着色,发布到 MapData */
	static void shadeAndPublish(long key, Map<Long, int[]> heightsMap, Map<Long, int[]> baseMap) {
		int cx = (int) (key >> 32);
		int cz = (int) key;
		int[] heights = heightsMap.get(key);
		int[] base = baseMap.get(key);
		if (heights == null || base == null) {
			return;
		}
		int[] north = heightsMap.get(MapData.chunkKey(cx, cz - 1));
		int[] out = new int[256];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int i = x * 16 + z;
				int b = base[i];
				if (b == MapData.UNEXPLORED) {
					out[i] = b;
					continue;
				}
				int hn = z > 0 ? heights[i - 1] : (north != null ? north[i] : heights[i]);
				out[i] = MapData.shade(b & 0xFFFFFF, heights[i] - hn);
			}
		}
		MapData.publishDiskChunk(cx, cz, out);
	}

	/**
	 * 维度对应的 region 目录。26.2 新存档布局:所有维度都在 dimensions/&lt;ns&gt;/&lt;path&gt;/region/
	 * (主世界也是);老布局是 主世界 region/ 下界 DIM-1/ 末地 DIM1/,找不到新目录时兜底。
	 */
	static Path regionDirFor(Path worldRoot, String dimId) {
		Identifier id = Identifier.tryParse(dimId);
		if (id != null) {
			Path modern = worldRoot.resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath()).resolve("region");
			if (Files.isDirectory(modern)) {
				return modern;
			}
		}
		return switch (dimId) {
			case "minecraft:the_nether" -> worldRoot.resolve("DIM-1").resolve("region");
			case "minecraft:the_end" -> worldRoot.resolve("DIM1").resolve("region");
			default -> worldRoot.resolve("region");
		};
	}

	/** 不可变的高度信息快照(给原版解析器算高度图位宽用),后台线程安全 */
	record DimInfo(int minY, int height) implements LevelHeightAccessor {
		@Override
		public int getHeight() {
			return height;
		}

		@Override
		public int getMinY() {
			return minY;
		}
	}
}
