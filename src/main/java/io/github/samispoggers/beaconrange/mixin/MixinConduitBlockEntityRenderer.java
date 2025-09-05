package io.github.samispoggers.beaconrange.mixin;

import io.github.samispoggers.beaconrange.client.BeaconRangeClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.ConduitBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.ConduitBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
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
@Mixin(ConduitBlockEntityRenderer.class)
public class MixinConduitBlockEntityRenderer {

    @Unique
    private final Map<BlockPos, Vector3f> conduitColors = new HashMap<>();

    @Inject(method = "render(Lnet/minecraft/block/entity/ConduitBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"))
    public void renderConduitBoxes(ConduitBlockEntity conduitBlockEntity, float f, MatrixStack matrixStack,
                                   VertexConsumerProvider vertexConsumerProvider, int i, int j,
                                   Vec3d vec3d, CallbackInfo ci) {
        if (BeaconRangeClient.myToggleConduitVariable) {
            renderBoundingBox(conduitBlockEntity, matrixStack, vertexConsumerProvider);
        }
    }

    @Unique
    private void renderBoundingBox(ConduitBlockEntity conduit, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        BlockPos blockPos = conduit.getPos();

        Vector3f color = conduitColors.computeIfAbsent(blockPos, p -> {
            Random random = new Random(p.asLong()); // Deterministic color per conduit
            return new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
        });

        int activatingBlocks = ((ConduitBlockEntityAccessor) conduit).getActivatingBlocks().size();
        if (activatingBlocks < 16) return; // Not enough blocks to activate

        // Calculate range: floor((activatingBlocks - 1) / 7) * 16, capped at 96
        int rangeSteps = Math.min((activatingBlocks - 1) / 7, 5);
        int range = (rangeSteps * 16) - 1;

        Vec3d min = new Vec3d(-range, -range, -range);
        Vec3d max = new Vec3d(range + 1, range + 1, range + 1);

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLines());
        drawSphereOutline(matrices, buffer, range, color.x(), color.y(), color.z(), 48);
    }


    @Unique
    private void drawSphereOutline(MatrixStack matrices, VertexConsumer buffer, float radius, float r, float g, float b, int segments) {
        MatrixStack.Entry entry = matrices.peek();

        // Horizontal ring (equator)
        for (int i = 0; i < segments; i++) {
            double theta1 = 2.0 * Math.PI * i / segments;
            double theta2 = 2.0 * Math.PI * (i + 1) / segments;

            float x1 = (float) (radius * Math.cos(theta1));
            float z1 = (float) (radius * Math.sin(theta1));
            float x2 = (float) (radius * Math.cos(theta2));
            float z2 = (float) (radius * Math.sin(theta2));

            buffer.vertex(entry.getPositionMatrix(), x1, 0, z1).color(r, g, b, 1.0f).normal(0, 1, 0);
            buffer.vertex(entry.getPositionMatrix(), x2, 0, z2).color(r, g, b, 1.0f).normal(0, 1, 0);
        }

        // Latitude rings (parallel circles above and below equator)
        int latitudeBands = 4; // Adjust for more/less rings
        for (int band = 1; band <= latitudeBands; band++) {
            double phi = Math.PI / 2 * (band / (double)(latitudeBands + 1)); // angle from equator

            float y = (float) (radius * Math.sin(phi));
            float yNeg = -y;
            float bandRadius = (float) (radius * Math.cos(phi));

            for (int i = 0; i < segments; i++) {
                double theta1 = 2.0 * Math.PI * i / segments;
                double theta2 = 2.0 * Math.PI * (i + 1) / segments;

                float x1 = (float) (bandRadius * Math.cos(theta1));
                float z1 = (float) (bandRadius * Math.sin(theta1));
                float x2 = (float) (bandRadius * Math.cos(theta2));
                float z2 = (float) (bandRadius * Math.sin(theta2));

                // Upper latitude ring
                buffer.vertex(entry.getPositionMatrix(), x1, y, z1).color(r, g, b, 1.0f).normal(0, 1, 0);
                buffer.vertex(entry.getPositionMatrix(), x2, y, z2).color(r, g, b, 1.0f).normal(0, 1, 0);

                // Lower latitude ring
                buffer.vertex(entry.getPositionMatrix(), x1, yNeg, z1).color(r, g, b, 1.0f).normal(0, 1, 0);
                buffer.vertex(entry.getPositionMatrix(), x2, yNeg, z2).color(r, g, b, 1.0f).normal(0, 1, 0);
            }
        }

        // Longitude rings (XZ and YZ)
        for (int i = 0; i < segments; i++) {
            double theta1 = 2.0 * Math.PI * i / segments;
            double theta2 = 2.0 * Math.PI * (i + 1) / segments;

            for (int j = 0; j <= segments; j++) {
                double phi1 = Math.PI * j / segments - Math.PI / 2;
                double phi2 = Math.PI * (j + 1) / segments - Math.PI / 2;

                float x1 = (float) (radius * Math.cos(phi1) * Math.cos(theta1));
                float y1 = (float) (radius * Math.sin(phi1));
                float z1 = (float) (radius * Math.cos(phi1) * Math.sin(theta1));

                float x2 = (float) (radius * Math.cos(phi2) * Math.cos(theta1));
                float y2 = (float) (radius * Math.sin(phi2));
                float z2 = (float) (radius * Math.cos(phi2) * Math.sin(theta1));

                buffer.vertex(entry.getPositionMatrix(), x1, y1, z1).color(r, g, b, 1.0f).normal(0, 1, 0);
                buffer.vertex(entry.getPositionMatrix(), x2, y2, z2).color(r, g, b, 1.0f).normal(0, 1, 0);
            }
        }
    }

}