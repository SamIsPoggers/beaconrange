package io.github.samispoggers.beaconrange.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class BeaconRangeClient implements ClientModInitializer {

    private static KeyBinding toggleBeaconKey;
    private static KeyBinding toggleConduitKey;
    public static boolean renderBeaconBounds = false;
    public static boolean renderConduitBounds = false;

    private final static KeyBinding.Category category =
            KeyBinding.Category.create(
                    Identifier.of("beaconrange", "main")
            );

    @Override
    public void onInitializeClient() {
        // Register the keybinding (e.g., key G)
        toggleBeaconKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.beaconrange.toggle_beacon",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                category
        ));

        // Register the keybinding (e.g., key G)
        toggleConduitKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.beaconrange.toggle_conduit",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleBeaconKey.wasPressed()) {
                renderBeaconBounds = !renderBeaconBounds;

                // Build the colored message
                Text message = Text.literal("Beacon Bounding Boxes are now ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(renderBeaconBounds ? "ON" : "OFF")
                                .formatted(renderBeaconBounds ? Formatting.GREEN : Formatting.RED)
                        );

                // Send to action bar
                if (client.player != null) {
                    client.player.sendMessage(message, true); // `true` sends to action bar
                }
            }

            while (toggleConduitKey.wasPressed()) {
                renderConduitBounds = !renderConduitBounds;

                // Build the colored message
                Text message = Text.literal("Conduit Bounding Boxes are now ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(renderConduitBounds ? "ON" : "OFF")
                                .formatted(renderConduitBounds ? Formatting.GREEN : Formatting.RED)
                        );

                // Send to action bar
                if (client.player != null) {
                    client.player.sendMessage(message, true); // `true` sends to action bar
                }
            }
        });
    }
}