package io.github.samispoggers.beaconrange.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class BeaconRangeClient implements ClientModInitializer {

    private static KeyBinding toggleBeaconKey;
    private static KeyBinding toggleConduitKey;
    public static boolean myToggleBeaconVariable = false;
    public static boolean myToggleConduitVariable = false;

    @Override
    public void onInitializeClient() {
        // Register the keybinding (e.g., key G)
        toggleBeaconKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.beaconrange.toggle_beacon",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.beaconrange"
        ));

        // Register the keybinding (e.g., key G)
        toggleConduitKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.beaconrange.toggle_conduit",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.beaconrange"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleBeaconKey.wasPressed()) {
                myToggleBeaconVariable = !myToggleBeaconVariable;

                // Build the colored message
                Text message = Text.literal("Beacon Bounding Boxes are now ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(myToggleBeaconVariable ? "ON" : "OFF")
                                .formatted(myToggleBeaconVariable ? Formatting.GREEN : Formatting.RED)
                        );

                // Send to action bar
                if (client.player != null) {
                    client.player.sendMessage(message, true); // `true` sends to action bar
                }
            }

            while (toggleConduitKey.wasPressed()) {
                myToggleConduitVariable = !myToggleConduitVariable;

                // Build the colored message
                Text message = Text.literal("Conduit Bounding Boxes are now ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(myToggleConduitVariable ? "ON" : "OFF")
                                .formatted(myToggleConduitVariable ? Formatting.GREEN : Formatting.RED)
                        );

                // Send to action bar
                if (client.player != null) {
                    client.player.sendMessage(message, true); // `true` sends to action bar
                }
            }
        });
    }
}