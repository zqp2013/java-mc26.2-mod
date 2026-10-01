package net.mile.superfurnace;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 进度授予工具:所有进度 JSON 用 minecraft:impossible 条件,由代码点名发放。
 * 已发过的 award 会直接返回 false,不会重复弹提示。
 */
public final class Advancements {
	private Advancements() {
	}

	public static void grant(ServerPlayer player, String path) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		AdvancementHolder holder = server.getAdvancements().get(
				Identifier.fromNamespaceAndPath(SuperFurnaceMod.MOD_ID, path));
		if (holder == null) {
			return;
		}
		player.getAdvancements().award(holder, "unlocked");
	}
}
