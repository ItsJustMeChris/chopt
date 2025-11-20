package mod.chopt;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Very small HUD overlay for debug info when looking at logs.
 */
@SuppressWarnings("deprecation") // HudRenderCallback is deprecated in favor of HudElementRegistry, but sufficient for debug text
public final class TreeDebugHud implements HudRenderCallback {
	private static final long DISPLAY_MS = 5_000;
	private static volatile InspectionView latest;

	public static void setInspection(InspectionView view) {
		latest = view;
	}

	public static void clear() {
		latest = null;
	}

	@Override
	public void onHudRender(GuiGraphics gui, DeltaTracker deltaTracker) {
		InspectionView view = latest;
		if (view == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.options.hideGui) return;
		if (System.currentTimeMillis() - view.timestamp() > DISPLAY_MS) {
			latest = null;
			return;
		}

		int x = 10;
		int y = 10;
		int color = 0xFFFFFFFF; // opaque white (ARGB)
		// small translucent backdrop for readability
		gui.fill(x - 4, y - 4, x + 160, y + 32, 0xAA000000);
		Component title = Component.literal("Chopt Debug");
		gui.drawString(mc.font, title, x, y, color, false);
		y += 10;

		Component treeLine = view.isTree()
			? Component.literal("Tree: yes (" + view.logs() + " logs)")
			: Component.literal("Tree: no");
		gui.drawString(mc.font, treeLine, x, y, color, false);
		y += 10;

		if (view.isTree()) {
			Component progress = Component.literal("Chops: " + view.hits() + " / " + view.required());
			gui.drawString(mc.font, progress, x, y, color, false);
		}
	}

	public record InspectionView(BlockPos pos, boolean isTree, int logs, int required, int hits, long timestamp) {}
}
