package io.github.samispoggers.beacon_bounding_boxes.compat;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig;

public class ModMenuCompat implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BeaconBoundingBoxesConfig::createScreen;
    }
}
