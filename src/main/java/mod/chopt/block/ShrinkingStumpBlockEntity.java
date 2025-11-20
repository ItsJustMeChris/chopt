package mod.chopt.block;

import mod.chopt.ChoptBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Stores which stripped block state to render for the shrinking stump.
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
	protected void saveAdditional(ValueOutput writer) {
		super.saveAdditional(writer);
		if (displayState != null) {
			writer.store("display_state", BlockState.CODEC, displayState);
		}
	}

	@Override
	public void loadAdditional(ValueInput reader) {
		super.loadAdditional(reader);
		displayState = reader.read("display_state", BlockState.CODEC).orElse(null);
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
	public CompoundTag getUpdateTag(HolderLookup.Provider lookupProvider) {
		return saveWithoutMetadata(lookupProvider);
	}
}
