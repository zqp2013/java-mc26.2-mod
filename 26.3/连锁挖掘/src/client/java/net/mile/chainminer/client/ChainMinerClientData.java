package net.mile.chainminer.client;

import net.mile.chainminer.ChainMinerConfig;

/** 客户端缓存的连锁挖掘设置(由服务端同步)。 */
public final class ChainMinerClientData {
	private static volatile int maxBlocks = ChainMinerConfig.DEFAULT;
	private static volatile boolean vacuumToPlayer = ChainMinerConfig.DEFAULT_VACUUM;
	private static volatile boolean warnWrongTier = ChainMinerConfig.DEFAULT_WARN_TIER;

	private ChainMinerClientData() {
	}

	public static int getMax() {
		return maxBlocks;
	}

	public static void setMax(int value) {
		maxBlocks = Math.max(ChainMinerConfig.MIN, Math.min(ChainMinerConfig.MAX, value));
	}

	public static boolean isVacuumToPlayer() {
		return vacuumToPlayer;
	}

	public static void setVacuumToPlayer(boolean value) {
		vacuumToPlayer = value;
	}

	public static boolean isWarnWrongTier() {
		return warnWrongTier;
	}

	public static void setWarnWrongTier(boolean value) {
		warnWrongTier = value;
	}
}
