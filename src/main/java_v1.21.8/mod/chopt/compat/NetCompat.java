package mod.chopt.compat;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Networking helpers for 1.21.10 (ResourceLocation-based payload IDs).
 */
public final class NetCompat {
	private NetCompat() {}

	public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return new CustomPacketPayload.Type<>(Ids.id(path));
	}
}
