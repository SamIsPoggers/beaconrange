package io.github.samispoggers.beaconrange.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.samispoggers.beaconrange.client.BeaconRangeClient;
import io.github.samispoggers.beaconrange.config.ConfigManager;
import io.github.samispoggers.beaconrange.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.BeaconBlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Environment(EnvType.CLIENT)
@Mixin(BeaconBlockEntityRenderer.class)
public abstract class MixinBeaconBlockEntityRenderer {

    @Unique
    private static final RenderPipeline TRANSLUCENT_BOX_PIPELINE =
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/translucent_box")
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthWrite(false)
                    .build();

    @Unique
    private static final RenderLayer TRANSLUCENT_BOX = RenderLayer.of(
            "beaconrange:translucent_box",
            1536,
            false,
            true,
            TRANSLUCENT_BOX_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
                    .build(false)
    );

    @Unique
    private final Map<BlockPos, Vector3f> beaconColors = new HashMap<>();

    @Inject(method = "render(Lnet/minecraft/client/render/block/entity/state/BeaconBlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"))
    private void onRenderBeacon(
            BeaconBlockEntityRenderState beaconBlockEntityRenderState,
            MatrixStack matrixStack,
            OrderedRenderCommandQueue orderedRenderCommandQueue,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        if (!BeaconRangeClient.renderBeaconBounds) return;

        var world = MinecraftClient.getInstance().world;
        if (world == null) return;

        BlockEntity be = world.getBlockEntity(beaconBlockEntityRenderState.pos);
        if (!(be instanceof BeaconBlockEntity beacon)) return;


        renderBoundingBox(beacon, matrixStack, orderedRenderCommandQueue);
    }

    @Unique
    private void renderBoundingBox(
            BeaconBlockEntity beacon,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue
    ) {
        BlockPos blockPos = beacon.getPos();

        // Deterministic color per beacon
        Vector3f color = beaconColors.computeIfAbsent(blockPos, p -> {
            Random random = new Random(p.asLong());
            return new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
        });

        int level = ((BeaconBlockEntityAccessor) beacon).getLevel();
        if (level <= 0) return;

        Box box = getBox(beacon, level);

        matrices.push();
        matrices.translate(0.5, 0.5, 0.5); // Center on the beacon block

        if (ConfigManager.config.boxMode.equals(ModConfig.BoxMode.box)) {
            queue.submitCustom(matrices, TRANSLUCENT_BOX, (entry, consumer) ->
                drawBoxFaces(entry, consumer, box, color.x(), color.y(), color.z(), 0.3f)
            );
        }

        // Then render the outline on top
        queue.submitCustom(matrices, RenderLayer.getLines(), (entry, consumer) ->
                VertexRendering.drawBox(entry, consumer, box, color.x(), color.y(), color.z(), 1.0f)
        );

        matrices.pop();
    }

    @Unique
    private static @NotNull Box getBox(BeaconBlockEntity beacon, int level) {
        // Vanilla ranges
        int[] levelRanges = {0, 20, 30, 40, 50};
        int range = levelRanges[Math.min(level, levelRanges.length - 1)];

        int yMin = -range;
        int yMax = switch (ConfigManager.config.heightMode) {
            case world -> 319 - beacon.getPos().getY();
            case custom -> ConfigManager.config.customYLevel - beacon.getPos().getY();
            default -> range + 1;
        };

        Vec3d min = new Vec3d(-range, yMin, -range);
        Vec3d max = new Vec3d(range + 1, yMax, range + 1);
        return new Box(min, max);
    }

    @Unique
    private void drawBoxFaces(MatrixStack.Entry entry, VertexConsumer buffer, Box box,
                              float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // bottom (y = minY)
        addVertex(buffer, entry, minX, minY, minZ, r, g, b, a, 0, -1, 0);
        addVertex(buffer, entry, maxX, minY, minZ, r, g, b, a, 0, -1, 0);
        addVertex(buffer, entry, maxX, minY, maxZ, r, g, b, a, 0, -1, 0);
        addVertex(buffer, entry, minX, minY, maxZ, r, g, b, a, 0, -1, 0);

        // top (y = maxY)
        addVertex(buffer, entry, minX, maxY, minZ, r, g, b, a, 0, 1, 0);
        addVertex(buffer, entry, minX, maxY, maxZ, r, g, b, a, 0, 1, 0);
        addVertex(buffer, entry, maxX, maxY, maxZ, r, g, b, a, 0, 1, 0);
        addVertex(buffer, entry, maxX, maxY, minZ, r, g, b, a, 0, 1, 0);

        // north (z = minZ)
        addVertex(buffer, entry, minX, minY, minZ, r, g, b, a, 0, 0, -1);
        addVertex(buffer, entry, minX, maxY, minZ, r, g, b, a, 0, 0, -1);
        addVertex(buffer, entry, maxX, maxY, minZ, r, g, b, a, 0, 0, -1);
        addVertex(buffer, entry, maxX, minY, minZ, r, g, b, a, 0, 0, -1);

        // south (z = maxZ)
        addVertex(buffer, entry, minX, minY, maxZ, r, g, b, a, 0, 0, 1);
        addVertex(buffer, entry, maxX, minY, maxZ, r, g, b, a, 0, 0, 1);
        addVertex(buffer, entry, maxX, maxY, maxZ, r, g, b, a, 0, 0, 1);
        addVertex(buffer, entry, minX, maxY, maxZ, r, g, b, a, 0, 0, 1);

        // west (x = minX)
        addVertex(buffer, entry, minX, minY, minZ, r, g, b, a, -1, 0, 0);
        addVertex(buffer, entry, minX, minY, maxZ, r, g, b, a, -1, 0, 0);
        addVertex(buffer, entry, minX, maxY, maxZ, r, g, b, a, -1, 0, 0);
        addVertex(buffer, entry, minX, maxY, minZ, r, g, b, a, -1, 0, 0);

        // east (x = maxX)
        addVertex(buffer, entry, maxX, minY, minZ, r, g, b, a, 1, 0, 0);
        addVertex(buffer, entry, maxX, maxY, minZ, r, g, b, a, 1, 0, 0);
        addVertex(buffer, entry, maxX, maxY, maxZ, r, g, b, a, 1, 0, 0);
        addVertex(buffer, entry, maxX, minY, maxZ, r, g, b, a, 1, 0, 0);
    }

    @Unique
    private void addVertex(VertexConsumer buffer, MatrixStack.Entry entry, float x, float y, float z,
                           float r, float g, float b, float a, float normalX, float normalY, float normalZ) {
        buffer.vertex(entry.getPositionMatrix(), x, y, z)
                .color(r, g, b, a)
                .normal(entry, normalX, normalY, normalZ);
    }
}