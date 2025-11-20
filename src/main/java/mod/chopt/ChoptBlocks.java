package mod.chopt;

import mod.chopt.block.ShrinkingStumpBlock;
import mod.chopt.block.ShrinkingStumpBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Central point for block and block entity registration.
 */
public final class ChoptBlocks {
	public static final String STUMP_NAME = "shrinking_stump";
	private static final ResourceKey<Block> STUMP_KEY = ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Chopt.MOD_ID, STUMP_NAME));

	public static final ShrinkingStumpBlock SHRINKING_STUMP = new ShrinkingStumpBlock(
		BlockBehaviour.Properties.of()
			.setId(STUMP_KEY)
			.mapColor(MapColor.WOOD)
			.strength(2.0F)
			.sound(SoundType.WOOD)
			.noOcclusion()
			.pushReaction(PushReaction.DESTROY)
	);

	public static final BlockEntityType<ShrinkingStumpBlockEntity> SHRINKING_STUMP_ENTITY =
		FabricBlockEntityTypeBuilder.create(ShrinkingStumpBlockEntity::new, SHRINKING_STUMP).build(null);

	private static boolean registered;

	private ChoptBlocks() {}

	public static void register() {
		if (registered) return;
		registered = true;

		registerBlock(STUMP_NAME, SHRINKING_STUMP);
		registerBlockEntity(STUMP_NAME, SHRINKING_STUMP_ENTITY);
	}

	private static void registerBlock(String path, Block block) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Chopt.MOD_ID, path);
		Registry.register(BuiltInRegistries.BLOCK, id, block);
	}

	private static <T extends BlockEntityType<?>> void registerBlockEntity(String path, T type) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Chopt.MOD_ID, path);
		Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
	}
}
