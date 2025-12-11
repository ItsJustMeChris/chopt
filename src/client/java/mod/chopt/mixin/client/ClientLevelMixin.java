package mod.chopt.mixin.client;

import mod.chopt.compat.StumpParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Make stump hit/break particles use the per-tree display state instead of the
 * placeholder model texture.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@ModifyVariable(method = "addDestroyBlockEffect", at = @At("HEAD"), argsOnly = true)
	private BlockState chopt$swapDestroyState(BlockState state, BlockPos pos) {
		return chopt$swapState((ClientLevel)(Object)this, state, pos);
	}

	private static BlockState chopt$swapState(ClientLevel level, BlockState original, BlockPos pos) {
		return StumpParticles.swap(level, pos, original);
	}
}
