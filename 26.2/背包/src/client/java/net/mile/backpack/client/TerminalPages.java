package net.mile.backpack.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

import net.mile.backpack.BackpackType;

/**
 * 终端页码记忆:关掉界面前在第几页,下次打开还在第几页(重启游戏也记得)。
 * E 界面(BackpackScreen)和箱子/熔炉等界面的侧边面板共用同一份记忆。
 * 存 config/backpack_terminal_page.properties,按档位名分条。
 */
public final class TerminalPages {

	private static final Path FILE =
			FabricLoader.getInstance().getConfigDir().resolve("backpack_terminal_page.properties");
	private static final Properties SAVED = new Properties();
	private static boolean loaded = false;

	private TerminalPages() {
	}

	public static int load(BackpackType type) {
		synchronized (SAVED) {
			readFile();
			try {
				return Math.max(0, Integer.parseInt(SAVED.getProperty(type.name(), "0")));
			} catch (NumberFormatException ignored) {
				return 0;
			}
		}
	}

	public static void save(BackpackType type, int page) {
		synchronized (SAVED) {
			readFile();
			SAVED.setProperty(type.name(), String.valueOf(Math.max(0, page)));
			try {
				Files.createDirectories(FILE.getParent());
				SAVED.store(Files.newOutputStream(FILE), "backpack terminal last page");
			} catch (IOException ignored) {
				// 记不住页码不致命
			}
		}
	}

	private static void readFile() {
		if (loaded) {
			return;
		}
		loaded = true;
		if (Files.exists(FILE)) {
			try (var in = Files.newInputStream(FILE)) {
				SAVED.load(in);
			} catch (IOException ignored) {
			}
		}
	}
}
