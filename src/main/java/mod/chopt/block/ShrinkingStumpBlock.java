package mod.chopt.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary stump that visually shrinks with chop progress.
 * Rendering is delegated to a block entity renderer so we can
 * reuse the stripped log's textures without per-species assets.
 */
public class ShrinkingStumpBlock extends RotatedPillarBlock implements EntityBlock {
	public static final int MAX_STAGE = 16;
	public static final float MIN_SCALE = 0.1f; // 90% smaller at the final step
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, MAX_STAGE);
	public static final IntegerProperty STAGES = IntegerProperty.create("stages", 1, MAX_STAGE);

	private static final Map<Integer, VoxelShape[]> SHAPES = new ConcurrentHashMap<>();
	private static final int DEFAULT_STAGES = 4;

	public ShrinkingStumpBlock(BlockBehaviour.Properties settings) {
		super(settings);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(AXIS, Direction.Axis.Y)
			.setValue(STAGE, 0)
			.setValue(STAGES, DEFAULT_STAGES));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(STAGE, STAGES);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		// Keep a model render so particles/picking work; actual visuals come from the BER.
		return RenderShape.MODEL;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeFor(state);
	}

	@Override
	public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return Blocks.OAK_LOG.defaultBlockState().getDestroyProgress(player, level, pos);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapeFor(state);
	}

	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		// Keep the outline consistent with collision/shape.
		return shapeFor(state);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ShrinkingStumpBlockEntity(pos, state);
	}

	public static float scaleFor(int stage, int totalStages) {
		int clampedStages = Mth.clamp(totalStages, 1, MAX_STAGE);
		int clampedStage = Mth.clamp(stage, 0, clampedStages);
		float t = (float) clampedStage / (float) clampedStages;
		return Mth.lerp(t, 1.0f, MIN_SCALE);
	}

	private static VoxelShape shapeFor(BlockState state) {
		int stages = state.getValue(STAGES);
		int stage = Math.min(state.getValue(STAGE), stages);
		VoxelShape[] cached = SHAPES.computeIfAbsent(stages, ShrinkingStumpBlock::buildShapes);
		return cached[Math.min(stage, cached.length - 1)];
	}

	private static VoxelShape[] buildShapes(int stages) {
		VoxelShape[] shapes = new VoxelShape[stages + 1];
		for (int i = 0; i <= stages; i++) {
			double scale = scaleFor(i, stages);
			double inset = (16.0 - 16.0 * scale) / 2.0;
			shapes[i] = Block.box(inset, 0, inset, 16.0 - inset, 16.0, 16.0 - inset);
		}
		return shapes;
	}
}
