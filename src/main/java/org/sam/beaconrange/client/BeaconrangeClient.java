package org.sam.beaconrange.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class BeaconrangeClient implements ClientModInitializer {

    private static KeyBinding toggleKey;
    public static boolean myToggleVariable = false;

    @Override
    public void onInitializeClient() {
        // Register the keybinding (e.g., key G)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.beaconrange.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.beaconrange"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                myToggleVariable = !myToggleVariable;

                // Build the colored message
                Text message = Text.literal("Beacon Bounding Boxes are now ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal(myToggleVariable ? "ON" : "OFF")
                                .formatted(myToggleVariable ? Formatting.GREEN : Formatting.RED)
                        );

                // Send to action bar
                if (client.player != null) {
                    client.player.sendMessage(message, true); // `true` sends to action bar
                }
            }
        });
    }
}
