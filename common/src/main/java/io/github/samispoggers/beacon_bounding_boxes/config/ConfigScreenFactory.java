package io.github.samispoggers.beacon_bounding_boxes.config;

import io.github.samispoggers.beacon_bounding_boxes.BeaconBoundingBoxes;
import io.github.samispoggers.beacon_bounding_boxes.platform.Services;
import net.minecraft.client.gui.screens.Screen;

public final class ConfigScreenFactory {
    private static final String YACL_MOD_ID = "yet_another_config_lib_v3";

    private ConfigScreenFactory() {
    }

    public static Screen create(Screen parent) {
        if (Services.get().isModLoaded(YACL_MOD_ID)) {
            try {
                return YaclConfigScreen.create(parent);
            } catch (Throwable t) {
                BeaconBoundingBoxes.LOGGER.warn(
                        "Falling back to built-in config screen because YACL failed to open",
                        t
                );
            }
        }
        return new FallbackConfigScreen(parent);
    }
}
