package mod.chopt.compat;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.ModelBakery;

/**
 * Render helpers for 1.21.11+ where RenderType moved to rendertype package.
 */
public final class ClientRenderCompat {
	private ClientRenderCompat() {}

	public static RenderType destroyType(int progress) {
		return ModelBakery.DESTROY_TYPES.get(progress);
	}
}
