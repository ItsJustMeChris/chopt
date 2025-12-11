package mod.chopt;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

/**
 * Networking helpers for debug inspect overlay.
 */
public final class ChoptNetworking {
	private static boolean payloadsRegistered = false;
	private ChoptNetworking() {}

	public static void registerPayloads() {
		if (payloadsRegistered) return;
		payloadsRegistered = true;
		PayloadTypeRegistry.playS2C().register(ShrinkingStumpDisplay.ID, ShrinkingStumpDisplay.CODEC);
	}

	public static void registerServerReceivers() {
		// no-op for now; stump display sync is sent proactively from the tree chopper
	}

	public static void syncShrinkingStump(ServerLevel level, BlockPos pos, BlockState state) {
		ShrinkingStumpDisplay payload = new ShrinkingStumpDisplay(pos, state);
		for (ServerPlayer player : PlayerLookup.tracking(level, pos)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public record ShrinkingStumpDisplay(BlockPos pos, BlockState state) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ShrinkingStumpDisplay> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Chopt.MOD_ID, "shrinking_stump_display"));
		public static final StreamCodec<FriendlyByteBuf, ShrinkingStumpDisplay> CODEC = CustomPacketPayload.codec(ShrinkingStumpDisplay::write, ShrinkingStumpDisplay::new);

		public ShrinkingStumpDisplay(FriendlyByteBuf buf) {
			this(buf.readBlockPos(), Block.stateById(buf.readVarInt()));
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeBlockPos(pos);
			buf.writeVarInt(Block.getId(state));
		}

		@Override
		public CustomPacketPayload.Type<ShrinkingStumpDisplay> type() {
			return ID;
		}
	}
}
