package io.github.samispoggers.beacon_bounding_boxes.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.samispoggers.beacon_bounding_boxes.BeaconBoundingBoxes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class BeaconBoundingBoxesClient {
    public static final Identifier KEYBIND_CATEGORY_ID =
            Identifier.fromNamespaceAndPath(BeaconBoundingBoxes.MOD_ID, "keybinds");

    public static KeyMapping toggleBeaconKey;
    public static KeyMapping toggleConduitKey;

    public static boolean renderBeaconBounds = false;
    public static boolean renderConduitBounds = false;

    private BeaconBoundingBoxesClient() {
    }

    public static KeyMapping createToggleBeaconKey(KeyMapping.Category category) {
        int defaultKeyCode = InputConstants.getKey("key.keyboard.g").getValue();
        toggleBeaconKey = new KeyMapping(
                "key.beacon_bounding_boxes.toggle_beacon",
                defaultKeyCode,
                category
        );
        return toggleBeaconKey;
    }

    public static KeyMapping createToggleConduitKey(KeyMapping.Category category) {
        int defaultKeyCode = InputConstants.getKey("key.keyboard.h").getValue();
        toggleConduitKey = new KeyMapping(
                "key.beacon_bounding_boxes.toggle_conduit",
                defaultKeyCode,
                category
        );
        return toggleConduitKey;
    }

    public static void onClientTick(Minecraft client) {
        if (toggleBeaconKey != null) {
            while (toggleBeaconKey.consumeClick()) {
                renderBeaconBounds = !renderBeaconBounds;
                sendToggleOverlay(client, "message.beacon_bounding_boxes.beacon_toggle", renderBeaconBounds);
            }
        }

        if (toggleConduitKey != null) {
            while (toggleConduitKey.consumeClick()) {
                renderConduitBounds = !renderConduitBounds;
                sendToggleOverlay(client, "message.beacon_bounding_boxes.conduit_toggle", renderConduitBounds);
            }
        }
    }

    private static void sendToggleOverlay(Minecraft client, String messageKey, boolean enabled) {
        if (client.player == null) {
            return;
        }
        Component stateText = Component.translatable(
                enabled ? "message.beacon_bounding_boxes.state.on" : "message.beacon_bounding_boxes.state.off"
        ).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);

        Component message = Component.translatable(messageKey, stateText).withStyle(ChatFormatting.GRAY);
        client.player.sendOverlayMessage(message);
    }
}
