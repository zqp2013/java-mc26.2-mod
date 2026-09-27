package net.mile.minimap.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

/**
 * 标签/死亡点/大地图尺寸的持久化:每个世界一个文件 {@code config/minimap/<世界名>.txt}。
 * 行格式:
 * <pre>
 * mapSize 500
 * death minecraft:overworld 123 64 -456
 * wp minecraft:overworld 200 70 -300 标签名
 * </pre>
 */
public final class MinimapStore {
	/** 标签自动配色(ARGB) */
	private static final int[] PALETTE = {
			0xFF55FF55, 0xFFFFFF55, 0xFF55FFFF, 0xFFFF8826,
			0xFFFF55FF, 0xFFAA88FF, 0xFF6BFF7B, 0xFFFF7B9E,
	};
	public static final int DEATH_COLOR = 0xFFFF4040;

	public static final List<Waypoint> waypoints = new ArrayList<>();
	public static Waypoint deathPoint;
	/** 大地图边长(格),50~1500,50 的倍数 */
	public static int mapSize = 300;

	private static Path file;
	private static boolean wasDead;
	private static int autoIndex = 1;

	private MinimapStore() {
	}

	public static void onJoin(Minecraft mc) {
		waypoints.clear();
		deathPoint = null;
		autoIndex = 1;
		file = FabricLoader.getInstance().getConfigDir().resolve("minimap").resolve(worldKey(mc) + ".txt");
		load();
	}

	public static void onLeave() {
		save();
		waypoints.clear();
		deathPoint = null;
		file = null;
		wasDead = false;
	}

	/** 每刻调用:检测玩家死亡,记录死亡点 */
	public static void tickDeath(Player player) {
		if (player == null) {
			wasDead = false;
			return;
		}
		boolean dead = player.isDeadOrDying();
		if (dead && !wasDead) {
			BlockPos pos = player.blockPosition();
			deathPoint = new Waypoint(player.level().dimension().identifier().toString(),
					pos.getX(), pos.getY(), pos.getZ(), "死亡点", DEATH_COLOR);
			save();
		}
		wasDead = dead;
	}

	public static Waypoint addWaypoint(String dimension, int x, int y, int z, String name) {
		if (name == null || name.isBlank()) {
			name = "地点" + autoIndex++;
		} else {
			bumpAutoIndex(name);
		}
		Waypoint wp = new Waypoint(dimension, x, y, z, name, PALETTE[waypoints.size() % PALETTE.length]);
		waypoints.add(wp);
		save();
		return wp;
	}

	/** 删除指定标签 */
	public static void remove(Waypoint wp) {
		if (waypoints.remove(wp)) {
			save();
		}
	}

	public static void clearWaypoints() {
		if (!waypoints.isEmpty()) {
			waypoints.clear();
			save();
		}
	}

	public static void clearDeath() {
		if (deathPoint != null) {
			deathPoint = null;
			save();
		}
	}

	public static void setMapSize(int size) {
		mapSize = Math.max(50, Math.min(1500, size));
		save();
	}

	/** 世界标识:单人=存档文件夹名,多人=服务器 IP */
	private static String worldKey(Minecraft mc) {
		String raw = "default";
		if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
			Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
			if (root.getFileName() != null) {
				raw = root.getFileName().toString();
			}
		} else {
			ServerData server = mc.getCurrentServer();
			if (server != null && server.ip != null && !server.ip.isBlank()) {
				raw = server.ip;
			}
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			sb.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '_');
		}
		if (!sb.toString().equals(raw)) {
			// 里有非文件名安全字符(如中文存档名),加 hash 后缀防碰撞
			sb.append('-').append(Integer.toHexString(raw.hashCode()));
		}
		return sb.toString();
	}

	private static void load() {
		if (file == null || !Files.exists(file)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				String[] parts = line.split(" ", 6);
				switch (parts[0]) {
					case "mapSize" -> {
						if (parts.length >= 2) {
							mapSize = Math.max(50, Math.min(1500, Integer.parseInt(parts[1])));
						}
					}
					case "death" -> {
						if (parts.length >= 5) {
							deathPoint = new Waypoint(parts[1], Integer.parseInt(parts[2]),
									Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), "死亡点", DEATH_COLOR);
						}
					}
					case "wp" -> {
						if (parts.length >= 6) {
							waypoints.add(new Waypoint(parts[1], Integer.parseInt(parts[2]),
									Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
									parts[5], PALETTE[(waypoints.size()) % PALETTE.length]));
							bumpAutoIndex(parts[5]);
						}
					}
					default -> {
					}
				}
			}
		} catch (Exception e) {
			MinimapClient.LOGGER.warn("读取小地图配置失败: {}", e.toString());
		}
	}

	public static void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			StringBuilder sb = new StringBuilder();
			sb.append("mapSize ").append(mapSize).append('\n');
			if (deathPoint != null) {
				sb.append("death ").append(deathPoint.dimension()).append(' ')
						.append(deathPoint.x()).append(' ').append(deathPoint.y()).append(' ')
						.append(deathPoint.z()).append('\n');
			}
			for (Waypoint wp : waypoints) {
				sb.append("wp ").append(wp.dimension()).append(' ')
						.append(wp.x()).append(' ').append(wp.y()).append(' ').append(wp.z()).append(' ')
						.append(wp.name().replace('\n', ' ')).append('\n');
			}
			Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MinimapClient.LOGGER.warn("保存小地图配置失败: {}", e.toString());
		}
	}

	/** 如果玩家手动输入了 "地点N" 这种名字,自动编号跟着跳,避免重名 */
	private static void bumpAutoIndex(String name) {
		if (name.startsWith("地点")) {
			try {
				autoIndex = Math.max(autoIndex, Integer.parseInt(name.substring(2)) + 1);
			} catch (NumberFormatException ignored) {
			}
		}
	}
}
