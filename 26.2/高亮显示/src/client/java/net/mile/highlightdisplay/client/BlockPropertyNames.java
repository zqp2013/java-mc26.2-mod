package net.mile.highlightdisplay.client;

import java.util.Map;

/**
 * 常见方块状态属性的中文映射(标签 + 常见取值)。
 * 没映射到的属性/取值按原样显示,不会漏信息。
 */
final class BlockPropertyNames {
	private BlockPropertyNames() {
	}

	/** 属性名 → 中文标签 */
	static final Map<String, String> LABELS = Map.ofEntries(
			Map.entry("age", "生长阶段"),
			Map.entry("stage", "生长阶段"),
			Map.entry("hatch", "孵化进度"),
			Map.entry("eggs", "蛋数"),
			Map.entry("power", "红石强度"),
			Map.entry("powered", "已激活"),
			Map.entry("lit", "已点燃"),
			Map.entry("extinguished", "已熄灭"),
			Map.entry("open", "已打开"),
			Map.entry("occupied", "被占用"),
			Map.entry("part", "部位"),
			Map.entry("facing", "朝向"),
			Map.entry("facing_direction", "朝向"),
			Map.entry("horizontal_facing", "朝向"),
			Map.entry("vertical_direction", "朝向"),
			Map.entry("axis", "朝向轴"),
			Map.entry("half", "上下半"),
			Map.entry("waterlogged", "含水"),
			Map.entry("shape", "形状"),
			Map.entry("type", "类型"),
			Map.entry("delay", "延迟"),
			Map.entry("locked", "已锁定"),
			Map.entry("mode", "模式"),
			Map.entry("inverted", "反相"),
			Map.entry("rotation", "旋转"),
			Map.entry("layers", "层数"),
			Map.entry("bites", "已食用"),
			Map.entry("hanging", "悬挂"),
			Map.entry("attached", "已附着"),
			Map.entry("disarmed", "已拆引信"),
			Map.entry("suspended", "悬空"),
			Map.entry("extended", "已伸出"),
			Map.entry("triggered", "已触发"),
			Map.entry("conditional", "有条件"),
			Map.entry("automatic", "自动"),
			Map.entry("in_wall", "贴墙"),
			Map.entry("bottom", "贴地"),
			Map.entry("stable", "稳固"),
			Map.entry("distance", "距离"),
			Map.entry("persistent", "常驻"),
			Map.entry("distance_from_hole", "距洞口"),
			Map.entry("signal_fire", "烟火信号"),
			Map.entry("north", "北侧"),
			Map.entry("south", "南侧"),
			Map.entry("east", "东侧"),
			Map.entry("west", "西侧"),
			Map.entry("up", "顶侧"),
			Map.entry("down", "底侧"),
			Map.entry("drag_down", "向下拖拽"),
			Map.entry("can_summon", "可召唤"),
			Map.entry("has_record", "正在播放唱片"),
			Map.entry("has_bottle_0", "瓶子1"),
			Map.entry("has_bottle_1", "瓶子2"),
			Map.entry("has_bottle_2", "瓶子3"),
			Map.entry("bloom", "绽放"),
			Map.entry("tilted", "已倾斜"),
			Map.entry("unstable", "易碎"),
			Map.entry("berries", "结果"),
			Map.entry("short", "绷紧"),
			Map.entry("cheesy", "奶酪化"),
			Map.entry("slot_0_occupied", "卡槽1"),
			Map.entry("slot_1_occupied", "卡槽2"),
			Map.entry("candles", "蜡烛数"),
			Map.entry("lit_by_player", "玩家点燃"),
			Map.entry("cracked", "已开裂"),
			Map.entry("crafting", "合成中"),
			Map.entry("orientation", "朝向"));

	/** 取值 → 中文 */
	static final Map<String, String> VALUES = Map.ofEntries(
			Map.entry("true", "是"),
			Map.entry("false", "否"),
			Map.entry("north", "北"),
			Map.entry("south", "南"),
			Map.entry("east", "东"),
			Map.entry("west", "西"),
			Map.entry("up", "上"),
			Map.entry("down", "下"),
			Map.entry("side", "侧面"),
			Map.entry("none", "无"),
			Map.entry("upper", "上半"),
			Map.entry("lower", "下半"),
			Map.entry("top", "顶"),
			Map.entry("bottom", "底"),
			Map.entry("left", "左"),
			Map.entry("right", "右"),
			Map.entry("head", "床头"),
			Map.entry("foot", "床尾"),
			Map.entry("normal", "普通"),
			Map.entry("sticky", "粘性"),
			Map.entry("compare", "比较"),
			Map.entry("subtract", "减法"),
			Map.entry("ceiling", "顶置"),
			Map.entry("floor", "地面"),
			Map.entry("wall", "壁挂"),
			Map.entry("single", "单段"),
			Map.entry("inner", "内角"),
			Map.entry("outer", "外角"),
			Map.entry("straight", "直行"),
			Map.entry("x", "东西向"),
			Map.entry("y", "南北向"),
			Map.entry("z", "上下向"),
			Map.entry("true_south", "正南"),
			Map.entry("true_north", "正北"),
			Map.entry("true_east", "正东"),
			Map.entry("true_west", "正西"));

	static String label(String name) {
		return LABELS.getOrDefault(name, name);
	}

	static String value(String raw) {
		return VALUES.getOrDefault(raw, raw);
	}
}
