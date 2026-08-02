package mod.chopt.compat.jade;

import mod.chopt.Chopt;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.theme.IThemeHelper;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.JadeUI;

/**
 * Makes Jade describe the tree being worked on rather than the placeholder block:
 * the original block's name and icon, plus the exact swing count from ShrinkingStumpData.
 */
public enum ShrinkingStumpProvider implements IBlockComponentProvider {
	INSTANCE;

	private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Chopt.MOD_ID, "shrinking_stump");

	@Override
	public Element getIcon(BlockAccessor accessor, IPluginConfig config, Element currentIcon) {
		BlockState original = displayState(accessor);
		if (original == null) {
			return null;
		}
		ItemStack stack = new ItemStack(original.getBlock());
		return stack.isEmpty() ? null : JadeUI.item(stack);
	}

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		BlockState original = displayState(accessor);
		if (original != null) {
			tooltip.replace(JadeIds.CORE_OBJECT_NAME, IThemeHelper.get().title(original.getBlock().getName()));
		}

		CompoundTag data = accessor.getServerData();
		int required = data.getIntOr(ShrinkingStumpData.KEY_REQUIRED, 0);
		if (required <= 0) {
			return;
		}
		int hits = Math.min(data.getIntOr(ShrinkingStumpData.KEY_HITS, 0), required);
		// Explicit white: Jade's default body colour is theme-dependent and can wash out.
		tooltip.add(Component.translatable("jade.chopt.chopping", hits, required).withStyle(ChatFormatting.WHITE));
	}

	private static BlockState displayState(BlockAccessor accessor) {
		return accessor.getBlockEntity() instanceof ShrinkingStumpBlockEntity target ? target.getDisplayState() : null;
	}

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@Override
	public int getDefaultPriority() {
		// Just after Jade's own name provider (HEAD - 100) so its line exists to replace.
		return TooltipPosition.HEAD - 99;
	}
}
