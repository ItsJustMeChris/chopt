package mod.chopt.mixin.client;

import mod.chopt.compat.StumpParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@ModifyVariable(method = "addDestroyBlockEffect", at = @At("HEAD"), argsOnly = true)
	private BlockState chopt$swapDestroyState(BlockState state, BlockPos pos) {
		return StumpParticles.swap((ClientLevel)(Object)this, pos, state);
	}
}
