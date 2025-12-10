package mod.chopt.mixin.client;

import mod.chopt.ChoptBlocks;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Make stump hit/break particles use the per-tree display state instead of the
 * placeholder model texture.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@ModifyVariable(method = "addDestroyBlockEffect", at = @At("HEAD"), argsOnly = true)
	private BlockState chopt$swapDestroyState(BlockState state, BlockPos pos) {
		return swapState((ClientLevel)(Object)this, state, pos);
	}

	@Redirect(
		method = "addBreakingBlockEffect",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
		)
	)
	private BlockState chopt$swapHitState(ClientLevel level, BlockPos pos, BlockPos blockPos, Direction side) {
		return swapState(level, level.getBlockState(pos), pos);
	}

	private static BlockState swapState(ClientLevel level, BlockState original, BlockPos pos) {
		if (original.is(ChoptBlocks.SHRINKING_STUMP)) {
			if (level.getBlockEntity(pos) instanceof ShrinkingStumpBlockEntity stump && stump.getDisplayState() != null) {
				return stump.getDisplayState();
			}
		}
		return original;
	}
}
