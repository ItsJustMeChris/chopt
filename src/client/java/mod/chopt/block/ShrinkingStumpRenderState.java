package mod.chopt.block;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Simple render state container for the shrinking stump.
 */
public class ShrinkingStumpRenderState extends BlockEntityRenderState {
	BlockState displayState;
	float scale = 1.0f;
	int light = 0;
}
