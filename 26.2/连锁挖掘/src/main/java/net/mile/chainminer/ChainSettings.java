package net.mile.chainminer;

/** 玩家的连锁挖掘设置。 */
public record ChainSettings(int max, boolean vacuumToPlayer, boolean warnWrongTier) {
}
