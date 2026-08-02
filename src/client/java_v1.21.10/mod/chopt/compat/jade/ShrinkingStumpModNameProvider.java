package mod.chopt.compat.jade;

import mod.chopt.Chopt;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

/**
 * Swaps Jade's mod line for the mod that actually owns the block we are standing in for,
 * so the tooltip is indistinguishable from the real one ("Minecraft" for a vanilla ore).
 *
 * <p>Separate from ShrinkingStumpProvider because of ordering: Jade's own mod name provider sits at
 * TAIL - 1, so the line does not exist yet at ShrinkingStumpProvider's HEAD - 99.
 */
public enum ShrinkingStumpModNameProvider implements IBlockComponentProvider {
	INSTANCE;

	private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Chopt.MOD_ID, "shrinking_stump_mod_name");

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		if (!(accessor.getBlockEntity() instanceof ShrinkingStumpBlockEntity target)) {
			return;
		}
		BlockState original = target.getDisplayState();
		if (original == null) {
			return;
		}
		String namespace = BuiltInRegistries.BLOCK.getKey(original.getBlock()).getNamespace();
		if (namespace.equals(Chopt.MOD_ID)) {
			return;
		}
		String modName = FabricLoader.getInstance()
			.getModContainer(namespace)
			.map(container -> container.getMetadata().getName())
			.orElse(namespace);
		Component line = Component.literal(modName).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC);
		tooltip.replace(JadeIds.CORE_MOD_NAME, line);
	}

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@Override
	public int getDefaultPriority() {
		// After Jade's own mod name provider (TAIL - 1) so its line exists to replace.
		return TooltipPosition.TAIL + 1;
	}
}
