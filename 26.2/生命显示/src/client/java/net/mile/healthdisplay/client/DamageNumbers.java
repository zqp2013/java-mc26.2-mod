package net.mile.healthdisplay.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 伤害数字:每帧对比生物血量快照,检测到血量下降就在它头上冒一个上飘的伤害数字。
 * 纯客户端(生物血量本来就同步到客户端),走 gizmo 文字通道,零 Mixin。
 * 不只是玩家攻击——箭、扫击、摔落、岩浆,任何来源的掉血都会冒数字。
 */
final class DamageNumbers {
	/** 数字存活时间(毫秒) */
	private static final long LIFE_MS = 800;
	/** 上飘高度(格) */
	private static final float RISE = 0.7F;
	/** 字号(文字世界高 ≈ scale*9/16) */
	private static final float SCALE = 0.25F;
	/** 最远追踪/显示距离(格) */
	private static final double MAX_DISTANCE = 48.0;
	private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;
	/** 同屏数字上限,防刷屏 */
	private static final int MAX_NUMBERS = 40;
	/** 大伤害(≥8)用金色显示 */
	private static final float BIG_HIT = 8.0F;

	/** 上一帧每个生物的血量快照(实体id → 血量) */
	private static final Map<Integer, Float> LAST_HEALTH = new HashMap<>();
	private static final List<Pop> ACTIVE = new ArrayList<>();
	private static final Random RANDOM = new Random();

	private DamageNumbers() {
	}

	/** 一次冒出的伤害数字 */
	private static final class Pop {
		final Vec3 start;
		final Vec3 drift;
		final String text;
		final int color;
		final long born;

		Pop(Vec3 start, Vec3 drift, String text, int color, long born) {
			this.start = start;
			this.drift = drift;
			this.text = text;
			this.color = color;
			this.born = born;
		}
	}

	/** 每帧调用:对比血量快照,检测到下降就生成伤害数字 */
	static void track(ClientLevel level, Player self, Vec3 cameraPos) {
		Map<Integer, Float> current = new HashMap<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || living == self || living instanceof ArmorStand) {
				continue;
			}
			float health = living.getHealth();
			if (health <= 0.0F) {
				continue;
			}
			Float prev = LAST_HEALTH.get(entity.getId());
			if (prev != null && prev > health + 0.01F
					&& living.distanceToSqr(cameraPos.x, cameraPos.y, cameraPos.z) <= MAX_DISTANCE_SQ) {
				spawn(living, prev - health);
			}
			current.put(entity.getId(), health);
		}
		LAST_HEALTH.clear();
		LAST_HEALTH.putAll(current);
	}

	private static void spawn(LivingEntity living, float damage) {
		if (ACTIVE.size() >= MAX_NUMBERS) {
			ACTIVE.remove(0);
		}
		// 出现点在生物眼睛高度,带一点随机水平偏移,连击时数字不叠在一起
		Vec3 start = new Vec3(living.getX(), living.getEyeY(), living.getZ());
		Vec3 drift = new Vec3((RANDOM.nextFloat() - 0.5F) * 0.3F, 0.0F, (RANDOM.nextFloat() - 0.5F) * 0.3F);
		int color = damage >= BIG_HIT ? 0xFFFFAA00 : 0xFFFFFFFF;
		ACTIVE.add(new Pop(start, drift, HealthBars.formatHealth(damage), color, System.currentTimeMillis()));
	}

	/** 每帧调用:把活着的数字画出来(自动过期消失) */
	static void render(DrawableGizmoPrimitives primitives, CameraRenderState camera) {
		long now = System.currentTimeMillis();
		Iterator<Pop> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Pop pop = it.next();
			float p = (now - pop.born) / (float) LIFE_MS;
			if (p >= 1.0F) {
				it.remove();
				continue;
			}
			double dx = pop.start.x - camera.pos.x;
			double dy = pop.start.y - camera.pos.y;
			double dz = pop.start.z - camera.pos.z;
			if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) {
				continue; // 太远不画,但保留着,走近了还能看见
			}
			float ease = 1.0F - (1.0F - p) * (1.0F - p); // 起快后慢的上飘
			Vec3 pos = new Vec3(
					pop.start.x + pop.drift.x * p,
					pop.start.y + RISE * ease,
					pop.start.z + pop.drift.z * p);
			primitives.addText(pos, pop.text,
					TextGizmo.Style.forColorAndCentered(pop.color).withScale(SCALE));
		}
	}
}
