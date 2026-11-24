package io.github.samispoggers.beacon_bounding_boxes.config;

import io.github.samispoggers.beacon_bounding_boxes.compat.ScreenCompat;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig.BoxMode;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig.ConduitMode;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig.HeightMode;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

public class FallbackConfigScreen extends Screen {
    private final Screen parent;

    private HeightMode heightMode;
    private int customYLevel;
    private BoxMode boxMode;
    private ConduitMode conduitMode;

    private CycleButton<HeightMode> heightModeButton;
    private CustomYSlider customYSlider;
    private CycleButton<BoxMode> boxModeButton;
    private CycleButton<ConduitMode> conduitModeButton;

    public FallbackConfigScreen(Screen parent) {
        super(Component.translatable("config.beacon_bounding_boxes.title"));
        this.parent = parent;
        BeaconBoundingBoxesConfig current = BeaconBoundingBoxesConfig.get();
        this.heightMode = current.heightMode;
        this.customYLevel = current.customYLevel;
        this.boxMode = current.boxMode;
        this.conduitMode = current.conduitMode;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int rowWidth = 240;
        int colX = centerX - rowWidth / 2;
        int titleWidth = this.font.width(this.title);

        this.addRenderableOnly(new StringWidget(
                centerX - titleWidth / 2,
                20,
                titleWidth,
                9,
                this.title,
                this.font
        ));

        int startY = Math.max(44, this.height / 4 - 12);
        int rowSpacing = 26;

        this.heightModeButton = this.addRenderableWidget(
                CycleButton.<HeightMode>builder(HeightMode::displayName, this.heightMode)
                        .withValues(HeightMode.values())
                        .withTooltip(mode -> Tooltip.create(
                                Component.translatable("config.beacon_bounding_boxes.option.height_mode.description")
                        ))
                        .create(
                                colX,
                                startY,
                                rowWidth,
                                20,
                                Component.translatable("config.beacon_bounding_boxes.option.height_mode"),
                                (button, value) -> this.heightMode = value
                        )
        );

        this.customYSlider = this.addRenderableWidget(new CustomYSlider(
                colX,
                startY + rowSpacing,
                rowWidth,
                20,
                this.customYLevel,
                value -> this.customYLevel = value
        ));
        this.customYSlider.setTooltip(Tooltip.create(
                Component.translatable("config.beacon_bounding_boxes.option.custom_y_level.description")
        ));

        this.boxModeButton = this.addRenderableWidget(
                CycleButton.<BoxMode>builder(BoxMode::displayName, this.boxMode)
                        .withValues(BoxMode.values())
                        .withTooltip(mode -> Tooltip.create(
                                Component.translatable("config.beacon_bounding_boxes.option.box_mode.description")
                        ))
                        .create(
                                colX,
                                startY + rowSpacing * 2,
                                rowWidth,
                                20,
                                Component.translatable("config.beacon_bounding_boxes.option.box_mode"),
                                (button, value) -> this.boxMode = value
                        )
        );

        this.conduitModeButton = this.addRenderableWidget(
                CycleButton.<ConduitMode>builder(ConduitMode::displayName, this.conduitMode)
                        .withValues(ConduitMode.values())
                        .withTooltip(mode -> Tooltip.create(
                                Component.translatable("config.beacon_bounding_boxes.option.conduit_mode.description")
                        ))
                        .create(
                                colX,
                                startY + rowSpacing * 3,
                                rowWidth,
                                20,
                                Component.translatable("config.beacon_bounding_boxes.option.conduit_mode"),
                                (button, value) -> this.conduitMode = value
                        )
        );

        int footerY = this.height - 32;
        int btnWidth = 76;
        int gap = 6;
        int totalFooterWidth = btnWidth * 3 + gap * 2;
        int footerStartX = centerX - totalFooterWidth / 2;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("config.beacon_bounding_boxes.reset"),
                        button -> {
                            this.heightMode = HeightMode.RADIUS;
                            this.customYLevel = BeaconBoundingBoxesConfig.DEFAULT_CUSTOM_Y;
                            this.boxMode = BoxMode.OUTLINE;
                            this.conduitMode = ConduitMode.OUTLINE;
                            this.heightModeButton.setValue(this.heightMode);
                            this.customYSlider.setIntValue(this.customYLevel);
                            this.boxModeButton.setValue(this.boxMode);
                            this.conduitModeButton.setValue(this.conduitMode);
                        })
                .bounds(footerStartX, footerY, btnWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        CommonComponents.GUI_CANCEL,
                        button -> this.onClose())
                .bounds(footerStartX + btnWidth + gap, footerY, btnWidth, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        CommonComponents.GUI_DONE,
                        button -> {
                            BeaconBoundingBoxesConfig cfg = BeaconBoundingBoxesConfig.get();
                            cfg.heightMode = this.heightMode;
                            cfg.customYLevel = this.customYLevel;
                            cfg.boxMode = this.boxMode;
                            cfg.conduitMode = this.conduitMode;
                            BeaconBoundingBoxesConfig.save();
                            this.onClose();
                        })
                .bounds(footerStartX + (btnWidth + gap) * 2, footerY, btnWidth, 20)
                .build());
    }

    @Override
    public void onClose() {
        ScreenCompat.setScreen(this.minecraft, this.parent);
    }

    private static final class CustomYSlider extends AbstractSliderButton {
        private static final int RANGE = BeaconBoundingBoxesConfig.MAX_CUSTOM_Y - BeaconBoundingBoxesConfig.MIN_CUSTOM_Y;
        private final IntConsumer onChange;
        private int currentValue;

        private CustomYSlider(int x, int y, int width, int height, int initialValue, IntConsumer onChange) {
            super(x, y, width, height, Component.empty(), toNormalized(initialValue));
            this.onChange = onChange;
            this.currentValue = clamp(initialValue);
            this.updateMessage();
        }

        private void setIntValue(int newValue) {
            this.currentValue = clamp(newValue);
            this.value = toNormalized(this.currentValue);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.translatable("config.beacon_bounding_boxes.option.custom_y_level")
                    .append(": ")
                    .append(String.valueOf(this.currentValue)));
        }

        @Override
        protected void applyValue() {
            this.currentValue = fromNormalized(this.value);
            this.onChange.accept(this.currentValue);
        }

        private static int clamp(int val) {
            return Math.max(BeaconBoundingBoxesConfig.MIN_CUSTOM_Y, Math.min(BeaconBoundingBoxesConfig.MAX_CUSTOM_Y, val));
        }

        private static double toNormalized(int val) {
            return (double) (clamp(val) - BeaconBoundingBoxesConfig.MIN_CUSTOM_Y) / (double) RANGE;
        }

        private static int fromNormalized(double normalized) {
            return clamp((int) Math.round(BeaconBoundingBoxesConfig.MIN_CUSTOM_Y + normalized * RANGE));
        }
    }
}
