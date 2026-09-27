package net.mile.superboss;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuperBossMod implements ModInitializer {
	public static final String MOD_ID = "superboss";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 进世界时给玩家的署名提示 */
	private static final Component GREETING = Component.literal("[超级Boss] zym制造")
			.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAA00)));

	@Override
	public void onInitialize() {
		// 推进末影弹雨的下落模拟
		ServerTickEvents.END_SERVER_TICK.register(server -> DragonBulletRain.tick());

		// 进世界时提示 zym制造
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				handler.player.sendSystemMessage(GREETING));

		LOGGER.info("SuperBoss 加载完毕:超级末影龙、强化凋零与强化坚守者已就绪!");
	}
}
