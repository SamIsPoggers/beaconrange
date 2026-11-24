package io.github.samispoggers.beacon_bounding_boxes.neoforge;

import io.github.samispoggers.beacon_bounding_boxes.BeaconBoundingBoxes;
import io.github.samispoggers.beacon_bounding_boxes.client.BeaconBoundingBoxesClient;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig;
import io.github.samispoggers.beacon_bounding_boxes.config.ConfigScreenFactory;
import io.github.samispoggers.beacon_bounding_boxes.platform.Services;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = BeaconBoundingBoxes.MOD_ID, dist = Dist.CLIENT)
public final class BeaconBoundingBoxesNeoForgeClient {

    public BeaconBoundingBoxesNeoForgeClient(IEventBus modEventBus, ModContainer modContainer) {
        Services.init(new NeoForgePlatformHelper());
        BeaconBoundingBoxesConfig.load();

        modEventBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            KeyMapping.Category category = new KeyMapping.Category(BeaconBoundingBoxesClient.KEYBIND_CATEGORY_ID);
            event.registerCategory(category);
            event.register(BeaconBoundingBoxesClient.createToggleBeaconKey(category));
            event.register(BeaconBoundingBoxesClient.createToggleConduitKey(category));
        });

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> BeaconBoundingBoxesClient.onClientTick(Minecraft.getInstance()));

        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> ConfigScreenFactory.create(parent)
        );
    }
}
