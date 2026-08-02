package mod.chopt.compat.jade;

import mod.chopt.Chopt;
import mod.chopt.TreeChopper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Ships the live swing count to the client. Only the server knows it: progress lives in
 * TreeChopper's session map, not on the block entity.
 *
 * <p>Shares its UID with ShrinkingStumpProvider so Jade's config toggle covers both halves.
 */
public enum ShrinkingStumpData implements IServerDataProvider<BlockAccessor> {
	INSTANCE;

	static final String KEY_HITS = "ChoptHits";
	static final String KEY_REQUIRED = "ChoptRequired";

	private static final ResourceLocation UID = new ResourceLocation(Chopt.MOD_ID, "shrinking_stump");

	@Override
	public void appendServerData(CompoundTag data, BlockAccessor accessor) {
		TreeChopper.Progress progress = TreeChopper.progressAt(accessor.getLevel(), accessor.getPosition());
		if (progress == null) {
			return;
		}
		data.putInt(KEY_HITS, progress.hits());
		data.putInt(KEY_REQUIRED, progress.requiredChops());
	}

	@Override
	public ResourceLocation getUid() {
		return UID;
	}
}
