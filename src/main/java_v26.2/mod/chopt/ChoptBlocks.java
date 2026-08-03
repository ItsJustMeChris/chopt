package mod.chopt;

import mod.chopt.block.ShrinkingStumpBlock;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import mod.chopt.compat.Ids;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * Central point for block and block entity registration.
 */
public final class ChoptBlocks {
	public static final String STUMP_NAME = "shrinking_stump";
	private static final ResourceKey<Block> STUMP_KEY = ResourceKey.create(Registries.BLOCK, Ids.id(STUMP_NAME));

	public static final ShrinkingStumpBlock SHRINKING_STUMP = new ShrinkingStumpBlock(
		BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG)
			.setId(STUMP_KEY)
			.noOcclusion()
			.ignitedByLava()
			.pushReaction(PushReaction.DESTROY)
	);

	public static final BlockEntityType<ShrinkingStumpBlockEntity> SHRINKING_STUMP_ENTITY =
		FabricBlockEntityTypeBuilder.create(ShrinkingStumpBlockEntity::new, SHRINKING_STUMP).build();

	private static boolean registered;

	private ChoptBlocks() {}

	public static void register() {
		if (registered) return;
		registered = true;

		registerBlock(STUMP_NAME, SHRINKING_STUMP);
		registerBlockEntity(STUMP_NAME, SHRINKING_STUMP_ENTITY);
	}

	private static void registerBlock(String path, Block block) {
		Registry.register(BuiltInRegistries.BLOCK, Ids.blockId(path), block);
	}

	private static <T extends BlockEntityType<?>> void registerBlockEntity(String path, T type) {
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Ids.blockEntityId(path), type);
	}
}
