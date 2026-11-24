package io.github.samispoggers.beacon_bounding_boxes.fabric.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return io.github.samispoggers.beacon_bounding_boxes.config.ConfigScreenFactory::create;
    }
}
