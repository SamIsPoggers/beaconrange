package io.github.samispoggers.bbb_selftest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.samispoggers.beacon_bounding_boxes.BeaconBoundingBoxes;
import io.github.samispoggers.beacon_bounding_boxes.client.BeaconBoundingBoxesClient;
import io.github.samispoggers.beacon_bounding_boxes.client.BoundingBoxRenderer;
import io.github.samispoggers.beacon_bounding_boxes.compat.IrisCompat;
import io.github.samispoggers.beacon_bounding_boxes.compat.ScreenCompat;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig;
import io.github.samispoggers.beacon_bounding_boxes.config.ConfigScreenFactory;
import io.github.samispoggers.beacon_bounding_boxes.config.FallbackConfigScreen;
import io.github.samispoggers.beacon_bounding_boxes.config.YaclConfigScreen;
import io.github.samispoggers.beacon_bounding_boxes.mixin.BeaconBlockEntityAccessor;
import io.github.samispoggers.beacon_bounding_boxes.mixin.ConduitBlockEntityAccessor;
import io.github.samispoggers.beacon_bounding_boxes.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public final class SelfTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger("BBBSelfTest");
    private static boolean completed = false;
    private static int ticksWaited = 0;

    private SelfTestRunner() {
    }

    public static void onClientTick(Minecraft client) {
        if (completed || !Boolean.getBoolean("bbb.selfTest")) {
            return;
        }
        ticksWaited++;
        if (hasOverlay(client) && ticksWaited < 300) {
            return;
        }

        completed = true;
        Path outputPath = Paths.get(System.getProperty("bbb.selfTestOutput", "self-test-result.txt"));
        try {
            runAssertions(client);
            writeResult(outputPath, "PASS\n");
            LOGGER.info("BBB self-test PASSED ({})", outputPath);
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String msg = "FAIL: " + t.getMessage() + "\n" + sw;
            writeResult(outputPath, msg);
            LOGGER.error("BBB self-test FAILED", t);
        } finally {
            client.stop();
        }
    }

    private static void runAssertions(Minecraft client) throws Exception {
        // 1. Mod loaded checks
        assertTrue(Services.get().isModLoaded(BeaconBoundingBoxes.MOD_ID),
                "Main mod '" + BeaconBoundingBoxes.MOD_ID + "' is not loaded");
        String expectedLoader = System.getProperty("bbb.expectedLoader");
        if (expectedLoader != null && !expectedLoader.isBlank()) {
            assertTrue(expectedLoader.equals(Services.get().getLoaderName()),
                    "Expected loader '" + expectedLoader + "', got '" + Services.get().getLoaderName() + "'");
        }

        if (Boolean.getBoolean("bbb.expectSodiumIris")) {
            assertTrue(Services.get().isModLoaded("sodium"), "Expected 'sodium' mod to be loaded");
            assertTrue(Services.get().isModLoaded("iris"), "Expected 'iris' mod to be loaded");
        }

        assertTrue(client.getResourceManager().getResource(BoundingBoxRenderer.WHITE_TEXTURE).isPresent(),
                "Missing bundled white texture resource: " + BoundingBoxRenderer.WHITE_TEXTURE);
        Identifier iconLocation = Identifier.fromNamespaceAndPath(BeaconBoundingBoxes.MOD_ID, "icon.png");
        assertTrue(client.getResourceManager().getResource(iconLocation).isPresent(),
                "Missing bundled mod icon resource: " + iconLocation);

        // 2. Force-load mixin targets and verify all 4 mixins applied
        Class.forName(BeaconRenderer.class.getName());
        Class.forName(ConduitRenderer.class.getName());
        Class.forName(BeaconBlockEntity.class.getName());
        Class.forName(ConduitBlockEntity.class.getName());

        assertTrue(BeaconBlockEntityAccessor.class.isAssignableFrom(BeaconBlockEntity.class),
                "BeaconBlockEntityAccessor mixin did not apply");
        assertTrue(ConduitBlockEntityAccessor.class.isAssignableFrom(ConduitBlockEntity.class),
                "ConduitBlockEntityAccessor mixin did not apply");
        assertTrue(hasMethod(BeaconRenderer.class, "bbb$onSubmitBeacon"),
                "MixinBeaconRenderer did not inject bbb$onSubmitBeacon");
        assertTrue(hasMethod(ConduitRenderer.class, "bbb$onSubmitConduit"),
                "MixinConduitRenderer did not inject bbb$onSubmitConduit");

        // 3. Runtime keybind resolution check (GLFW vs SDL agnostic)
        assertTrue(BeaconBoundingBoxesClient.toggleBeaconKey != null, "toggleBeaconKey is null");
        assertTrue(BeaconBoundingBoxesClient.toggleConduitKey != null, "toggleConduitKey is null");
        assertTrue("key.keyboard.g".equals(BeaconBoundingBoxesClient.toggleBeaconKey.getDefaultKey().getName()),
                "Unexpected default beacon key: " + BeaconBoundingBoxesClient.toggleBeaconKey.getDefaultKey().getName());
        assertTrue("key.keyboard.h".equals(BeaconBoundingBoxesClient.toggleConduitKey.getDefaultKey().getName()),
                "Unexpected default conduit key: " + BeaconBoundingBoxesClient.toggleConduitKey.getDefaultKey().getName());

        // 4. Config load/save/reload round-trip & sanitize()
        BeaconBoundingBoxesConfig cfg = BeaconBoundingBoxesConfig.get();
        var origHeight = cfg.heightMode;
        int origY = cfg.customYLevel;
        var origBox = cfg.boxMode;
        var origConduit = cfg.conduitMode;
        try {
            cfg.heightMode = BeaconBoundingBoxesConfig.HeightMode.CUSTOM;
            cfg.customYLevel = 123;
            cfg.boxMode = BeaconBoundingBoxesConfig.BoxMode.BOX;
            cfg.conduitMode = BeaconBoundingBoxesConfig.ConduitMode.FILLED;
            BeaconBoundingBoxesConfig.save();
            assertTrue(Files.exists(BeaconBoundingBoxesConfig.getConfigPath()), "Config file was not written");

            cfg.resetToDefaults();
            BeaconBoundingBoxesConfig.load();
            BeaconBoundingBoxesConfig reloaded = BeaconBoundingBoxesConfig.get();
            assertTrue(reloaded.heightMode == BeaconBoundingBoxesConfig.HeightMode.CUSTOM,
                    "Reloaded heightMode mismatch");
            assertTrue(reloaded.customYLevel == 123, "Reloaded customYLevel mismatch");
            assertTrue(reloaded.boxMode == BeaconBoundingBoxesConfig.BoxMode.BOX, "Reloaded boxMode mismatch");
            assertTrue(reloaded.conduitMode == BeaconBoundingBoxesConfig.ConduitMode.FILLED,
                    "Reloaded conduitMode mismatch");

            reloaded.conduitMode = null;
            reloaded.sanitize();
            assertTrue(reloaded.conduitMode == BeaconBoundingBoxesConfig.ConduitMode.OUTLINE,
                    "Expected sanitize() to default null conduitMode to OUTLINE");
        } finally {
            BeaconBoundingBoxesConfig.get().heightMode = origHeight;
            BeaconBoundingBoxesConfig.get().customYLevel = origY;
            BeaconBoundingBoxesConfig.get().boxMode = origBox;
            BeaconBoundingBoxesConfig.get().conduitMode = origConduit;
            BeaconBoundingBoxesConfig.save();
        }

        // 5. Config screen factory & ScreenCompat lifecycle
        Screen factoryScreen = ConfigScreenFactory.create(null);
        assertTrue(factoryScreen != null, "ConfigScreenFactory.create returned null");
        ScreenCompat.setScreen(client, factoryScreen);

        Screen fallbackScreen = new FallbackConfigScreen(null);
        assertTrue(fallbackScreen != null, "FallbackConfigScreen returned null");
        ScreenCompat.setScreen(client, fallbackScreen);

        if (Services.get().isModLoaded("yet_another_config_lib_v3")) {
            Screen yaclScreen = YaclConfigScreen.create(null);
            assertTrue(yaclScreen != null, "YaclConfigScreen.create returned null");
            ScreenCompat.setScreen(client, yaclScreen);
        }
        ScreenCompat.setScreen(client, null);

        // 6. Rendering pipeline & Sodium/Iris vertex contract validation
        assertTrue(BoundingBoxRenderer.getFillRenderType() != null, "Fill RenderType is null");
        assertTrue(BoundingBoxRenderer.getShaderOutlineRenderType() != null, "Shader outline RenderType is null");
        assertTrue(RenderTypes.lines() != null, "RenderTypes.lines() is null");
        assertTrue(!IrisCompat.isRenderingShadowPass(), "Expected non-shadow pass outside rendering");
        IrisCompat.isShaderPackInUse();

        PoseStack poseStack = new PoseStack();
        AABB testBox = new AABB(-20, -20, -20, 21, 21, 21);
        Vec3 camRel = new Vec3(0.0, 20.0, 60.0);

        RecordingConsumer quadConsumer = new RecordingConsumer();
        BoundingBoxRenderer.drawBoxFaces(poseStack.last(), quadConsumer.proxy(), testBox, 0.2f, 0.6f, 0.9f, 0.3f);
        assertTrue(quadConsumer.vertexCount == 48, "Expected 48 double-sided quad vertices, got " + quadConsumer.vertexCount);
        assertTrue(quadConsumer.normalCalls == 48, "Expected 48 normal calls on box faces, got " + quadConsumer.normalCalls);
        assertTrue(!quadConsumer.invalidNormalSeen, "Box faces emitted invalid normal");

        RecordingConsumer sphereFaceConsumer = new RecordingConsumer();
        BoundingBoxRenderer.drawSphereFaces(poseStack.last(), sphereFaceConsumer.proxy(), 31.0f, 0.4f, 0.8f, 0.5f, 0.25f);
        assertTrue(sphereFaceConsumer.vertexCount > 0 && sphereFaceConsumer.vertexCount % 4 == 0,
                "Invalid sphere face vertex count: " + sphereFaceConsumer.vertexCount);
        assertTrue(sphereFaceConsumer.normalCalls == sphereFaceConsumer.vertexCount,
                "Sphere faces missing normals: " + sphereFaceConsumer.normalCalls + "/" + sphereFaceConsumer.vertexCount);
        assertTrue(!sphereFaceConsumer.invalidNormalSeen, "Sphere faces emitted invalid normal");

        RecordingConsumer quadLineBoxConsumer = new RecordingConsumer();
        BoundingBoxRenderer.renderQuadLineBox(poseStack.last(), quadLineBoxConsumer.proxy(), testBox, camRel, 0.2f, 0.6f, 0.9f, 1.0f);
        assertTrue(quadLineBoxConsumer.vertexCount >= 48 && quadLineBoxConsumer.vertexCount % 4 == 0,
                "Expected >= 48 quad line box vertices divisible by 4, got " + quadLineBoxConsumer.vertexCount);
        assertTrue(quadLineBoxConsumer.normalCalls == quadLineBoxConsumer.vertexCount,
                "Quad line box missing normals: " + quadLineBoxConsumer.normalCalls + "/" + quadLineBoxConsumer.vertexCount);
        assertTrue(!quadLineBoxConsumer.invalidNormalSeen, "Quad line box emitted invalid normal");

        RecordingConsumer sphereQuadConsumer = new RecordingConsumer();
        BoundingBoxRenderer.drawSphereQuadOutline(poseStack.last(), sphereQuadConsumer.proxy(), 31.0f, camRel, 0.4f, 0.8f, 0.5f, 48);
        assertTrue(sphereQuadConsumer.vertexCount > 0 && sphereQuadConsumer.vertexCount % 4 == 0,
                "Invalid sphere quad outline vertex count: " + sphereQuadConsumer.vertexCount);
        assertTrue(sphereQuadConsumer.normalCalls == sphereQuadConsumer.vertexCount,
                "Sphere quad outline missing normals: " + sphereQuadConsumer.normalCalls + "/" + sphereQuadConsumer.vertexCount);
        assertTrue(!sphereQuadConsumer.invalidNormalSeen, "Sphere quad outline emitted invalid normal");

        RecordingConsumer lineConsumer = new RecordingConsumer();
        BoundingBoxRenderer.renderLineBox(poseStack.last(), lineConsumer.proxy(), testBox, 0.2f, 0.6f, 0.9f, 1.0f);
        assertTrue(lineConsumer.vertexCount == 24, "Expected 24 line box vertices, got " + lineConsumer.vertexCount);
        assertTrue(lineConsumer.normalCalls == 24, "Expected 24 normal calls on line box, got " + lineConsumer.normalCalls);
        assertTrue(lineConsumer.lineWidthCalls == 24, "Expected 24 lineWidth calls on line box");
        assertTrue(!lineConsumer.invalidNormalSeen, "Line box emitted invalid/zero/NaN normal");

        RecordingConsumer sphereConsumer = new RecordingConsumer();
        BoundingBoxRenderer.drawSphereOutline(poseStack.last(), sphereConsumer.proxy(), 31.0f, 0.4f, 0.8f, 0.5f, 48);
        assertTrue(sphereConsumer.vertexCount > 0 && sphereConsumer.vertexCount % 2 == 0,
                "Invalid sphere vertex count: " + sphereConsumer.vertexCount);
        assertTrue(sphereConsumer.normalCalls == sphereConsumer.vertexCount,
                "Sphere lines missing normals: " + sphereConsumer.normalCalls + "/" + sphereConsumer.vertexCount);
        assertTrue(sphereConsumer.lineWidthCalls == sphereConsumer.vertexCount,
                "Sphere lines missing lineWidth calls");
        assertTrue(!sphereConsumer.invalidNormalSeen, "Sphere outline emitted invalid/zero/NaN normal");

        // 7. Live conduit permanence checks (no early frustum or 64-block unloading)
        ConduitBlockEntity testConduit = new ConduitBlockEntity(BlockPos.ZERO, Blocks.CONDUIT.defaultBlockState());
        BlockEntityRenderer<ConduitBlockEntity, ?> conduitRenderer =
                client.getBlockEntityRenderDispatcher().getRenderer(testConduit);
        assertTrue(conduitRenderer != null, "ConduitRenderer was null");
        assertTrue(conduitRenderer.shouldRenderOffScreen(), "ConduitRenderer.shouldRenderOffScreen() must return true");
        int expectedViewDist = client.options.getEffectiveRenderDistance() * 16;
        assertTrue(conduitRenderer.getViewDistance() == expectedViewDist,
                "Expected ConduitRenderer.getViewDistance() == " + expectedViewDist + ", got " + conduitRenderer.getViewDistance());
        if (conduitRenderer.getViewDistance() > 80) {
            assertTrue(conduitRenderer.shouldRender(testConduit, new Vec3(80.0, 0.0, 0.0)),
                    "ConduitRenderer must continue rendering beyond 64 blocks when inside render distance");
        }
        assertTrue(!conduitRenderer.shouldRender(testConduit, new Vec3(conduitRenderer.getViewDistance() + 32.0, 0.0, 0.0)),
                "ConduitRenderer must stop rendering once conduit center is outside render distance");

        if ("neoforge".equals(Services.get().getLoaderName())) {
            assertTrue(hasMethod(ConduitRenderer.class, "bbb$getConduitRenderBoundingBox"),
                    "MixinConduitRenderer did not inject bbb$getConduitRenderBoundingBox on NeoForge");
            assertTrue(hasMethod(BeaconRenderer.class, "bbb$getBeaconRenderBoundingBox"),
                    "MixinBeaconRenderer did not inject bbb$getBeaconRenderBoundingBox on NeoForge");
            Method getRenderBoundingBox = conduitRenderer.getClass().getMethod("getRenderBoundingBox", ConduitBlockEntity.class);
            AABB conduitAabb = (AABB) getRenderBoundingBox.invoke(conduitRenderer, testConduit);
            assertTrue(conduitAabb != null
                            && conduitAabb.minX <= -99.0 && conduitAabb.minY <= -99.0 && conduitAabb.minZ <= -99.0
                            && conduitAabb.maxX >= 100.0 && conduitAabb.maxY >= 100.0 && conduitAabb.maxZ >= 100.0,
                    "Expected expanded ConduitRenderer render bounding box on NeoForge, got " + conduitAabb);
        }

        // 8. Translations loaded
        Language lang = Language.getInstance();
        assertTrue(lang.has("key.category.beacon_bounding_boxes.keybinds"), "Missing key category translation");
        assertTrue(lang.has("key.beacon_bounding_boxes.toggle_beacon"), "Missing toggle_beacon translation");
        assertTrue(lang.has("message.beacon_bounding_boxes.beacon_toggle"), "Missing beacon_toggle translation");
        assertTrue(lang.has("config.beacon_bounding_boxes.title"), "Missing config title translation");
        assertTrue(lang.has("config.beacon_bounding_boxes.option.conduit_mode"), "Missing conduit_mode translation");
        assertTrue(lang.has("config.beacon_bounding_boxes.option.conduit_mode.description"),
                "Missing conduit_mode description translation");
        assertTrue(lang.has("config.beacon_bounding_boxes.conduit_mode.outline"),
                "Missing conduit_mode.outline translation");
        assertTrue(lang.has("config.beacon_bounding_boxes.conduit_mode.filled"),
                "Missing conduit_mode.filled translation");
    }

    private static boolean hasOverlay(Minecraft client) {
        try {
            if (client.gui != null) {
                var guiOverlayMethod = client.gui.getClass().getMethod("overlay");
                return guiOverlayMethod.invoke(client.gui) != null;
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            LOGGER.debug("Gui#overlay check failed", t);
        }
        try {
            var mcGetOverlayMethod = Minecraft.class.getMethod("getOverlay");
            return mcGetOverlayMethod.invoke(client) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasMethod(Class<?> clazz, String methodName) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(methodName) || m.getName().endsWith("$" + methodName));
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void writeResult(Path outputPath, String content) {
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to write self-test result to {}", outputPath, e);
        }
    }

    private static final class RecordingConsumer implements InvocationHandler {
        int vertexCount = 0;
        int normalCalls = 0;
        int lineWidthCalls = 0;
        boolean invalidNormalSeen = false;

        VertexConsumer proxy() {
            return (VertexConsumer) Proxy.newProxyInstance(
                    VertexConsumer.class.getClassLoader(),
                    new Class<?>[]{VertexConsumer.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            String name = method.getName();
            if ("addVertex".equals(name) && args != null && args.length == 3) {
                vertexCount++;
            } else if ("setNormal".equals(name) && args != null && args.length == 3) {
                normalCalls++;
                float nx = (float) args[0];
                float ny = (float) args[1];
                float nz = (float) args[2];
                float lenSq = nx * nx + ny * ny + nz * nz;
                if (Float.isNaN(lenSq) || Float.isInfinite(lenSq) || lenSq < 0.01f) {
                    invalidNormalSeen = true;
                }
            } else if ("setLineWidth".equals(name)) {
                lineWidthCalls++;
            }
            if (VertexConsumer.class.isAssignableFrom(method.getReturnType())) {
                return proxy;
            }
            Class<?> ret = method.getReturnType();
            if (ret == boolean.class) return false;
            if (ret == int.class) return 0;
            if (ret == float.class) return 0.0f;
            if (ret == double.class) return 0.0d;
            return null;
        }
    }
}
