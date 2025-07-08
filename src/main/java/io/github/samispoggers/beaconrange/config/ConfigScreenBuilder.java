package io.github.samispoggers.beaconrange.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ConfigScreenBuilder {

    public static Screen build(Screen parent) {
        ModConfig config = ConfigManager.config;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("YourMod Config"));

        builder.setSavingRunnable(ConfigManager::save); // Save to disk on exit

        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // Enum for height mode
        general.addEntry(entryBuilder
                .startEnumSelector(Text.literal("Height Mode"), ModConfig.HeightMode.class, config.heightMode)
                .setDefaultValue(ModConfig.HeightMode.radius)
                .setTooltip(Text.literal("Choose how the vertical range is calculated."))
                .setSaveConsumer(newValue -> config.heightMode = newValue)
                .build());

        // Integer for custom y level
        general.addEntry(entryBuilder
                .startIntField(Text.literal("Custom Y Level"), config.customYLevel)
                .setDefaultValue(100)
                .setTooltip(Text.literal("Used only when Height Mode is set to 'custom'."))
                .setMin(1)
                .setMax(319)
                .setSaveConsumer(newValue -> config.customYLevel = newValue)
                .build());

        return builder.build();
    }
}
