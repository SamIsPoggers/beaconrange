package io.github.samispoggers.beacon_bounding_boxes.neoforge;

import io.github.samispoggers.beacon_bounding_boxes.platform.PlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class NeoForgePlatformHelper implements PlatformHelper {
    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    @Override
    public String getLoaderName() {
        return "neoforge";
    }
}
