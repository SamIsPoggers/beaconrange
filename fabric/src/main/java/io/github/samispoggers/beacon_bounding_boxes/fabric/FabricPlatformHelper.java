package io.github.samispoggers.beacon_bounding_boxes.fabric;

import io.github.samispoggers.beacon_bounding_boxes.platform.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class FabricPlatformHelper implements PlatformHelper {
    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public String getLoaderName() {
        return "fabric";
    }
}
