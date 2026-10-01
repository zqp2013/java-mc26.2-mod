package net.mile.superboss;

/**
 * 全部 Boss 强化数值都集中在这里,想调强度改这里即可。
 */
public final class SuperBossConfig {
	private SuperBossConfig() {
	}

	// ===== 超级末影龙 =====
	/** 最大生命(原版 200) */
	public static final double DRAGON_MAX_HEALTH = 500.0;
	/** 火球弹幕攻击间隔(刻,20 刻 = 1 秒) */
	public static final int DRAGON_FIREBALL_INTERVAL = 60;
	/** 每轮火球弹幕发射的火球数 */
	public static final int DRAGON_FIREBALL_COUNT = 3;
	/** 锁定玩家的范围 */
	public static final double DRAGON_FIREBALL_RANGE = 64.0;
	/** 弹雨技能间隔(刻,300 = 15 秒) */
	public static final int DRAGON_BULLET_RAIN_INTERVAL = 300;
	/** 每轮弹雨子弹总数 */
	public static final int DRAGON_BULLET_RAIN_COUNT = 2000;
	/** 子弹随机降落在玩家周围多少格内 */
	public static final double DRAGON_BULLET_RAIN_RADIUS = 50.0;
	/** 子弹能连续穿透几层方块(第 4 层才会挡住;中间隔了空气则重新计数) */
	public static final int DRAGON_BULLET_BLOCK_PIERCE = 3;
	/** 一轮弹雨在多少刻内陆续落下(40 = 2 秒) */
	public static final int DRAGON_BULLET_RAIN_DURATION = 40;
	/** 子弹下落速度(格/刻) */
	public static final double DRAGON_BULLET_FALL_SPEED = 1.5;
	/** 子弹从多高的地方开始落下 */
	public static final double DRAGON_BULLET_SPAWN_HEIGHT = 32.0;
	/** 每发子弹的伤害(1 点 = 半颗心) */
	public static final float DRAGON_BULLET_DAMAGE = 1.0F;
	/** 栖息(停在祭坛上)时超级龙息的判定间隔(刻,40 = 每 2 秒掷一次骰子) */
	public static final int DRAGON_SUPER_BREATH_INTERVAL = 40;
	/** 每次判定发射超级龙息的概率(0.10 = 10%) */
	public static final float DRAGON_SUPER_BREATH_CHANCE = 0.10F;
	/** 超级龙息直接命中的伤害(1 点 = 半颗心) */
	public static final float DRAGON_SUPER_BREATH_DAMAGE = 10.0F;
	/** 落点周围多少格内算被"打到"(会被清增益) */
	public static final double DRAGON_SUPER_BREATH_HIT_RADIUS = 3.0;
	/** 超级龙息残留毒云半径(原版龙息云 3 格) */
	public static final float DRAGON_SUPER_BREATH_CLOUD_RADIUS = 5.0F;
	/** 残留毒云持续时间(刻,600 = 30 秒) */
	public static final int DRAGON_SUPER_BREATH_CLOUD_DURATION = 600;
	/** 毒云里额外凋零效果的时长(刻) */
	public static final int DRAGON_SUPER_BREATH_WITHER_DURATION = 100;
	/** 末影水晶嵌进柱身的高度比例(0 = 柱底,1 = 柱顶;0.75 ≈ 露出地面那段的中部) */
	public static final double CRYSTAL_EMBED_FRACTION = 0.75;
	/** 末影龙每次栖息时补全被破坏的末影柱(只补黑曜石,不复活已死的水晶) */
	public static final boolean DRAGON_PILLAR_REPAIR_ON_PERCH = true;
	/** 召唤仆从的间隔(刻,240 = 12 秒) */
	public static final int DRAGON_SUMMON_INTERVAL = 240;
	/** 每波召唤:幻翼/末影螨/潜影贝各召几只 */
	public static final int DRAGON_SUMMON_PHANTOMS = 2;
	public static final int DRAGON_SUMMON_ENDERMITES = 4;
	public static final int DRAGON_SUMMON_SHULKERS = 2;
	/** 场上仆从存活上限(超了就不补召) */
	public static final int DRAGON_SUMMON_PHANTOM_CAP = 6;
	public static final int DRAGON_SUMMON_ENDERMITE_CAP = 12;
	public static final int DRAGON_SUMMON_SHULKER_CAP = 4;
	/** 仆从在目标玩家周围多少格内落地 */
	public static final double DRAGON_SUMMON_RADIUS = 10.0;
	/** 末影龙死亡时是否把还活着的仆从清场 */
	public static final boolean DRAGON_SUMMON_CLEAR_ON_DEATH = true;

	// ===== 强化凋零 =====
	/** 最大生命(原版 300) */
	public static final double WITHER_MAX_HEALTH = 800.0;
	/** 多久没受伤后开始回血(刻,200 刻 = 10 秒) */
	public static final int WITHER_REGEN_DELAY = 200;
	/** 每秒回复的最大生命百分比(0.005 = 0.5%) */
	public static final float WITHER_REGEN_PERCENT_PER_SECOND = 0.005F;
	/** 召唤凋灵骷髅的间隔(刻,400 = 20 秒) */
	public static final int WITHER_SUMMON_INTERVAL = 400;
	/** 每次召唤数量 */
	public static final int WITHER_SUMMON_COUNT = 5;
	/** 场上凋灵骷髅上限 */
	public static final int WITHER_SKELETON_CAP = 10;
	/** 一次齐射发射的头颅数 */
	public static final int WITHER_SKULL_VOLLEY_COUNT = 3;
	/** 骷髅头爆炸半径倍率(原版 1.0 格 → 2.0 格) */
	public static final float WITHER_SKULL_BLAST_MULTIPLIER = 2.0F;
	/** 骷髅头爆炸额外直接伤害(≈伤害翻倍) */
	public static final float WITHER_SKULL_EXTRA_DAMAGE = 6.0F;
	/** 骷髅头额外击退强度 */
	public static final float WITHER_SKULL_KNOCKBACK = 2.2F;

	// ===== 通用 =====
	/** 末影龙/凋零受到的爆炸伤害倍率(0.2 = 只吃 20%,即免疫 80%) */
	public static final float BOSS_EXPLOSION_DAMAGE_FACTOR = 0.2F;

	// ===== 强化坚守者 =====
	/** 最大生命(原版 500) */
	public static final double WARDEN_MAX_HEALTH = 1000.0;
	/** 血条可见范围(格) */
	public static final double WARDEN_BOSS_BAR_RANGE = 128.0;
	/** 音波攻击附加负面效果数量下限/上限(随机 1-5 种) */
	public static final int WARDEN_SONIC_EFFECT_MIN = 1;
	public static final int WARDEN_SONIC_EFFECT_MAX = 5;
	/** 负面效果时长(刻):10-20 秒随机 */
	public static final int WARDEN_SONIC_EFFECT_DURATION_BASE = 200;
	public static final int WARDEN_SONIC_EFFECT_DURATION_BONUS = 200;
	/** 受击触发"大运冲撞"的概率(0.15 = 15%) */
	public static final float WARDEN_CHARGE_CHANCE = 0.15F;
	/** 大运冲撞冷却(刻) */
	public static final int WARDEN_CHARGE_COOLDOWN = 60;
	/** 大运冲撞造成的伤害(21 点 = 10.5 颗心) */
	public static final float WARDEN_CHARGE_DAMAGE = 21.0F;
	/** 大运冲撞往正上方弹起的速度 */
	public static final double WARDEN_CHARGE_LAUNCH = 1.8;
}
