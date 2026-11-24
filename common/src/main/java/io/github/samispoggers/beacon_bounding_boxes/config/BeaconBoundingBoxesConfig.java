package io.github.samispoggers.beacon_bounding_boxes.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import io.github.samispoggers.beacon_bounding_boxes.BeaconBoundingBoxes;
import io.github.samispoggers.beacon_bounding_boxes.platform.Services;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BeaconBoundingBoxesConfig {
    public static final int MIN_CUSTOM_Y = -64;
    public static final int MAX_CUSTOM_Y = 319;
    public static final int DEFAULT_CUSTOM_Y = 64;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile BeaconBoundingBoxesConfig instance = new BeaconBoundingBoxesConfig();

    public HeightMode heightMode = HeightMode.RADIUS;
    public int customYLevel = DEFAULT_CUSTOM_Y;
    public BoxMode boxMode = BoxMode.OUTLINE;
    public ConduitMode conduitMode = ConduitMode.OUTLINE;

    public static BeaconBoundingBoxesConfig get() {
        return instance;
    }

    public static Path getConfigPath() {
        return Services.get().getConfigDir().resolve("beacon_bounding_boxes.json");
    }

    public static synchronized void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            instance = new BeaconBoundingBoxesConfig();
            save();
            return;
        }

        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                BeaconBoundingBoxes.LOGGER.warn("Config file {} was empty; restoring defaults.", path);
                instance = new BeaconBoundingBoxesConfig();
                save();
                return;
            }
            BeaconBoundingBoxesConfig loaded = GSON.fromJson(raw, BeaconBoundingBoxesConfig.class);
            if (loaded == null) {
                loaded = new BeaconBoundingBoxesConfig();
            }
            loaded.sanitize();
            instance = loaded;
        } catch (JsonSyntaxException | IOException e) {
            BeaconBoundingBoxes.LOGGER.warn("Failed to read config file {}; restoring defaults.", path, e);
            instance = new BeaconBoundingBoxesConfig();
        }
    }

    public static synchronized void save() {
        instance.sanitize();
        Path path = getConfigPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BeaconBoundingBoxes.LOGGER.error("Failed to save config file {}", path, e);
        }
    }

    public void resetToDefaults() {
        this.heightMode = HeightMode.RADIUS;
        this.customYLevel = DEFAULT_CUSTOM_Y;
        this.boxMode = BoxMode.OUTLINE;
        this.conduitMode = ConduitMode.OUTLINE;
    }

    public void sanitize() {
        if (this.heightMode == null) {
            this.heightMode = HeightMode.RADIUS;
        }
        if (this.boxMode == null) {
            this.boxMode = BoxMode.OUTLINE;
        }
        if (this.conduitMode == null) {
            this.conduitMode = ConduitMode.OUTLINE;
        }
        this.customYLevel = Math.max(MIN_CUSTOM_Y, Math.min(MAX_CUSTOM_Y, this.customYLevel));
    }

    public enum HeightMode {
        RADIUS("config.beacon_bounding_boxes.height_mode.radius"),
        WORLD_HEIGHT("config.beacon_bounding_boxes.height_mode.world_height"),
        CUSTOM("config.beacon_bounding_boxes.height_mode.custom");

        private final String translationKey;

        HeightMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component displayName() {
            return Component.translatable(this.translationKey);
        }
    }

    public enum BoxMode {
        OUTLINE("config.beacon_bounding_boxes.box_mode.outline"),
        BOX("config.beacon_bounding_boxes.box_mode.box");

        private final String translationKey;

        BoxMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component displayName() {
            return Component.translatable(this.translationKey);
        }
    }

    public enum ConduitMode {
        OUTLINE("config.beacon_bounding_boxes.conduit_mode.outline"),
        FILLED("config.beacon_bounding_boxes.conduit_mode.filled");

        private final String translationKey;

        ConduitMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component displayName() {
            return Component.translatable(this.translationKey);
        }
    }
}
