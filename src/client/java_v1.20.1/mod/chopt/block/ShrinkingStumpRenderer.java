package mod.chopt.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 1.20.1 renderer uses the classic BlockEntityRenderer API.
 */
public class ShrinkingStumpRenderer implements BlockEntityRenderer<ShrinkingStumpBlockEntity> {
	private final BlockRenderDispatcher dispatcher;

	public ShrinkingStumpRenderer(BlockEntityRendererProvider.Context ctx) {
		this.dispatcher = ctx.getBlockRenderDispatcher();
	}

	@Override
	public void render(ShrinkingStumpBlockEntity stump, float tickDelta, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		BlockState display = stump.getDisplayState();
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		float scale = ShrinkingStumpBlock.scaleFor(
			stump.getBlockState().getValue(ShrinkingStumpBlock.STAGE),
			stump.getBlockState().getValue(ShrinkingStumpBlock.STAGES)
		);
		pose.scale(scale, 1.0f, scale);
		pose.translate(-0.5, 0, -0.5);

		dispatcher.renderSingleBlock(display, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(ShrinkingStumpBlockEntity stump) {
		return true;
	}

	@Override
	public boolean shouldRender(ShrinkingStumpBlockEntity blockEntity, Vec3 cameraPos) {
		return true;
	}
}
