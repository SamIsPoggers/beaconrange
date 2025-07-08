package io.github.samispoggers.beaconrange;

import io.github.samispoggers.beaconrange.config.ConfigManager;
import net.fabricmc.api.ModInitializer;

public class BeaconRange implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigManager.load();
    }
}