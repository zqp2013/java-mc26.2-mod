package net.mile.chainminer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.mile.chainminer.payload.SyncSettingsPayload;
import net.mile.chainminer.payload.UndoPayload;
import net.mile.chainminer.payload.UpdateSettingsPayload;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChainMinerMod implements ModInitializer {
	public static final String MOD_ID = "chainminer";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 注册累计连锁挖掘统计 + 进度
		ChainMinerAdvancements.init();

		// 双向数据包注册(两侧都要注册编解码器)
		PayloadTypeRegistry.serverboundPlay().register(UpdateSettingsPayload.TYPE, UpdateSettingsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SyncSettingsPayload.TYPE, SyncSettingsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UndoPayload.TYPE, UndoPayload.CODEC);

		// 撤销最近一次连锁
		ServerPlayNetworking.registerGlobalReceiver(UndoPayload.TYPE,
				(payload, context) -> ChainMinerUndo.undo(context.server(), context.player()));

		// 客户端在设置界面保存新设置
		ServerPlayNetworking.registerGlobalReceiver(UpdateSettingsPayload.TYPE, (payload, context) -> {
			ChainSettings clamped = ChainMinerConfig.set(context.player(), payload.max(),
					payload.vacuumToPlayer(), payload.warnWrongTier());
			ServerPlayNetworking.send(context.player(), new SyncSettingsPayload(clamped.max(),
					clamped.vacuumToPlayer(), clamped.warnWrongTier()));
		});

		// 玩家进服时同步当前设置(设置持久化在 config/chainminer-settings.txt)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ChainSettings settings = ChainMinerConfig.get(handler.player);
			ServerPlayNetworking.send(handler.player, new SyncSettingsPayload(settings.max(),
					settings.vacuumToPlayer(), settings.warnWrongTier()));
		});

		ChainMining.register();

		LOGGER.info("连锁挖掘模组加载完毕:潜行挖掘连锁同类方块,Ctrl+右键打开设置界面!");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
