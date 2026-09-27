package net.mile.healthdisplay.client;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * 生物头顶血条:走原版 gizmo 图元通道(addQuad 彩色四边形 + addText 自动面向相机的文字),
 * 纯几何提交,零 Mixin。
 * 血条 = 半透明黑背景 + 按血量比例的彩色填充(满血绿→空血红,与原版耐久条同款 HSV 渐变),
 * 中间写着 当前生命/最大生命。
 */
final class HealthBars {
	/** 最远显示距离(格) */
	private static final double MAX_DISTANCE = 32.0;
	private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;
	/** 血条高度(世界单位) */
	private static final float BAR_HEIGHT = 0.13F;
	/** 血条中心离头顶的高度 */
	private static final double HEAD_OFFSET = 0.30;
	/** 背景色(带 alpha → 进半透明组) */
	private static final int BACKGROUND_COLOR = 0xB4000000;
	/** 血量文字缩放(文字世界高 ≈ scale*9/16,0.16 → 约 0.09,能舒服地塞进 0.13 高的血条) */
	private static final float TEXT_SCALE = 0.16F;
	/** 文字往相机方向挪一点,确保画在血条四边形前面(同深度会打架被盖住) */
	private static final double TEXT_LIFT = 0.03;

	private HealthBars() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		CameraRenderState camera = context.levelState().cameraRenderState;
		if (level == null || !camera.initialized) {
			return;
		}

		// 相机的右/上方向:血条永远正对着玩家(广告牌)
		Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera.orientation);
		Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera.orientation);

		DrawableGizmoPrimitives primitives = new DrawableGizmoPrimitives();

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || living == minecraft.player) {
				continue;
			}
			if (living.isRemoved() || !living.isAlive() || living.isInvisible()) {
				continue;
			}
			if (living instanceof ArmorStand) {
				continue; // 盔甲架不是生物
			}
			float maxHealth = living.getMaxHealth();
			float health = living.getHealth();
			if (maxHealth <= 0.0F || health <= 0.0F) {
				continue;
			}
			if (living.distanceToSqr(camera.pos.x, camera.pos.y, camera.pos.z) > MAX_DISTANCE_SQ) {
				continue;
			}

			AABB box = living.getBoundingBox();
			Vec3 center = new Vec3(living.getX(), box.maxY + HEAD_OFFSET, living.getZ());
			float width = Mth.clamp(living.getBbWidth() * 1.35F, 0.9F, 2.2F);
			float fraction = Mth.clamp(health / maxHealth, 0.0F, 1.0F);

			// 背景板(相机朝向的矩形)
			Vec3 halfRight = new Vec3(right.x * width * 0.5, right.y * width * 0.5, right.z * width * 0.5);
			Vec3 halfUp = new Vec3(up.x * BAR_HEIGHT * 0.5, up.y * BAR_HEIGHT * 0.5, up.z * BAR_HEIGHT * 0.5);
			Vec3 bottomLeft = center.subtract(halfRight).subtract(halfUp);
			Vec3 bottomRight = center.add(halfRight).subtract(halfUp);
			Vec3 topRight = center.add(halfRight).add(halfUp);
			Vec3 topLeft = center.subtract(halfRight).add(halfUp);
			primitives.addQuad(bottomLeft, bottomRight, topRight, topLeft, BACKGROUND_COLOR);

			// 彩色填充:从左往右按血量比例,颜色绿→红(与原版耐久条同款 HSV 渐变)
			Vec3 fillRight = new Vec3(right.x * width * fraction, right.y * width * fraction, right.z * width * fraction);
			Vec3 fillBottomRight = bottomLeft.add(fillRight);
			Vec3 fillTopRight = topLeft.add(fillRight);
			int fillColor = 0xFF000000 | Mth.hsvToRgb(fraction / 3.0F, 0.85F, 1.0F);
			primitives.addQuad(bottomLeft, fillBottomRight, fillTopRight, topLeft, fillColor);

			// 血量文字(自动面向相机并居中):往相机方向抬高一点点,保证画在血条前面不被盖住
			Vec3 toCamera = new Vec3(camera.pos.x - center.x, camera.pos.y - center.y, camera.pos.z - center.z)
					.normalize();
			Vec3 textPos = center.add(toCamera.scale(TEXT_LIFT));
			primitives.addText(textPos, (int) Math.ceil(health) + "/" + (int) maxHealth,
					TextGizmo.Style.forColorAndCentered(0xFFFFFFFF).withScale(TEXT_SCALE));
		}

		// false = 正常深度测试:隔着墙看不到(不作弊)
		primitives.submit(context.submitNodeCollector(), camera, false);
	}
}
