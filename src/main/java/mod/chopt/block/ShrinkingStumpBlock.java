package mod.chopt.block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Temporary stump that visually shrinks with chop progress.
 * Rendering is delegated to a block entity renderer so we can
 * reuse the stripped log's textures without per-species assets.
 */
public class ShrinkingStumpBlock extends Block implements EntityBlock {
	public static final int MAX_STAGE = 3;
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, MAX_STAGE);

	private static final VoxelShape[] SHAPES = new VoxelShape[]{
		Block.box(0, 0, 0, 16, 16, 16),          // stage 0 (full)
		Block.box(2, 0, 2, 14, 16, 14),          // stage 1
		Block.box(4, 0, 4, 12, 16, 12),          // stage 2
		Block.box(6, 0, 6, 10, 16, 10)           // stage 3 (smallest)
	};

	public ShrinkingStumpBlock(BlockBehaviour.Properties settings) {
		super(settings);
		this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STAGE);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		// Standard model render; visuals provided by the BER.
		return RenderShape.MODEL;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(STAGE)];
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(STAGE)];
	}

	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		// Keep the outline consistent with collision/shape.
		return SHAPES[state.getValue(STAGE)];
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ShrinkingStumpBlockEntity(pos, state);
	}
}
