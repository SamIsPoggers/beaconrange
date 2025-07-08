package io.github.samispoggers.beaconrange.config;

public class ModConfig {
    public enum HeightMode {
        radius,
        world,
        custom
    }

    public HeightMode heightMode = HeightMode.radius;
    public int customYLevel = 100;
}
