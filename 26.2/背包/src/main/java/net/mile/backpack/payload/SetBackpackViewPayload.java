package net.mile.backpack.payload;

import net.mile.backpack.BackpackMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端翻页/搜索 → 服务端同步视图(超级储存终端)。
 * 服务端按同样的规则标记可见槽,忽略不可见槽的点击;物品本身始终全量同步。
 */
public record SetBackpackViewPayload(int page, String search) implements CustomPacketPayload {

	public static final Type<SetBackpackViewPayload> TYPE =
			new Type<>(BackpackMod.id("set_view"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SetBackpackViewPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetBackpackViewPayload::page,
			ByteBufCodecs.STRING_UTF8, SetBackpackViewPayload::search,
			SetBackpackViewPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
