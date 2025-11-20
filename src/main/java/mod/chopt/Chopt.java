package mod.chopt;

import net.fabricmc.api.ModInitializer;

public class Chopt implements ModInitializer {
	public static final String MOD_ID = "chopt";

	@Override
	public void onInitialize() {
		ChoptNetworking.registerPayloads();
		TreeChopper.register();
		ChoptNetworking.registerServerReceivers();
	}
}
