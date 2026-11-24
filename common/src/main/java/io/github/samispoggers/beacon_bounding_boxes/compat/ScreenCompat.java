package io.github.samispoggers.beacon_bounding_boxes.compat;

import io.github.samispoggers.beacon_bounding_boxes.BeaconBoundingBoxes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class ScreenCompat {
    private static final MethodHandle GUI_SET_SCREEN = resolveGuiSetScreen();
    private static final MethodHandle MC_SET_SCREEN = resolveMcSetScreen();

    private ScreenCompat() {
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        if (minecraft == null) {
            return;
        }
        try {
            if (GUI_SET_SCREEN != null && minecraft.gui != null) {
                GUI_SET_SCREEN.invokeExact(minecraft.gui, screen);
                return;
            }
            if (MC_SET_SCREEN != null) {
                MC_SET_SCREEN.invokeExact(minecraft, screen);
            }
        } catch (Throwable t) {
            BeaconBoundingBoxes.LOGGER.error("Failed to switch screen", t);
        }
    }

    private static MethodHandle resolveGuiSetScreen() {
        try {
            return MethodHandles.publicLookup().findVirtual(
                    Gui.class,
                    "setScreen",
                    MethodType.methodType(void.class, Screen.class)
            );
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }

    private static MethodHandle resolveMcSetScreen() {
        try {
            return MethodHandles.publicLookup().findVirtual(
                    Minecraft.class,
                    "setScreen",
                    MethodType.methodType(void.class, Screen.class)
            );
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }
}
