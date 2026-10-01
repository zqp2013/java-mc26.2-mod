package net.mile.minimap.client;

import java.util.UUID;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 小地图进度授予(单人游戏):所有进度 JSON 用 minecraft:impossible 条件,由代码点名发放。
 * 客户端线程不能直接碰服务端数据,丢到集成服务端线程执行。
 */
final class MinimapAdvancements {
	private MinimapAdvancements() {
	}

	static void grant(String path) {
		Minecraft mc = Minecraft.getInstance();
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) {
			return; // 多人服上没有本模组的进度数据,跳过
		}
		UUID uuid = mc.player.getUUID();
		server.execute(() -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!player.getUUID().equals(uuid)) {
					continue;
				}
				AdvancementHolder holder = server.getAdvancements().get(
						Identifier.fromNamespaceAndPath("minimap", path));
				if (holder != null) {
					player.getAdvancements().award(holder, "unlocked");
				}
				return;
			}
		});
	}
}
