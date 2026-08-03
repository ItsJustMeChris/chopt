package mod.chopt.block;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the shrinking stump by reusing the stripped log's model and scaling X/Z.
 */
public class ShrinkingStumpRenderer implements BlockEntityRenderer<ShrinkingStumpBlockEntity, ShrinkingStumpRenderState> {
	private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

	private final BlockModelResolver blockModelResolver;

	public ShrinkingStumpRenderer(BlockEntityRendererProvider.Context context) {
		this.blockModelResolver = context.blockModelResolver();
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
			state.light = LightCoordsUtil.getLightCoords(stump.getLevel(), stump.getBlockPos());
		}
		state.level = stump.getLevel() instanceof ClientLevel clientLevel ? clientLevel : null;
		if (display != null) {
			this.blockModelResolver.update(state.blockModel, display, BLOCK_DISPLAY_CONTEXT);
		} else {
			state.blockModel.clear();
		}
	}

	@Override
	public void submit(ShrinkingStumpRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState cameraState) {
		if (state.displayState == null) return;
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.scale(state.scale, 1.0f, state.scale);
		pose.translate(-0.5, 0, -0.5);

		state.blockModel.submit(pose, collector, state.light, OverlayTexture.NO_OVERLAY, 0);

		if (state.breakProgress != null && state.level != null) {
			ModelFeatureRenderer.CrumblingOverlay breakOverlay = state.breakProgress;
			BlockStateModel breakModel = Minecraft.getInstance().getModelManager()
				.getBlockStateModelSet().get(state.displayState);
			List<BlockStateModelPart> breakParts = new ArrayList<>();
			RandomSource random = RandomSource.createThreadLocalInstance();
			random.setSeed(state.displayState.getSeed(state.blockPos));
			breakModel.collectParts(random, breakParts);
			collector.submitBreakingBlockModel(pose, List.copyOf(breakParts), breakOverlay.progress());
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
