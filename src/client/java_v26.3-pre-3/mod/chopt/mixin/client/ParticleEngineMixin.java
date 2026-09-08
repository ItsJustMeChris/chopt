package mod.chopt.mixin.client;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 1.21.11 does not need particle crack swapping; stub keeps mixin list consistent.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
}
