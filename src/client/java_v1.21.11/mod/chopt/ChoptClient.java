package mod.chopt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import mod.chopt.block.ShrinkingStumpBlock;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import mod.chopt.block.ShrinkingStumpRenderer;

@SuppressWarnings("deprecation") // HudRenderCallback remains the simplest hook for a tiny debug overlay
public class ChoptClient implements ClientModInitializer {
	private static final boolean ENABLE_DEBUG_OVERLAY = false;
	private static final int INSPECT_INTERVAL_TICKS = 5;
	private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> pendingStumpDisplays = new java.util.HashMap<>();
	private BlockPos lastInspectedPos = null;
	private long lastInspectTick = -INSPECT_INTERVAL_TICKS;

	@Override
	public void onInitializeClient() {
		ChoptNetworking.registerPayloads();
		BlockEntityRenderers.register(ChoptBlocks.SHRINKING_STUMP_ENTITY, ShrinkingStumpRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(ChoptNetworking.ShrinkingStumpDisplay.ID, (payload, context) -> {
			context.client().execute(() -> {
				if (context.client().level == null) return;
				if (context.client().level.getBlockEntity(payload.pos()) instanceof ShrinkingStumpBlockEntity stump) {
					stump.setDisplayState(payload.state());
					return;
				}
				pendingStumpDisplays.put(payload.pos(), payload.state());
			});
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				TreeDebugHud.clear();
				pendingStumpDisplays.clear();
				return;
			}

			if (!pendingStumpDisplays.isEmpty()) {
				pendingStumpDisplays.entrySet().removeIf(entry -> {
					if (client.level.getBlockEntity(entry.getKey()) instanceof ShrinkingStumpBlockEntity stump) {
						stump.setDisplayState(entry.getValue());
						return true;
					}
					return false;
				});
			}

			if (!ENABLE_DEBUG_OVERLAY) {
				return; // skip HUD + inspect logic in release builds
			}

			HitResult hit = client.hitResult;
			if (!(hit instanceof BlockHitResult blockHit)) {
				TreeDebugHud.clear();
				return;
			}

			BlockPos pos = blockHit.getBlockPos();
			BlockState state = client.level.getBlockState(pos);
			if (!state.is(BlockTags.LOGS) && !state.is(ChoptBlocks.SHRINKING_STUMP)) {
				TreeDebugHud.clear();
				return;
			}

			long nowTick = client.level.getGameTime();
			if (pos.equals(lastInspectedPos) && (nowTick - lastInspectTick) < INSPECT_INTERVAL_TICKS) {
				return; // keep client-side scan lightweight
			}

			TreeChopper.Inspection inspection = TreeChopper.inspect(client.level, pos);
			if (inspection.isTree()) {
				int logs = inspection.logs();
				int required = TreeChopper.computeRequiredChops(logs);
				int hits = estimateHits(state, required);
				TreeDebugHud.setInspection(new TreeDebugHud.InspectionView(pos, true, logs, required, hits, System.currentTimeMillis()));
				lastInspectedPos = pos;
				lastInspectTick = nowTick;
			} else {
				TreeDebugHud.clear();
			}
		});

		if (!ENABLE_DEBUG_OVERLAY) {
			return;
		}

		HudRenderCallback.EVENT.register(new TreeDebugHud());
	}

	private static int estimateHits(BlockState state, int requiredChops) {
		if (!state.hasProperty(ShrinkingStumpBlock.STAGE) || !state.hasProperty(ShrinkingStumpBlock.STAGES)) {
			return 0;
		}
		int stage = state.getValue(ShrinkingStumpBlock.STAGE);
		int stages = Math.max(1, state.getValue(ShrinkingStumpBlock.STAGES));
		double ratio = (double) stage / (double) stages;
		return Mth.clamp((int) Math.ceil(ratio * requiredChops), 0, requiredChops);
	}
}
