package mod.chopt;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Simple S2C packet for stump display sync for 1.20.1 (pre-CustomPacketPayload).
 */
public final class ChoptNetworking {
	public static final ResourceLocation SHRINKING_STUMP_DISPLAY_ID = new ResourceLocation(Chopt.MOD_ID, "shrinking_stump_display");
	private ChoptNetworking() {}

	public static void registerPayloads() {
		// no static payload registry in this version
	}

	public static void registerServerReceivers() {
		// no-op for now; stump display sync is sent proactively from the tree chopper
	}

	public static void syncShrinkingStump(ServerLevel level, BlockPos pos, BlockState state) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeBlockPos(pos);
		buf.writeVarInt(Block.getId(state));
		for (ServerPlayer player : PlayerLookup.tracking(level, pos)) {
			ServerPlayNetworking.send(player, SHRINKING_STUMP_DISPLAY_ID, new FriendlyByteBuf(buf.copy()));
		}
		buf.release();
	}
}
