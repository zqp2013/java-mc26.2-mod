package net.mile.superboss;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * 坚守者的 Boss 血条挂在静态弱引用表上,实体被移除时可跨类清理。
 */
public final class WardenBossBars {
	private static final Map<Warden, ServerBossEvent> BARS = new WeakHashMap<>();

	private WardenBossBars() {
	}

	public static ServerBossEvent getOrCreate(Warden warden) {
		return BARS.computeIfAbsent(warden, w -> new ServerBossEvent(
				Mth.createInsecureUUID(w.getRandom()),
				w.getDisplayName(),
				BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.PROGRESS));
	}

	public static void remove(Warden warden) {
		ServerBossEvent bar = BARS.remove(warden);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}
}
