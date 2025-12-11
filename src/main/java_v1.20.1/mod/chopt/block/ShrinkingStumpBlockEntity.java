package mod.chopt.block;

import mod.chopt.ChoptBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Stores which stripped block state to render for the shrinking stump.
 * 1.20.1 variant uses CompoundTag save/load APIs.
 */
public class ShrinkingStumpBlockEntity extends BlockEntity {
	private BlockState displayState;

	public ShrinkingStumpBlockEntity(BlockPos pos, BlockState state) {
		super(ChoptBlocks.SHRINKING_STUMP_ENTITY, pos, state);
	}

	public BlockState getDisplayState() {
		return displayState != null ? displayState : defaultState();
	}

	public void setDisplayState(BlockState state) {
		this.displayState = state;
		setChanged();
	}

	private BlockState defaultState() {
		return Blocks.OAK_LOG.defaultBlockState();
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (displayState != null) {
			tag.putInt("display_state", Block.getId(displayState));
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		int id = tag.contains("display_state") ? tag.getInt("display_state") : 0;
		displayState = id != 0 ? Block.stateById(id) : null;
	}

	public static BlockState copyAxis(BlockState source, BlockState target) {
		if (source.hasProperty(BlockStateProperties.AXIS) && target.hasProperty(BlockStateProperties.AXIS)) {
			return target.setValue(BlockStateProperties.AXIS, source.getValue(BlockStateProperties.AXIS));
		}
		return target;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata();
	}
}
