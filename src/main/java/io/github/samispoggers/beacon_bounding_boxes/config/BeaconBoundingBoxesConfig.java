package io.github.samispoggers.beacon_bounding_boxes.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public class BeaconBoundingBoxesConfig {

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("beacon_bounding_boxes.json");

    public static final ConfigClassHandler<BeaconBoundingBoxesConfig> HANDLER =
            ConfigClassHandler.createBuilder(BeaconBoundingBoxesConfig.class)
                    .serializer(config -> GsonConfigSerializerBuilder.create(config)
                            .setPath(CONFIG_PATH)
                            .build())
                    .build();

    public static BeaconBoundingBoxesConfig get() {
        return HANDLER.instance();
    }

    public static void load() {
        HANDLER.load();
    }

    public static void save() {
        HANDLER.save();
    }

    @SerialEntry
    public HeightMode heightMode = HeightMode.RADIUS;

    @SerialEntry
    public int customYLevel = 64;

    @SerialEntry
    public BoxMode boxMode = BoxMode.OUTLINE;

    public static Screen createScreen(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Beacon Bounding Boxes"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("General"))
                        .group(OptionGroup.createBuilder()
                                .name(Component.literal("Rendering"))
                                .option(Option.<HeightMode>createBuilder()
                                        .name(Component.literal("Height Mode"))
                                        .description(OptionDescription.of(
                                                Component.literal("How tall the beacon bounding box should be")
                                        ))
                                        .binding(
                                                HeightMode.RADIUS,
                                                () -> get().heightMode,
                                                v -> get().heightMode = v
                                        )
                                        .controller(opt -> EnumControllerBuilder.create(opt)
                                                .enumClass(HeightMode.class))
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.literal("Custom Y Level"))
                                        .description(OptionDescription.of(
                                                Component.literal("Top Y level used when Height Mode is set to Custom")
                                        ))
                                        .binding(
                                                64,
                                                () -> get().customYLevel,
                                                v -> get().customYLevel = v
                                        )
                                        .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                                                .min(-64)
                                                .max(319))
                                        .build())
                                .option(Option.<BoxMode>createBuilder()
                                        .name(Component.literal("Box Mode"))
                                        .description(OptionDescription.of(
                                                Component.literal("How the bounding box is rendered")
                                        ))
                                        .binding(
                                                BoxMode.OUTLINE,
                                                () -> get().boxMode,
                                                v -> get().boxMode = v
                                        )
                                        .controller(opt -> EnumControllerBuilder.create(opt)
                                                .enumClass(BoxMode.class))
                                        .build())

                                .build())
                        .build())
                .save(BeaconBoundingBoxesConfig::save)
                .build()
                .generateScreen(parent);
    }

    public enum HeightMode {
        RADIUS,
        WORLD_HEIGHT,
        CUSTOM
    }

    public enum BoxMode {
        OUTLINE,
        BOX
    }
}
