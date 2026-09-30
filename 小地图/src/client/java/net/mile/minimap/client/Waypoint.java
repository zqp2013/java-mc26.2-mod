package net.mile.minimap.client;

/** 一个地图标签(玩家标记或死亡点);createdGameDay = 创建时的游戏日(用于 50 游戏日进度) */
public record Waypoint(String dimension, int x, int y, int z, String name, int color, long createdGameDay) {

	public Waypoint(String dimension, int x, int y, int z, String name, int color) {
		this(dimension, x, y, z, name, color, 0L);
	}
}
