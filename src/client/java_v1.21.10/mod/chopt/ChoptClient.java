package mod.chopt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import mod.chopt.block.ShrinkingStumpRenderer;

public class ChoptClient implements ClientModInitializer {
	private final java.util.Map<BlockPos, BlockState> pendingStumpDisplays = new java.util.HashMap<>();

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
		});
	}
}
