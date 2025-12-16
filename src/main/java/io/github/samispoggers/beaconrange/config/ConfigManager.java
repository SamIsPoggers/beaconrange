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
        ConfigFileWrapper wrapper = null;

        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);

                if (!json.isBlank()) {
                    wrapper = GSON.fromJson(json, ConfigFileWrapper.class);
                }
            } catch (Exception e) {
                System.err.println("[BeaconRange] Failed to load config, regenerating: " + e);
            }
        }

        if (wrapper == null) {
            config = new ModConfig();
            save();
        } else {
            config = wrapper.toConfig();
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