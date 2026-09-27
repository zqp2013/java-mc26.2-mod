package net.mile.chainminer.payload;

import net.mile.chainminer.ChainMinerMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端 → 客户端:同步连锁挖掘设置当前值。 */
public record SyncSettingsPayload(int max, boolean vacuumToPlayer) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SyncSettingsPayload> TYPE =
			new CustomPacketPayload.Type<>(ChainMinerMod.id("sync_settings"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SyncSettingsPayload> CODEC =
			StreamCodec.of(SyncSettingsPayload::write, SyncSettingsPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, SyncSettingsPayload payload) {
		buf.writeVarInt(payload.max);
		buf.writeBoolean(payload.vacuumToPlayer);
	}

	private static SyncSettingsPayload read(RegistryFriendlyByteBuf buf) {
		return new SyncSettingsPayload(buf.readVarInt(), buf.readBoolean());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
