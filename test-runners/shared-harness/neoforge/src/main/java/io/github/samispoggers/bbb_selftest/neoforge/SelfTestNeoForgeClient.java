package io.github.samispoggers.bbb_selftest.neoforge;

import io.github.samispoggers.bbb_selftest.SelfTestRunner;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "bbb_self_test", dist = Dist.CLIENT)
public final class SelfTestNeoForgeClient {
    public SelfTestNeoForgeClient() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> SelfTestRunner.onClientTick(Minecraft.getInstance()));
    }
}
