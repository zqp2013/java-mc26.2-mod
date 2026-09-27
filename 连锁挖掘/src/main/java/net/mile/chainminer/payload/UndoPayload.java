package net.mile.chainminer.payload;

import net.mile.chainminer.ChainMinerMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端:撤销最近一次连锁。 */
public record UndoPayload() implements CustomPacketPayload {
	public static final UndoPayload INSTANCE = new UndoPayload();

	public static final CustomPacketPayload.Type<UndoPayload> TYPE =
			new CustomPacketPayload.Type<>(ChainMinerMod.id("undo"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UndoPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
