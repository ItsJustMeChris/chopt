package mod.chopt.compat;

import mod.chopt.ChoptBlocks;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Version-stable helper for selecting the stump's display state for particles.
 * Called from version-specific mixins.
 */
public final class StumpParticles {
	private StumpParticles() {}

	public static BlockState swap(Level level, BlockPos pos, BlockState original) {
		if (original.is(ChoptBlocks.SHRINKING_STUMP)) {
			if (level.getBlockEntity(pos) instanceof ShrinkingStumpBlockEntity stump && stump.getDisplayState() != null) {
				return stump.getDisplayState();
			}
		}
		return original;
	}
}
