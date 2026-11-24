package io.github.samispoggers.beacon_bounding_boxes.fabric;

import io.github.samispoggers.beacon_bounding_boxes.client.BeaconBoundingBoxesClient;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig;
import io.github.samispoggers.beacon_bounding_boxes.platform.Services;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class BeaconBoundingBoxesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Services.init(new FabricPlatformHelper());
        BeaconBoundingBoxesConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(BeaconBoundingBoxesClient.KEYBIND_CATEGORY_ID);
        KeyMappingHelper.registerKeyMapping(BeaconBoundingBoxesClient.createToggleBeaconKey(category));
        KeyMappingHelper.registerKeyMapping(BeaconBoundingBoxesClient.createToggleConduitKey(category));

        ClientTickEvents.END_CLIENT_TICK.register(BeaconBoundingBoxesClient::onClientTick);
    }
}
