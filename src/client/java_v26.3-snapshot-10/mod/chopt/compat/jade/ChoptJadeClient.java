package mod.chopt.compat.jade;

import mod.chopt.block.ShrinkingStumpBlock;
import snownee.jade.api.IWailaClientRegistration;

/**
 * Client-only half of the Jade integration. Invoked reflectively by ChoptJadePlugin so
 * that a dedicated server never has to load these classes.
 */
public final class ChoptJadeClient {
	private ChoptJadeClient() {}

	public static void register(IWailaClientRegistration registration) {
		registration.registerBlockComponent(ShrinkingStumpProvider.INSTANCE, ShrinkingStumpBlock.class);
		registration.registerBlockIcon(ShrinkingStumpProvider.INSTANCE, ShrinkingStumpBlock.class);
		registration.registerBlockComponent(ShrinkingStumpModNameProvider.INSTANCE, ShrinkingStumpBlock.class);
	}
}
