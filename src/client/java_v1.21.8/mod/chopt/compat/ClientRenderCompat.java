package mod.chopt.compat;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelBakery;

/**
 * Render helpers for 1.21.10 where RenderType lives in net.minecraft.client.renderer.
 */
public final class ClientRenderCompat {
	private ClientRenderCompat() {}

	public static RenderType destroyType(int progress) {
		return ModelBakery.DESTROY_TYPES.get(progress);
	}
}
