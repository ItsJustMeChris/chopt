package mod.chopt.block;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Simple render state container for the shrinking stump.
 */
public class ShrinkingStumpRenderState extends BlockEntityRenderState {
	BlockState displayState;
	float scale = 1.0f;
	int light = 0;
	@Nullable ClientLevel level;
}
