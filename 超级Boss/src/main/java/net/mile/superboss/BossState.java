package net.mile.superboss;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * 进度/功能共用的跨类状态(都在服务端线程访问,用并发容器防多维度并发)。
 */
public final class BossState {
	/** 凋零齐射编号递增(0 = 不是超级凋零齐射里的头颅) */
	public static final java.util.concurrent.atomic.AtomicInteger VOLLEY_ID = new java.util.concurrent.atomic.AtomicInteger();

	/** 玩家 → (齐射编号 → 已被炸到的头颅数);集满 3 个发"把伤害吃满了" */
	public static final Map<UUID, Map<Integer, Integer>> VOLLEY_HITS = new ConcurrentHashMap<>();

	/** 凋零召唤的骷髅死后掉的头颅实体 id(被玩家捡起时计入进度) */
	public static final Set<Integer> TRACKED_SKULL_ITEMS = ConcurrentHashMap.newKeySet();

	/** 玩家 → 从召唤骷髅捡到的凋零头颅数 */
	public static final Map<UUID, Integer> SUMMONED_SKULL_PICKUPS = new ConcurrentHashMap<>();

	/** 玩家在柱子上累计挖的黑曜石数 */
	public static final Map<UUID, Integer> PILLAR_OBSIDIAN_MINED = new ConcurrentHashMap<>();

	/** 玩家是否已被告知"进入末地"过(重复进末地不重复发) */
	public static final Set<UUID> SEEN_END = ConcurrentHashMap.newKeySet();

	/** 三Boss死亡时间戳(毫秒),用于"1 分钟内同时击败"判定 */
	public static volatile long dragonDeathAt = 0L;
	public static volatile long witherDeathAt = 0L;
	public static volatile long wardenDeathAt = 0L;

	private BossState() {
	}

	/** 齐射头颅炸到玩家一次;集满齐射数发进度 */
	public static void recordVolleyHit(ServerPlayer player, int volleyId) {
		if (volleyId == 0) {
			return;
		}
		Map<Integer, Integer> hits = VOLLEY_HITS.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>());
		int now = hits.merge(volleyId, 1, Integer::sum);
		if (now >= 3) {
			hits.remove(volleyId);
			Advancements.grant(player, "wither_volley_full");
		}
	}

	/** 捡到凋零召唤骷髅掉的头颅;集满 3 个发进度 */
	public static void recordSummonedSkullPickup(ServerPlayer player, int amount) {
		int count = SUMMONED_SKULL_PICKUPS.merge(player.getUUID(), amount, Integer::sum);
		if (count >= 3) {
			Advancements.grant(player, "summoned_skulls_3");
		}
	}
}
