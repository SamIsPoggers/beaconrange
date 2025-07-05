package io.github.samispoggers.beaconrange.mixin;

import io.github.samispoggers.beaconrange.client.BeaconRangeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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
    private final Map<BlockPos, Vector3f> beaconColors = new HashMap<>();

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"))
    private void onRenderBeacon(BlockEntity entity, float tickDelta, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos, CallbackInfo ci) {
        if (entity instanceof BeaconBlockEntity beacon && BeaconRangeClient.myToggleVariable) {
            renderBoundingBox(beacon, matrices, vertexConsumers);
        }
    }

    @Unique
    private void renderBoundingBox(BeaconBlockEntity beacon, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        BlockPos blockPos = beacon.getPos();

        Vector3f color = beaconColors.computeIfAbsent(blockPos, p -> {
            Random random = new Random(p.asLong()); // Deterministic color per beacon
            return new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
        });

        int level = ((BeaconBlockEntityAccessor) beacon).getLevel();
        if (level <= 0) return;

        int[] levelRanges = {0, 20, 30, 40, 50}; // Beacon range by level
        int range = levelRanges[Math.min(level, 4)];

        Vec3d min = new Vec3d(-range, -range, -range);
        Vec3d max = new Vec3d(range + 1, range + 1, range + 1);
        Box box = new Box(min, max);

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLines());

        drawBoxOutline(matrices, buffer, box, color.x(), color.y(), color.z());
    }

    /**
     * Renders a simple line box outline using the provided VertexConsumer.
     */
    @Unique
    private void drawBoxOutline(MatrixStack matrices, VertexConsumer buffer, Box box, float r, float g, float b) {
        MatrixStack.Entry entry = matrices.peek();

        // 12 edges of the box (each with two points)
        Vec3d[] corners = {
                new Vec3d(box.minX, box.minY, box.minZ),
                new Vec3d(box.maxX, box.minY, box.minZ),
                new Vec3d(box.minX, box.maxY, box.minZ),
                new Vec3d(box.maxX, box.maxY, box.minZ),
                new Vec3d(box.minX, box.minY, box.maxZ),
                new Vec3d(box.maxX, box.minY, box.maxZ),
                new Vec3d(box.minX, box.maxY, box.maxZ),
                new Vec3d(box.maxX, box.maxY, box.maxZ),
        };

        int[][] lines = {
                {0, 1}, {1, 3}, {3, 2}, {2, 0},
                {4, 5}, {5, 7}, {7, 6}, {6, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        for (int[] line : lines) {
            Vec3d start = corners[line[0]];
            Vec3d end = corners[line[1]];

            buffer.vertex(entry.getPositionMatrix(), (float) start.x, (float) start.y, (float) start.z)
                    .color(r, g, b, (float) 1.0)
                    .normal(0.0F, 1.0F, 0.0F);
            buffer.vertex(entry.getPositionMatrix(), (float) end.x, (float) end.y, (float) end.z)
                    .color(r, g, b, (float) 1.0)
                    .normal(0.0F, 1.0F, 0.0F);
        }
    }
}