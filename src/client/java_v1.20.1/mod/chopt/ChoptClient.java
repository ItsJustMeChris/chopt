package mod.chopt;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import mod.chopt.block.ShrinkingStumpRenderer;

public class ChoptClient implements ClientModInitializer {
	private final java.util.Map<BlockPos, BlockState> pendingStumpDisplays = new java.util.HashMap<>();

	@Override
	public void onInitializeClient() {
		ChoptNetworking.registerPayloads();
		BlockEntityRenderers.register(ChoptBlocks.SHRINKING_STUMP_ENTITY, ShrinkingStumpRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(ChoptNetworking.SHRINKING_STUMP_DISPLAY_ID, (client, handler, buf, responseSender) -> {
			handleStumpDisplay(client, buf);
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

	private void handleStumpDisplay(net.minecraft.client.Minecraft client, FriendlyByteBuf buf) {
		BlockPos pos = buf.readBlockPos();
		BlockState state = Block.stateById(buf.readVarInt());
		client.execute(() -> {
			if (client.level == null) return;
			if (client.level.getBlockEntity(pos) instanceof ShrinkingStumpBlockEntity stump) {
				stump.setDisplayState(state);
			} else {
				pendingStumpDisplays.put(pos, state);
			}
		});
	}
}
