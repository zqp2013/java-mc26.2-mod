package net.mile.superfurnace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 超级熔炉方块:朝向玩家放置,燃烧时点亮 LIT 状态(换 front_on 贴图)。
 */
public class SuperFurnaceBlock extends BaseEntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	private final FurnaceTier tier;

	public SuperFurnaceBlock(FurnaceTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, Boolean.FALSE));
	}

	// 26.3: BaseEntityBlock 的 codec() 覆写要求已删除,方块序列化不再走每方块 MapCodec

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		// 放置时把物品上的等级组件带进方块实体
		Integer itemLevel = stack.get(SuperFurnaceMod.LEVEL);
		if (itemLevel != null && itemLevel > 1
				&& level.getBlockEntity(pos) instanceof SuperFurnaceBlockEntity furnace) {
			furnace.setFurnaceLevel(itemLevel);
		}
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		// 创造模式中键取方块也保留等级
		ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
		if (includeData && level.getBlockEntity(pos) instanceof SuperFurnaceBlockEntity furnace
				&& furnace.getFurnaceLevel() > 1) {
			stack.set(SuperFurnaceMod.LEVEL, furnace.getFurnaceLevel());
		}
		return stack;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		// BaseEntityBlock 默认不渲染模型,必须改回 MODEL
		return RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return tier.blockEntityType.create(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return createTickerHelper(type, tier.blockEntityType, SuperFurnaceBlockEntity::serverTick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
			BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof SuperFurnaceBlockEntity furnace) {
			player.openMenu(furnace);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// 燃烧时像原版熔炉一样冒火苗/烟雾粒子(纯视觉)
		if (state.getValue(LIT)) {
			double x = pos.getX() + 0.5;
			double y = pos.getY();
			double z = pos.getZ() + 0.5;
			if (random.nextDouble() < 0.1) {
				level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA,
						x + (random.nextDouble() - 0.5) * 0.4, y + 0.6, z + (random.nextDouble() - 0.5) * 0.4,
						0.0, 0.02, 0.0);
			}
			level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
					x + (random.nextDouble() - 0.5) * 0.6, y + 0.9, z + (random.nextDouble() - 0.5) * 0.6,
					0.0, 0.02, 0.0);
		}
	}
}
