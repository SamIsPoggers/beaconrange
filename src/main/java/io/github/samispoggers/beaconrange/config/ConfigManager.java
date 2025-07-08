package io.github.samispoggers.beaconrange.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("beaconrange.json");

    public static ModConfig config = new ModConfig();

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                config = GSON.fromJson(json, ConfigFileWrapper.class).toConfig();
            } catch (IOException e) {
                System.err.println("[BeaconRange] Failed to load config: " + e);
            }
        } else {
            save(); // Save default config
        }
    }

    public static void save() {
        try {
            ConfigFileWrapper wrapper = new ConfigFileWrapper(config);
            String json = GSON.toJson(wrapper);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            System.err.println("[BeaconRange] Failed to save config: " + e);
        }
    }
}