package io.github.samispoggers.beacon_bounding_boxes.platform;

import java.nio.file.Path;

public interface PlatformHelper {
    Path getConfigDir();

    boolean isModLoaded(String modId);

    String getLoaderName();
}
