package mod.chopt.mixin.client;

import mod.chopt.compat.StumpParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swap stump display state into block hit (crack) particles.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Shadow @Final private ClientLevel level;

	@ModifyVariable(
		method = "crack",
		at = @At(value = "STORE"),
		ordinal = 0,
		require = 0, // optional: if method signature changes, skip gracefully
		expect = 0
	)
	private BlockState chopt$swapCrackState(BlockState original, BlockPos pos, Direction side) {
		return StumpParticles.swap(this.level, pos, original);
	}
}
