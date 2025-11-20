package mod.chopt;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Networking helpers for debug inspect overlay.
 */
public final class ChoptNetworking {
	private static boolean payloadsRegistered = false;
	private ChoptNetworking() {}

	public static void registerPayloads() {
		if (payloadsRegistered) return;
		payloadsRegistered = true;
		PayloadTypeRegistry.playC2S().register(InspectRequest.ID, InspectRequest.CODEC);
		PayloadTypeRegistry.playS2C().register(InspectResponse.ID, InspectResponse.CODEC);
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(InspectRequest.ID, (payload, context) -> {
			ServerPlayer player = context.player();
			Level level = player.level();
			TreeChopper.Inspection inspection = TreeChopper.inspect(level, payload.pos());
			InspectResponse response = new InspectResponse(payload.pos(), inspection.isTree(), inspection.logs(), inspection.requiredChops(), inspection.hits());
			context.responseSender().sendPacket(response);
		});
	}

	public record InspectRequest(BlockPos pos) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<InspectRequest> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Chopt.MOD_ID, "inspect_request"));
		public static final StreamCodec<FriendlyByteBuf, InspectRequest> CODEC = CustomPacketPayload.codec(InspectRequest::write, InspectRequest::new);

		public InspectRequest(FriendlyByteBuf buf) {
			this(buf.readBlockPos());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeBlockPos(pos);
		}

		@Override
		public CustomPacketPayload.Type<InspectRequest> type() {
			return ID;
		}
	}

	public record InspectResponse(BlockPos pos, boolean isTree, int logs, int required, int hits) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<InspectResponse> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Chopt.MOD_ID, "inspect_response"));
		public static final StreamCodec<FriendlyByteBuf, InspectResponse> CODEC = CustomPacketPayload.codec(InspectResponse::write, InspectResponse::new);

		public InspectResponse(FriendlyByteBuf buf) {
			this(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeBlockPos(pos);
			buf.writeBoolean(isTree);
			buf.writeVarInt(logs);
			buf.writeVarInt(required);
			buf.writeVarInt(hits);
		}

		@Override
		public CustomPacketPayload.Type<InspectResponse> type() {
			return ID;
		}
	}
}
