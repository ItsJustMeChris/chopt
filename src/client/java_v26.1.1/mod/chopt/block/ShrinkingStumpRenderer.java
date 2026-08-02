package mod.chopt.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import mod.chopt.compat.ClientRenderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the shrinking stump by reusing the stripped log's model and scaling X/Z.
 */
public class ShrinkingStumpRenderer implements BlockEntityRenderer<ShrinkingStumpBlockEntity, ShrinkingStumpRenderState> {
	public ShrinkingStumpRenderer(BlockEntityRendererProvider.Context ignored) {
	}

	@Override
	public ShrinkingStumpRenderState createRenderState() {
		return new ShrinkingStumpRenderState();
	}

	@Override
	public void extractRenderState(ShrinkingStumpBlockEntity stump, ShrinkingStumpRenderState state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay overlay) {
		BlockEntityRenderer.super.extractRenderState(stump, state, partialTick, camera, overlay);
		BlockState display = stump.getDisplayState();
		state.displayState = display;
		state.scale = scaleFor(stump);
		if (stump.getLevel() != null) {
			state.light = LevelRenderer.getLightCoords(stump.getLevel(), stump.getBlockPos());
		}
		state.level = stump.getLevel() instanceof ClientLevel clientLevel ? clientLevel : null;
	}

	@Override
	public void submit(ShrinkingStumpRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState cameraState) {
		if (state.displayState == null) return;
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.scale(state.scale, 1.0f, state.scale);
		pose.translate(-0.5, 0, -0.5);

		collector.submitBlock(pose, state.displayState, state.light, OverlayTexture.NO_OVERLAY, 0);

		if (state.breakProgress != null && state.level != null) {
			ModelFeatureRenderer.CrumblingOverlay breakOverlay = state.breakProgress;
			var destroyType = ClientRenderCompat.destroyType(breakOverlay.progress());
			PoseStack overlayStack = new PoseStack();
			overlayStack.last().set(pose.last()); // match scaled stump transform
			SheetedDecalTextureGenerator decalBuffer = new SheetedDecalTextureGenerator(
				Minecraft.getInstance().renderBuffers().crumblingBufferSource().getBuffer(destroyType),
				breakOverlay.cameraPose(),
				1.0f
			);
			Minecraft.getInstance().getBlockRenderer()
				.renderBreakingTexture(state.displayState, state.blockPos, state.level, overlayStack, decalBuffer);
		}
		pose.popPose();
	}

	@Override
	public boolean shouldRender(ShrinkingStumpBlockEntity blockEntity, Vec3 cameraPos) {
		return true; // always render when present
	}

	private float scaleFor(ShrinkingStumpBlockEntity stump) {
		BlockState state = stump.getBlockState();
		int stage = state.getValue(ShrinkingStumpBlock.STAGE);
		int stages = state.getValue(ShrinkingStumpBlock.STAGES);
		return ShrinkingStumpBlock.scaleFor(stage, stages);
	}
}
