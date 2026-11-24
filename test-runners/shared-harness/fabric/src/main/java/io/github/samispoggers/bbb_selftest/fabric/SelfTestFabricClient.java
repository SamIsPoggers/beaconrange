package io.github.samispoggers.bbb_selftest.fabric;

import io.github.samispoggers.bbb_selftest.SelfTestRunner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class SelfTestFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(SelfTestRunner::onClientTick);
    }
}
