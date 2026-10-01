package net.mile.highlightdisplay.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 注视目标信息面板:屏幕顶部中间。
 * 看方块 → 名称 + ID + 全部方块状态(中文) + 坐标/距离/接收红石信号/方块实体/生物群系;
 * 看实体 → 名称 + ID + 生命值/护甲/着火/速度/坐标。
 * 没有任何注视目标时整个面板隐藏。
 */
class TargetInfoPanel implements HudElement {
	private static final int NAME_COLOR = 0xFFFFFFFF;
	private static final int ID_COLOR = 0xFFA8A8A8;
	private static final int PROP_COLOR = 0xFF7BDFFF;
	private static final int INFO_COLOR = 0xFFFFEE88;
	private static final int BG_COLOR = 0xA0000000;

	private static final int TOP_Y = 4;
	private static final int TEXT_LINE_HEIGHT = 11;
	private static final int ICON_LINE_HEIGHT = 17;
	/** 小字号行的行高(缩小到 0.75 后约 7px + 1px 间距) */
	private static final int SMALL_LINE_HEIGHT = 8;
	/** 进度条行高(条 5px + 上下各 1px 间距) */
	private static final int BAR_LINE_HEIGHT = 7;
	/** 图标与文字的间距 */
	private static final int ICON_TEXT_GAP = 3;
	/** 信息行的文字缩放(比默认字号小一圈) */
	private static final float SMALL_SCALE = 0.75F;
	/** 信息行每行最多几条(用户要求 2-3 条竖排) */
	private static final int MAX_TOKENS_PER_LINE = 3;
	/** 小字行最大宽度(按缩放后像素算,超出提前换行) */
	private static final int MAX_SMALL_LINE_WIDTH = 250;
	private static final String SEPARATOR = " · ";

	/** 注视探测距离(格),比原版准星的 4.5 格远 */
	private static final double PICK_RANGE = 32.0;

	/** 这些整型属性按 "当前/最大" 展示生长进度 */
	private static final Set<String> GROWTH_INT_PROPERTIES = Set.of("age", "stage", "hatch");

	/** small = 用 0.75 缩放的小字渲染(名称行除外);bar >= 0 时在文字下画一根进度条 */
	private record Line(String text, int color, ItemStack icon, boolean small, float bar) {
	}

	/** 正在挖的方块位置与已挖 tick 数(目标/松开左键就重置) */
	private BlockPos miningPos;
	private float miningTicks;

