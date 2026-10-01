package net.mile.highlightdisplay.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 高亮显示的纯客户端设置(Ctrl+右键打开界面修改)。
 * 持久化到 config/highlightdisplay.properties。
 */
public final class HighlightConfig {
	public static final boolean DEFAULT_SHOW_ID = true;
	public static final boolean DEFAULT_SHOW_STATE = true;
	public static final boolean DEFAULT_SHOW_INFO = true;
	public static final boolean DEFAULT_SHOW_TIER = true;
	public static final boolean DEFAULT_SHOW_PROGRESS = true;

	private static final Logger LOGGER = LoggerFactory.getLogger("highlightdisplay");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("highlightdisplay.properties");

	private static volatile boolean showId = DEFAULT_SHOW_ID;
	private static volatile boolean showState = DEFAULT_SHOW_STATE;
	private static volatile boolean showInfo = DEFAULT_SHOW_INFO;
	private static volatile boolean showTier = DEFAULT_SHOW_TIER;
	private static volatile boolean showProgress = DEFAULT_SHOW_PROGRESS;

	private HighlightConfig() {
	}

	public static boolean isShowId() {
		return showId;
	}

	public static boolean isShowState() {
		return showState;
	}

	public static boolean isShowInfo() {
		return showInfo;
	}

	public static boolean isShowTier() {
		return showTier;
	}

	public static boolean isShowProgress() {
		return showProgress;
	}

	public static void setShowId(boolean value) {
		showId = value;
	}

	public static void setShowState(boolean value) {
		showState = value;
	}

	public static void setShowInfo(boolean value) {
		showInfo = value;
	}

	public static void setShowTier(boolean value) {
		showTier = value;
	}

	public static void setShowProgress(boolean value) {
		showProgress = value;
	}

	public static void load() {
		if (!Files.exists(FILE)) {
			return;
		}
		Properties properties = new Properties();
		try {
			properties.load(Files.newBufferedReader(FILE, StandardCharsets.UTF_8));
			showId = Boolean.parseBoolean(properties.getProperty("show_id", String.valueOf(DEFAULT_SHOW_ID)));
			showState = Boolean.parseBoolean(properties.getProperty("show_state", String.valueOf(DEFAULT_SHOW_STATE)));
			showInfo = Boolean.parseBoolean(properties.getProperty("show_info", String.valueOf(DEFAULT_SHOW_INFO)));
			showTier = Boolean.parseBoolean(properties.getProperty("show_tier", String.valueOf(DEFAULT_SHOW_TIER)));
			showProgress = Boolean.parseBoolean(properties.getProperty("show_progress", String.valueOf(DEFAULT_SHOW_PROGRESS)));
		} catch (IOException e) {
			LOGGER.warn("读取高亮显示设置失败,本次使用默认值:{}", FILE, e);
		}
	}

	public static void save() {
		Properties properties = new Properties();
		properties.setProperty("show_id", String.valueOf(showId));
		properties.setProperty("show_state", String.valueOf(showState));
		properties.setProperty("show_info", String.valueOf(showInfo));
		properties.setProperty("show_tier", String.valueOf(showTier));
		properties.setProperty("show_progress", String.valueOf(showProgress));
		try {
			Files.createDirectories(FILE.getParent());
			properties.store(Files.newBufferedWriter(FILE, StandardCharsets.UTF_8), " 高亮显示设置");
		} catch (IOException e) {
			LOGGER.warn("保存高亮显示设置失败:{}", FILE, e);
		}
	}

	/** 供设置界面一次性读取的开关清单 */
	public record Toggles(boolean id, boolean state, boolean info, boolean tier, boolean progress) {
	}

	public static Toggles current() {
		return new Toggles(showId, showState, showInfo, showTier, showProgress);
	}

	public static void apply(Toggles toggles) {
		showId = toggles.id();
		showState = toggles.state();
		showInfo = toggles.info();
		showTier = toggles.tier();
		showProgress = toggles.progress();
		save();
	}
}
