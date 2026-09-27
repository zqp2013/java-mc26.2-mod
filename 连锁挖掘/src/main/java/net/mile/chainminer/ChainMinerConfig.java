package net.mile.chainminer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按玩家保存的连锁挖掘设置(每次最多连锁多少个方块 + 掉落物是否自动掉到脚下)。
 * 持久化到 config/chainminer-settings.txt,退出世界或重启游戏都不会丢失。
 * 文件每行一个玩家:<UUID> <上限> <脚下掉落:0|1>(旧格式没有第三列时按关处理)。
 */
public final class ChainMinerConfig {
	public static final int MIN = 1;
	public static final int MAX = 400;
	public static final int DEFAULT = 64;
	public static final boolean DEFAULT_VACUUM = false;

	private static final Logger LOGGER = LoggerFactory.getLogger(ChainMinerMod.MOD_ID);
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chainminer-settings.txt");

	private static final Map<UUID, ChainSettings> SETTINGS_BY_PLAYER = new HashMap<>();
	private static boolean loaded = false;

	private ChainMinerConfig() {
	}

	private static synchronized void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split(" ");
				if (parts.length < 2) {
					continue;
				}
				try {
					UUID playerId = UUID.fromString(parts[0]);
					int max = Math.max(MIN, Math.min(MAX, Integer.parseInt(parts[1])));
					boolean vacuum = parts.length >= 3 && Integer.parseInt(parts[2]) != 0;
					SETTINGS_BY_PLAYER.put(playerId, new ChainSettings(max, vacuum));
				} catch (IllegalArgumentException ignored) {
					// 跳过坏行
				}
			}
		} catch (IOException e) {
			LOGGER.warn("读取连锁挖掘设置失败,本次使用默认值:{}", FILE, e);
		}
	}

	private static synchronized void save() {
		try {
			Files.createDirectories(FILE.getParent());
			List<String> lines = new ArrayList<>();
			lines.add("# 连锁挖掘设置:每行一个玩家 <UUID> <每次最多连锁数> <掉落物掉到脚下:0|1>");
			for (Map.Entry<UUID, ChainSettings> entry : SETTINGS_BY_PLAYER.entrySet()) {
				ChainSettings settings = entry.getValue();
				lines.add(entry.getKey() + " " + settings.max() + " " + (settings.vacuumToPlayer() ? 1 : 0));
			}
			Files.write(FILE, lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("保存连锁挖掘设置失败:{}", FILE, e);
		}
	}

	private static ChainSettings defaults() {
		return new ChainSettings(DEFAULT, DEFAULT_VACUUM);
	}

	public static synchronized ChainSettings get(ServerPlayer player) {
		load();
		return SETTINGS_BY_PLAYER.getOrDefault(player.getUUID(), defaults());
	}

	/** 设置并返回按范围收敛后的值。 */
	public static synchronized ChainSettings set(ServerPlayer player, int max, boolean vacuumToPlayer) {
		load();
		ChainSettings clamped = new ChainSettings(Math.max(MIN, Math.min(MAX, max)), vacuumToPlayer);
		SETTINGS_BY_PLAYER.put(player.getUUID(), clamped);
		save();
		return clamped;
	}
}
