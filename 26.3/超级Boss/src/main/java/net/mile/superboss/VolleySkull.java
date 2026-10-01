package net.mile.superboss;

/**
 * 凋零齐射头颅的鸭子接口:由 WitherSkullMixin 实现,记录这颗头颅属于哪次齐射。
 */
public interface VolleySkull {
	void superboss$setVolleyId(int volleyId);

	int superboss$getVolleyId();
}
