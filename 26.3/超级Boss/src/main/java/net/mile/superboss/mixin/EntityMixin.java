package net.mile.superboss.mixin;

import net.mile.superboss.Advancements;
import net.mile.superboss.BossState;
import net.mile.superboss.WardenBossBars;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Boss/召唤物被移除时的处理:
 * 1) 坚守者被移除(死亡清理/钻地消失/卸载)时清掉 Boss 血条;
 * 2) 三大 Boss 死亡 → 发进度 + 记录死亡时间(供"1 分钟内三杀"判定);
 * 3) 凋零召唤的凋灵骷髅死亡 → 必掉一个凋灵骷髅头颅并登记,供"捡头颅"进度用。
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

	@Inject(method = "remove", at = @At("TAIL"))
	private void superboss$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;

		if (self instanceof Warden warden) {
			WardenBossBars.remove(warden);
			if (reason == Entity.RemovalReason.KILLED && self.level() instanceof ServerLevel serverLevel) {
				BossState.wardenDeathAt = System.currentTimeMillis();
				superboss$grantInLevel(serverLevel, self, 128.0, "kill_warden");
			}
			return;
		}
		if (reason != Entity.RemovalReason.KILLED || !(self.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (self instanceof EnderDragon) {
			BossState.dragonDeathAt = System.currentTimeMillis();
			for (ServerPlayer player : serverLevel.players()) {
				Advancements.grant(player, "kill_dragon");
				// 击杀数走原版统计(只统计玩家亲手击杀)
				int kills = player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityTypes.ENDER_DRAGON));
				if (kills >= 5) {
					Advancements.grant(player, "kill_dragon_5");
				}
			}
		} else if (self instanceof WitherBoss) {
			BossState.witherDeathAt = System.currentTimeMillis();
			superboss$grantInLevel(serverLevel, self, 128.0, "kill_wither");
		} else if (self instanceof WitherSkeleton skeleton
				&& skeleton.entityTags().contains("superboss_summoned")) {
			// 凋零召唤的骷髅必掉头颅:记下掉落物实体,被玩家捡起时算进度
			ItemEntity skullDrop = new ItemEntity(serverLevel, skeleton.getX(), skeleton.getY(), skeleton.getZ(),
					new ItemStack(Items.WITHER_SKELETON_SKULL));
			serverLevel.addFreshEntity(skullDrop);
			BossState.TRACKED_SKULL_ITEMS.add(skullDrop.getId());
		}
	}

	@org.spongepowered.asm.mixin.Unique
	private static void superboss$grantInLevel(ServerLevel serverLevel, Entity at, double range, String path) {
		for (ServerPlayer player : serverLevel.players()) {
			if (range <= 0.0 || player.distanceToSqr(at) <= range * range) {
				Advancements.grant(player, path);
			}
		}
	}
}
