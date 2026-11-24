package io.github.samispoggers.beacon_bounding_boxes.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig.BoxMode;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig.ConduitMode;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig.HeightMode;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class YaclConfigScreen {
    private YaclConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("config.beacon_bounding_boxes.title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("config.beacon_bounding_boxes.category.general"))
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("config.beacon_bounding_boxes.group.rendering"))
                                .option(Option.<HeightMode>createBuilder()
                                        .name(Component.translatable("config.beacon_bounding_boxes.option.height_mode"))
                                        .description(OptionDescription.of(
                                                Component.translatable("config.beacon_bounding_boxes.option.height_mode.description")
                                        ))
                                        .binding(
                                                HeightMode.RADIUS,
                                                () -> BeaconBoundingBoxesConfig.get().heightMode,
                                                v -> BeaconBoundingBoxesConfig.get().heightMode = v
                                        )
                                        .controller(opt -> EnumControllerBuilder.create(opt)
                                                .enumClass(HeightMode.class)
                                                .formatValue(HeightMode::displayName))
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("config.beacon_bounding_boxes.option.custom_y_level"))
                                        .description(OptionDescription.of(
                                                Component.translatable("config.beacon_bounding_boxes.option.custom_y_level.description")
                                        ))
                                        .binding(
                                                BeaconBoundingBoxesConfig.DEFAULT_CUSTOM_Y,
                                                () -> BeaconBoundingBoxesConfig.get().customYLevel,
                                                v -> BeaconBoundingBoxesConfig.get().customYLevel = v
                                        )
                                        .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                                                .min(BeaconBoundingBoxesConfig.MIN_CUSTOM_Y)
                                                .max(BeaconBoundingBoxesConfig.MAX_CUSTOM_Y))
                                        .build())
                                .option(Option.<BoxMode>createBuilder()
                                        .name(Component.translatable("config.beacon_bounding_boxes.option.box_mode"))
                                        .description(OptionDescription.of(
                                                Component.translatable("config.beacon_bounding_boxes.option.box_mode.description")
                                        ))
                                        .binding(
                                                BoxMode.OUTLINE,
                                                () -> BeaconBoundingBoxesConfig.get().boxMode,
                                                v -> BeaconBoundingBoxesConfig.get().boxMode = v
                                        )
                                        .controller(opt -> EnumControllerBuilder.create(opt)
                                                .enumClass(BoxMode.class)
                                                .formatValue(BoxMode::displayName))
                                        .build())
                                .option(Option.<ConduitMode>createBuilder()
                                        .name(Component.translatable("config.beacon_bounding_boxes.option.conduit_mode"))
                                        .description(OptionDescription.of(
                                                Component.translatable("config.beacon_bounding_boxes.option.conduit_mode.description")
                                        ))
                                        .binding(
                                                ConduitMode.OUTLINE,
                                                () -> BeaconBoundingBoxesConfig.get().conduitMode,
                                                v -> BeaconBoundingBoxesConfig.get().conduitMode = v
                                        )
                                        .controller(opt -> EnumControllerBuilder.create(opt)
                                                .enumClass(ConduitMode.class)
                                                .formatValue(ConduitMode::displayName))
                                        .build())
                                .build())
                        .build())
                .save(BeaconBoundingBoxesConfig::save)
                .build()
                .generateScreen(parent);
    }
}