	@Override
	public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		if (minecraft.gui.screen() != null) {
			return; // 开着背包/聊天等界面时不显示,避免残留过期信息
		}
		HitResult hit = pick(minecraft, deltaTracker);
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}
		List<Line> lines = switch (hit.getType()) {
			case BLOCK -> blockLines(minecraft, (BlockHitResult) hit, deltaTracker.getRealtimeDeltaTicks());
			case ENTITY -> entityLines((EntityHitResult) hit, minecraft.font);
			default -> List.of();
		};
		if (lines.isEmpty()) {
			return;
		}
		render(gui, minecraft.font, lines);
	}

	/**
	 * 自己的注视射线:最远 PICK_RANGE 格(原版准星只有 4.5/5 格)。
	 * 方块与实体各探测一次,取离眼睛更近的那个。
	 */
	private HitResult pick(Minecraft minecraft, DeltaTracker deltaTracker) {
		Player player = minecraft.player;
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(partialTick).scale(PICK_RANGE));

		BlockHitResult blockHit = minecraft.level.clip(
				new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, new AABB(eye, end).inflate(1.0),
				target -> target != player && target.isAlive() && !target.isSpectator() && target.isPickable(), 0.0);

		if (entityHit != null && (blockHit.getType() == HitResult.Type.MISS
				|| entityHit.getLocation().distanceToSqr(eye) < blockHit.getLocation().distanceToSqr(eye))) {
			return entityHit;
		}
		return blockHit;
	}

	// ---------- 方块 ----------

	private List<Line> blockLines(Minecraft minecraft, BlockHitResult hit, float deltaTicks) {
		ClientLevel level = minecraft.level;
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();

		List<Line> lines = new ArrayList<>();
		ItemStack icon = new ItemStack(block.asItem());
		lines.add(new Line(block.getName().getString(), NAME_COLOR, icon.isEmpty() ? null : icon, false, -1.0F));
		if (HighlightConfig.isShowId()) {
			lines.add(new Line(String.valueOf(BuiltInRegistries.BLOCK.getKey(block)), ID_COLOR, null, true, -1.0F));
		}
		if (HighlightConfig.isShowState()) {
			lines.addAll(propertyLines(minecraft.font, state, block));
		}
		if (HighlightConfig.isShowTier()) {
			Line tier = tierLine(state);
			if (tier != null) {
				lines.add(tier);
			}
		}
		if (HighlightConfig.isShowInfo()) {
			lines.addAll(packLines(minecraft.font, blockInfoTokens(minecraft, level, hit, pos, state), INFO_COLOR));
		}
		if (HighlightConfig.isShowProgress()) {
			Line progress = miningProgressLine(minecraft, level, pos, state, deltaTicks);
			if (progress != null) {
				lines.add(progress);
			}
		}
		return lines;
	}

	/** 挖掘等级行:按方块需求的工具等级画对应镐子图标(如铁镐及以上显示铁镐) */
	private Line tierLine(BlockState state) {
		ItemStack icon;
		String label;
		if (state.is(net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL)) {
			icon = new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
			label = "钻石镐及以上";
		} else if (state.is(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL)) {
			icon = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
			label = "铁镐及以上";
		} else if (state.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL)) {
			icon = new ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE);
			label = "石镐及以上";
		} else if (state.requiresCorrectToolForDrops()) {
			icon = new ItemStack(net.minecraft.world.item.Items.WOODEN_PICKAXE);
			label = "木镐及以上";
		} else {
			return null; // 手挖也掉落,不显示等级行
		}
		return new Line("挖掘等级 " + label, INFO_COLOR, icon, true, -1.0F);
	}

	/** 挖掘进度行:挖同一格时累计,换目标或松手重置;显示 已挖秒数/总共要几秒/百分比 + 进度条 */
	private Line miningProgressLine(Minecraft minecraft, ClientLevel level, BlockPos pos, BlockState state, float deltaTicks) {
		boolean attacking = minecraft.options.keyAttack.isDown();
		// 探测距离有 32 格,但原版只能挖 4.5 格内的方块:够不着的方块按住左键也挖不动,
		// 进度条不能走(修复:对远处方块也触发进度条的 bug)
		if (!attacking || !canReach(minecraft, pos)) {
			this.miningPos = null;
			this.miningTicks = 0.0F;
			return null;
		}
		if (!pos.equals(this.miningPos)) {
			this.miningPos = pos;
			this.miningTicks = 0.0F;
		}
		float perTick = state.getDestroyProgress(minecraft.player, level, pos);
		if (perTick <= 0.0F) {
			return null; // 挖不动的方块不显示
		}
		this.miningTicks += deltaTicks;
		float totalTicks = 1.0F / perTick;
		float fraction = Math.min(1.0F, this.miningTicks / totalTicks);
		String text = String.format(Locale.ROOT, "挖掘进度 %.1f秒/%.1f秒 已挖%.0f%%",
				this.miningTicks / 20.0F, totalTicks / 20.0F, fraction * 100.0F);
		return new Line(text, INFO_COLOR, null, true, fraction);
	}

	/** 眼睛到方块包围盒的最近距离是否在原版可交互范围内(生存约 4.5 格) */
	private boolean canReach(Minecraft minecraft, BlockPos pos) {
		Vec3 eye = minecraft.player.getEyePosition();
		double dx = Math.max(Math.max(pos.getX() - eye.x, 0.0), eye.x - (pos.getX() + 1));
		double dy = Math.max(Math.max(pos.getY() - eye.y, 0.0), eye.y - (pos.getY() + 1));
		double dz = Math.max(Math.max(pos.getZ() - eye.z, 0.0), eye.z - (pos.getZ() + 1));
		double range = minecraft.player.blockInteractionRange();
		return dx * dx + dy * dy + dz * dz <= range * range;
	}

	/** 把全部方块状态格式化成中文,每行竖排 2-3 条 */
	private List<Line> propertyLines(Font font, BlockState state, Block block) {
		Collection<Property<?>> properties = state.getProperties();
		if (properties.isEmpty()) {
			return List.of();
		}

		List<String> tokens = new ArrayList<>(properties.size());
		for (Property<?> property : properties) {
			tokens.add(formatProperty(state, block, property));
		}
		return packLines(font, tokens, PROP_COLOR);
	}

	/** 小字行打包:每行最多 MAX_TOKENS_PER_LINE 条,太长提前换行 */
	private List<Line> packLines(Font font, List<String> tokens, int color) {
		List<Line> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int count = 0;
		for (String token : tokens) {
			if (current.isEmpty()) {
				current.append(token);
				count = 1;
				continue;
			}
			String candidate = current + SEPARATOR + token;
			if (count + 1 > MAX_TOKENS_PER_LINE || font.width(candidate) * SMALL_SCALE > MAX_SMALL_LINE_WIDTH) {
				lines.add(new Line(current.toString(), color, null, true, -1.0F));
				current = new StringBuilder(token);
				count = 1;
			} else {
				current = new StringBuilder(candidate);
				count++;
			}
		}
		if (!current.isEmpty()) {
			lines.add(new Line(current.toString(), color, null, true, -1.0F));
		}
		return lines;
	}

	private <T extends Comparable<T>> String formatProperty(BlockState state, Block block, Property<T> property) {
		String name = property.getName();
		String label = BlockPropertyNames.label(name);
		String rawValue = property.getName(state.getValue(property));

		if (property instanceof IntegerProperty intProperty) {
			int value = state.getValue(intProperty);
			int max = intProperty.getPossibleValues().getLast();
			if (block instanceof CropBlock crop && "age".equals(name)) {
				// 农作物:生长阶段 + 是否成熟
				return label + " " + value + "/" + max + (crop.isMaxAge(state) ? "(已成熟)" : "(生长中)");
			}
			if (GROWTH_INT_PROPERTIES.contains(name) && max > 1) {
				// 下界疣/可可豆/树苗等:带进度的整型属性
				return label + " " + value + "/" + max;
			}
		}
		return label + " " + BlockPropertyNames.value(rawValue);
	}

	private List<String> blockInfoTokens(Minecraft minecraft, ClientLevel level, BlockHitResult hit, BlockPos pos, BlockState state) {
		List<String> parts = new ArrayList<>();
		parts.add("坐标 " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
		parts.add("距离 " + String.format(Locale.ROOT, "%.1f",
				hit.getLocation().distanceTo(minecraft.player.getEyePosition())));
		parts.add("接收红石信号 " + level.getBestNeighborSignal(pos));
		if (state.hasBlockEntity()) {
			BlockEntity blockEntity = level.getBlockEntity(pos);
			if (blockEntity != null) {
				parts.add("方块实体 " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()));
			}
		}
		level.getBiomeManager().getBiome(pos).unwrapKey()
				.ifPresent(key -> parts.add("生物群系 " + key.identifier().getPath()));
		return parts;
	}

	// ---------- 实体 ----------

	private List<Line> entityLines(EntityHitResult hit, Font font) {
		Entity entity = hit.getEntity();
		List<Line> lines = new ArrayList<>();
		lines.add(new Line(entity.getDisplayName().getString(), NAME_COLOR, null, false, -1.0F));
		lines.add(new Line(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())), ID_COLOR, null, true, -1.0F));

		List<String> parts = new ArrayList<>();
		if (entity instanceof LivingEntity living) {
			parts.add("生命值 " + (int) living.getHealth() + "/" + (int) living.getMaxHealth());
			if (living.getArmorValue() > 0) {
				parts.add("护甲 " + living.getArmorValue());
			}
		}
		parts.add("着火 " + (entity.isOnFire() ? "是" : "否"));
		Vec3 motion = entity.getDeltaMovement();
		parts.add("速度 " + String.format(Locale.ROOT, "%.1f", Math.sqrt(motion.x * motion.x + motion.z * motion.z)));
		BlockPos pos = entity.blockPosition();
		parts.add("坐标 " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
		lines.addAll(packLines(font, parts, INFO_COLOR));
		return lines;
	}

	// ---------- 渲染 ----------

	private void render(GuiGraphicsExtractor gui, Font font, List<Line> lines) {
		int screenWidth = gui.guiWidth();

		// 面板宽 = 最宽的一行(含图标,图标行文字按原字号算;小字行按缩放后的宽度算)
		int panelWidth = 0;
		for (Line line : lines) {
			int w = line.icon() != null
					? 16 + ICON_TEXT_GAP + font.width(line.text())
					: (int) (font.width(line.text()) * (line.small() ? SMALL_SCALE : 1.0F));
			panelWidth = Math.max(panelWidth, w);
		}

		int totalHeight = 0;
		for (Line line : lines) {
			totalHeight += line.icon() != null ? ICON_LINE_HEIGHT : line.small() ? SMALL_LINE_HEIGHT : TEXT_LINE_HEIGHT;
			if (line.bar() >= 0.0F) {
				totalHeight += BAR_LINE_HEIGHT;
			}
		}

		// 背景板(屏幕顶部中间)
		gui.fill(screenWidth / 2 - panelWidth / 2 - 5, TOP_Y - 2,
				screenWidth / 2 + panelWidth / 2 + 5, TOP_Y + totalHeight + 2, BG_COLOR);

		int y = TOP_Y;
		for (Line line : lines) {
			ItemStack icon = line.icon();
			if (icon != null) {
				// 名称行:图标 + 正常字号
				int lineW = 16 + ICON_TEXT_GAP + font.width(line.text());
				int startX = screenWidth / 2 - lineW / 2;
				gui.item(icon, startX, y);
				gui.itemDecorations(font, icon, startX, y);
				gui.text(font, line.text(), startX + 16 + ICON_TEXT_GAP, y + 4, line.color(), true);
				y += ICON_LINE_HEIGHT;
			} else if (line.small()) {
				// 小字行:推入缩放矩阵后按 0.75 渲染
				int scaledWidth = (int) (font.width(line.text()) * SMALL_SCALE);
				int startX = screenWidth / 2 - scaledWidth / 2;
				gui.pose().pushMatrix();
				gui.pose().translate(startX, y);
				gui.pose().scale(SMALL_SCALE, SMALL_SCALE);
				gui.text(font, line.text(), 0, 0, line.color(), true);
				gui.pose().popMatrix();
				y += SMALL_LINE_HEIGHT;
				if (line.bar() >= 0.0F) {
					// 挖掘进度条:深底 + 绿色已挖部分
					int barWidth = Math.max(40, panelWidth - 12);
					int barX = screenWidth / 2 - barWidth / 2;
					gui.fill(barX - 1, y, barX + barWidth + 1, y + 6, 0xFF222222);
					gui.fill(barX, y + 1, barX + Math.max(1, (int) (barWidth * line.bar())), y + 5, 0xFF3CE03C);
					y += BAR_LINE_HEIGHT;
				}
			} else {
				int startX = screenWidth / 2 - font.width(line.text()) / 2;
				gui.text(font, line.text(), startX, y + 1, line.color(), true);
				y += TEXT_LINE_HEIGHT;
			}
		}
	}
}
