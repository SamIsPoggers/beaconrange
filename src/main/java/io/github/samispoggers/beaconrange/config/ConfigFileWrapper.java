package io.github.samispoggers.beaconrange.config;

public class ConfigFileWrapper {
    public ModConfig.HeightMode heightMode;
    public String _heightMode_comment = "Valid values: radius, world, custom";

    public ModConfig.BoxMode boxMode;
    public String _boxMode_comment = "Valid values: outline, box";

    public int customYLevel;

    public ConfigFileWrapper() {
        // Default constructor for Gson
    }

    public ConfigFileWrapper(ModConfig config) {
        this.heightMode = config.heightMode;
        this.boxMode = config.boxMode;
        this.customYLevel = config.customYLevel;
    }

    public ModConfig toConfig() {
        ModConfig config = new ModConfig();

        if (this.heightMode != null)
            config.heightMode = this.heightMode;

        if (this.boxMode != null)
            config.boxMode = this.boxMode;

        config.customYLevel = this.customYLevel;

        return config;
    }
}
