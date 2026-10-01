package net.mile.superboss;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 坚守者音波攻击的随机负面效果池:启动时从注册表筛出所有非增益效果。
 */
public final class SuperBossEffects {
	private static final List<Holder<MobEffect>> HARMFUL_EFFECTS = new ArrayList<>();

	static {
		BuiltInRegistries.MOB_EFFECT.asHolderIdMap().forEach(holder -> {
			if (!holder.value().isBeneficial()) {
				HARMFUL_EFFECTS.add(holder);
			}
		});
	}

	private SuperBossEffects() {
	}

	/**
	 * 给目标随机施加 min~max 种不重复的负面效果。
	 * 瞬间伤害必为 II 级(瞬间伤害2),其余 I 级。
	 * 返回实际施加的数量,以及是否包含瞬间伤害 II(用 {@link Result} 包装)。
	 */
	public static Result applyRandomHarmfulEffects(LivingEntity target, int min, int max) {
		if (HARMFUL_EFFECTS.isEmpty() || !target.isAlive()) {
			return new Result(0, false);
		}
		RandomSource random = target.getRandom();
		int count = Math.min(min + random.nextInt(max - min + 1), HARMFUL_EFFECTS.size());

		// 洗牌后取前 count 个,保证不重复
		List<Holder<MobEffect>> pool = new ArrayList<>(HARMFUL_EFFECTS);
		for (int i = pool.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			Holder<MobEffect> tmp = pool.get(i);
			pool.set(i, pool.get(j));
			pool.set(j, tmp);
		}

		int duration = SuperBossConfig.WARDEN_SONIC_EFFECT_DURATION_BASE
				+ random.nextInt(SuperBossConfig.WARDEN_SONIC_EFFECT_DURATION_BONUS + 1);
		boolean gotInstantHarmTwo = false;
		for (int i = 0; i < count; i++) {
			Holder<MobEffect> effect = pool.get(i);
			boolean instantHarm = effect.value() == net.minecraft.world.effect.MobEffects.INSTANT_DAMAGE.value();
			// 瞬间伤害统一按 II 级(瞬间伤害2)施加
			int amplifier = instantHarm ? 1 : 0;
			if (instantHarm) {
				gotInstantHarmTwo = true;
			}
			target.addEffect(new MobEffectInstance(effect, instantHarm ? 1 : duration, amplifier));
		}
		return new Result(count, gotInstantHarmTwo);
	}

	/** 一次音波的效果结算结果 */
	public record Result(int count, boolean instantHarmTwo) {
	}

}
