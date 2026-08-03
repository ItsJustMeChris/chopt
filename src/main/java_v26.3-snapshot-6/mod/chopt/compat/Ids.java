package mod.chopt.compat;

import mod.chopt.Chopt;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * 1.21.11+ identifiers (Identifier rename).
 */
public final class Ids {
	private Ids() {}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Chopt.MOD_ID, path);
	}

	public static ResourceKey<Block> blockKey(String path) {
		return ResourceKey.create(Registries.BLOCK, id(path));
	}

	public static Identifier blockId(String path) {
		return id(path);
	}

	public static Identifier blockEntityId(String path) {
		return id(path);
	}

	public static <T extends BlockEntityType<?>> ResourceKey<BlockEntityType<?>> blockEntityKey(String path) {
		return ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id(path));
	}
}
