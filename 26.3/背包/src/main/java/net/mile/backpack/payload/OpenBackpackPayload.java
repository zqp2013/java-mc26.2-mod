package net.mile.backpack.payload;

import net.mile.backpack.BackpackMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端按 E → 服务端打开背包物品栏(服务端权威菜单) */
public record OpenBackpackPayload() implements CustomPacketPayload {

	public static final OpenBackpackPayload INSTANCE = new OpenBackpackPayload();

	public static final Type<OpenBackpackPayload> TYPE =
			new Type<>(BackpackMod.id("open"));

	public static final StreamCodec<io.netty.buffer.ByteBuf, OpenBackpackPayload> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
