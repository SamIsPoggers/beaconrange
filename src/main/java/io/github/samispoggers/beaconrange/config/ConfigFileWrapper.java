package io.github.samispoggers.beaconrange.config;

public class ConfigFileWrapper {
    public ModConfig.HeightMode heightMode;
    public String _heightMode_comment = "Valid values: radius, world, custom";

    public int customYLevel;

    public ConfigFileWrapper() {
        // Default constructor for Gson
    }

    public ConfigFileWrapper(ModConfig config) {
        this.heightMode = config.heightMode;
        this.customYLevel = config.customYLevel;
    }

    public ModConfig toConfig() {
        ModConfig config = new ModConfig();
        config.heightMode = this.heightMode;
        config.customYLevel = this.customYLevel;
        return config;
    }
}
