package net.mile.minimap.client;

/** 一个地图标签(玩家标记或死亡点) */
public record Waypoint(String dimension, int x, int y, int z, String name, int color) {
}
