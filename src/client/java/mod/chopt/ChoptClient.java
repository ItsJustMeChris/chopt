package mod.chopt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import mod.chopt.block.ShrinkingStumpRenderer;

@SuppressWarnings("deprecation") // HudRenderCallback remains the simplest hook for a tiny debug overlay
public class ChoptClient implements ClientModInitializer {
	private static final int INSPECT_COOLDOWN_TICKS = 5;
	private static final Logger LOGGER = LogUtils.getLogger();
	private BlockPos lastSentPos = null;
	private int tickCounter = 0;
	private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> pendingStumpDisplays = new java.util.HashMap<>();

	@Override
	public void onInitializeClient() {
		ChoptNetworking.registerPayloads();
		HudRenderCallback.EVENT.register(new TreeDebugHud());
		BlockEntityRenderers.register(ChoptBlocks.SHRINKING_STUMP_ENTITY, ShrinkingStumpRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(ChoptNetworking.InspectResponse.ID, (payload, context) -> {
			context.client().execute(() -> TreeDebugHud.setInspection(
				new TreeDebugHud.InspectionView(payload.pos(), payload.isTree(), payload.logs(), payload.required(), payload.hits(), System.currentTimeMillis())
			));
			LOGGER.info("Chopt inspect response {} tree={} hits {}/{}", payload.pos(), payload.isTree(), payload.hits(), payload.required());
		});

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
				lastSentPos = null;
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

			HitResult hit = client.hitResult;
			if (!(hit instanceof BlockHitResult blockHit)) {
				TreeDebugHud.clear();
				lastSentPos = null;
				return;
			}

			BlockPos pos = blockHit.getBlockPos();
			BlockState state = client.level.getBlockState(pos);
			if (!state.is(BlockTags.LOGS) && !state.is(ChoptBlocks.SHRINKING_STUMP)) {
				TreeDebugHud.clear();
				lastSentPos = null;
				return;
			}

			if (tickCounter++ % INSPECT_COOLDOWN_TICKS != 0 && pos.equals(lastSentPos)) {
				return; // avoid spamming packets
			}

			LOGGER.info("Chopt sending inspect for {}", pos);
			ClientPlayNetworking.send(new ChoptNetworking.InspectRequest(pos));
			lastSentPos = pos;
		});
	}
}
