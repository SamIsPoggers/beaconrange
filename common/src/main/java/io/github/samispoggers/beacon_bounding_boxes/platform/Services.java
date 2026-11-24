package io.github.samispoggers.beacon_bounding_boxes.platform;

import java.util.Objects;

public final class Services {
    private static PlatformHelper instance;

    private Services() {
    }

    public static void init(PlatformHelper helper) {
        instance = Objects.requireNonNull(helper, "PlatformHelper must not be null");
    }

    public static PlatformHelper get() {
        if (instance == null) {
            throw new IllegalStateException("Services.init(...) has not been called yet");
        }
        return instance;
    }
}
