package net.mile.chainminer.payload;

import net.mile.chainminer.ChainMinerMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端 → 服务端:更新连锁挖掘设置。 */
public record UpdateSettingsPayload(int max, boolean vacuumToPlayer) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<UpdateSettingsPayload> TYPE =
			new CustomPacketPayload.Type<>(ChainMinerMod.id("update_settings"));

	public static final StreamCodec<RegistryFriendlyByteBuf, UpdateSettingsPayload> CODEC =
			StreamCodec.of(UpdateSettingsPayload::write, UpdateSettingsPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, UpdateSettingsPayload payload) {
		buf.writeVarInt(payload.max);
		buf.writeBoolean(payload.vacuumToPlayer);
	}

	private static UpdateSettingsPayload read(RegistryFriendlyByteBuf buf) {
		return new UpdateSettingsPayload(buf.readVarInt(), buf.readBoolean());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
