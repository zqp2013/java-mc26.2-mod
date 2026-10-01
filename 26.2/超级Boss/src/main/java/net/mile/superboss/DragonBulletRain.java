package net.mile.superboss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 超级末影龙的"末影弹雨":每隔一段时间从天而降大量子弹,随机落在目标玩家周围。
 * 纯服务端模拟(不注册实体),视觉用粒子拖尾;命中造成 1 点伤害,
 * 并随机删除受害者(玩家)背包/装备栏/副手中一整格物品。
 */
public final class DragonBulletRain {
	private static final Logger LOGGER = LoggerFactory.getLogger("superboss");

	private static final List<PendingBullet> PENDING = new ArrayList<>();
	private static final List<Bullet> ACTIVE = new ArrayList<>();

	private DragonBulletRain() {
	}

	/** 开始一轮弹雨:围绕目标玩家随机布点,子弹陆续从天而降。 */
	public static void start(ServerLevel level, Player target, EnderDragon dragon) {
		target.playSound(SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.7F);
		RandomSource random = target.getRandom();
		for (int i = 0; i < SuperBossConfig.DRAGON_BULLET_RAIN_COUNT; i++) {
			// 均匀撒在半径 64 的圆盘内
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = SuperBossConfig.DRAGON_BULLET_RAIN_RADIUS * Math.sqrt(random.nextDouble());
			double x = target.getX() + Math.cos(angle) * distance;
			double z = target.getZ() + Math.sin(angle) * distance;
			double y = target.getY() + SuperBossConfig.DRAGON_BULLET_SPAWN_HEIGHT;
			int delay = random.nextInt(SuperBossConfig.DRAGON_BULLET_RAIN_DURATION + 1);
			PENDING.add(new PendingBullet(level, dragon, x, y, z, delay));
		}
		LOGGER.debug("超级末影龙在 {} 周围降下 {} 发末影弹", target.getName().getString(), SuperBossConfig.DRAGON_BULLET_RAIN_COUNT);
	}

	/** 每个服务器刻推进一次(由 SuperBossMod 的 ServerTickEvents 调用)。 */
	public static void tick() {
		if (!PENDING.isEmpty()) {
			Iterator<PendingBullet> iterator = PENDING.iterator();
			while (iterator.hasNext()) {
				PendingBullet pending = iterator.next();
				if (--pending.delay < 0) {
					ACTIVE.add(new Bullet(pending));
					iterator.remove();
				}
			}
		}
		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Bullet> iterator = ACTIVE.iterator();
		while (iterator.hasNext()) {
			if (iterator.next().step()) {
				iterator.remove();
			}
		}
	}

	/** 随机删除一整格物品(含护甲栏 36-39 和副手 40)。 */
	private static void deleteRandomSlot(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		RandomSource random = player.getRandom();
		List<Integer> filled = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (!inventory.getItem(i).isEmpty()) {
				filled.add(i);
			}
		}
		if (filled.isEmpty()) {
			return;
		}
		int slot = filled.get(random.nextInt(filled.size()));
		inventory.removeItem(slot, inventory.getItem(slot).getCount());
		player.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.6F);
		player.sendOverlayMessage(Component.literal("§5超级末影龙抹除了你的一格物品!"));
	}

	private static final class PendingBullet {
		final ServerLevel level;
		final EnderDragon dragon;
		final double x;
		final double y;
		final double z;
		int delay;

		PendingBullet(ServerLevel level, EnderDragon dragon, double x, double y, double z, int delay) {
			this.level = level;
			this.dragon = dragon;
			this.x = x;
			this.y = y;
			this.z = z;
			this.delay = delay;
		}
	}

	private static final class Bullet {
		final ServerLevel level;
		final EnderDragon dragon;
		double x;
		double y;
		double z;
		int age;
		int idleTicks;
		/** 当前已连续穿过的实心方块层数(遇到空气归零) */
		int consecutiveSolidBlocks;

		Bullet(PendingBullet pending) {
			this.level = pending.level;
			this.dragon = pending.dragon;
			this.x = pending.x;
			this.y = pending.y;
			this.z = pending.z;
		}

		/** @return true 表示子弹已消耗(命中/被挡/超时),应移除 */
		boolean step() {
			age++;
			if (age > 200) {
				return true;
			}
			double newY = y - SuperBossConfig.DRAGON_BULLET_FALL_SPEED;

			BlockPos tip = BlockPos.containing(x, y, z);
			if (!level.isLoaded(tip)) {
				// 玩家跑远导致区块卸载:悬停一会儿等区块,等不到就消失
				return ++idleTicks > 100;
			}
			idleTicks = 0;

			// 命中生物(检测框覆盖整段下落路径):打中第一个就消失
			AABB hitBox = new AABB(x - 0.4, newY - 0.6, z - 0.4, x + 0.4, y + 0.6, z + 0.4);
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, hitBox)) {
				if (living == dragon || !living.isAlive() || living.isSpectator()) {
					continue;
				}
				hit(living);
				return true;
			}

			// 逐格检查这一刻穿过的方块:连续第 4 层实心方块才会挡住子弹
			int endCellY = BlockPos.containing(x, newY, z).getY();
			for (int cellY = tip.getY() - 1; cellY >= endCellY; cellY--) {
				BlockPos cellPos = new BlockPos(tip.getX(), cellY, tip.getZ());
				BlockState state = level.getBlockState(cellPos);
				if (!state.getCollisionShape(level, cellPos).isEmpty()) {
					if (++consecutiveSolidBlocks > SuperBossConfig.DRAGON_BULLET_BLOCK_PIERCE) {
						level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
								x, cellY + 1.1, z, 3, 0.15, 0.1, 0.15, 0.01);
						return true;
					}
				} else {
					consecutiveSolidBlocks = 0;
				}
			}

			y = newY;

			// 掉出世界底部
			if (y < level.getMinY() - 16.0) {
				return true;
			}

			// 下落拖尾
			if (age % 3 == 0) {
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			return false;
		}

		private void hit(LivingEntity victim) {
			DamageSource source = dragon != null && dragon.isAlive()
					? level.damageSources().mobAttack(dragon)
					: level.damageSources().magic();
			boolean hurt = victim.hurtServer(level, source, SuperBossConfig.DRAGON_BULLET_DAMAGE);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, x, y, z, 8, 0.2, 0.2, 0.2, 0.1);
			if (hurt && victim instanceof ServerPlayer player) {
				deleteRandomSlot(player);
			}
		}
	}
}
