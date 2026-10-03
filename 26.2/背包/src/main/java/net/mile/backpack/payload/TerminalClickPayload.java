package net.mile.backpack.payload;

import net.mile.backpack.BackpackMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 其他容器界面(箱子/熔炉等)侧边终端面板的点击 → 服务端执行存取。
 * slot = 终端内容格真实下标;面板的页码/搜索只是客户端视图,服务端不关心。
 */
public record TerminalClickPayload(int slot, boolean right, boolean shift) implements CustomPacketPayload {

	public static final Type<TerminalClickPayload> TYPE =
			new Type<>(BackpackMod.id("terminal_click"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TerminalClickPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TerminalClickPayload::slot,
			ByteBufCodecs.BOOL, TerminalClickPayload::right,
			ByteBufCodecs.BOOL, TerminalClickPayload::shift,
			TerminalClickPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
