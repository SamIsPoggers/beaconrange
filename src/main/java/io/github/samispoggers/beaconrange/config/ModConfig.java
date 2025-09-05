package io.github.samispoggers.beaconrange.config;

public class ModConfig {
    public enum HeightMode {
        radius,
        world,
        custom
    }

    public enum BoxMode{
        outline,
        box
    }

    public HeightMode heightMode = HeightMode.radius;
    public int customYLevel = 100;
    public BoxMode boxMode = BoxMode.box;
}
