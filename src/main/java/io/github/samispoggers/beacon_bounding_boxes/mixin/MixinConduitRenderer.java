package io.github.samispoggers.beacon_bounding_boxes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.samispoggers.beacon_bounding_boxes.client.BeaconBoundingBoxesClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.blockentity.state.CondiutRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
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
@Mixin(ConduitRenderer.class)
public class MixinConduitRenderer {

    @Unique
    private final Map<BlockPos, Vector3f> conduitColors = new HashMap<>();

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/CondiutRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"))
    public void onSubmitConduit(
            CondiutRenderState conduitRenderState,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        if (!BeaconBoundingBoxesClient.renderConduitBounds) return;

        var world = Minecraft.getInstance().level;
        if (world == null) return;

        BlockEntity be = world.getBlockEntity(conduitRenderState.blockPos);
        if (!(be instanceof ConduitBlockEntity conduit)) return;

        renderBoundingBox(conduit, poseStack, submitNodeCollector);
    }

    @Unique
    private void renderBoundingBox(ConduitBlockEntity conduit, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        BlockPos blockPos = conduit.getBlockPos();

        Vector3f color = conduitColors.computeIfAbsent(blockPos, p -> {
            Random random = new Random(p.asLong()); // Deterministic color per conduit
            return new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
        });

        int activatingBlocks = ((ConduitBlockEntityAccessor) conduit).getEffectBlocks().size();
        if (activatingBlocks < 16) return; // Not enough blocks to activate

        // Calculate range: floor((activatingBlocks - 1) / 7) * 16, capped at 96
        int rangeSteps = Math.min((activatingBlocks - 1) / 7, 5);
        int range = (rangeSteps * 16) - 1;

        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
            drawSphereOutline(pose, buffer, range, color.x(), color.y(), color.z(), 48);
        });
    }


    @Unique
    private void drawSphereOutline(PoseStack.Pose pose, VertexConsumer buffer, float radius, float r, float g, float b, int segments) {

        // Horizontal ring (equator)
        for (int i = 0; i < segments; i++) {
            double theta1 = 2.0 * Math.PI * i / segments;
            double theta2 = 2.0 * Math.PI * (i + 1) / segments;

            float x1 = (float) (radius * Math.cos(theta1));
            float z1 = (float) (radius * Math.sin(theta1));
            float x2 = (float) (radius * Math.cos(theta2));
            float z2 = (float) (radius * Math.sin(theta2));

            buffer.addVertex(pose, x1, 0, z1).setColor(r, g, b, 1.0f).setNormal(pose, 0, 1, 0).setLineWidth(1.0f);
            buffer.addVertex(pose, x2, 0, z2).setColor(r, g, b, 1.0f).setNormal(pose, 0, 1, 0).setLineWidth(1.0f);
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
                buffer.addVertex(pose, x1, y, z1).setColor(r, g, b, 1.0f).setNormal(pose, 0, 1, 0).setLineWidth(1.0f);
                buffer.addVertex(pose, x2, y, z2).setColor(r, g, b, 1.0f).setNormal(pose, 0, 1, 0).setLineWidth(1.0f);

                // Lower latitude ring
                buffer.addVertex(pose, x1, yNeg, z1).setColor(r, g, b, 1.0f).setNormal(pose, 0, 1, 0).setLineWidth(1.0f);
                buffer.addVertex(pose, x2, yNeg, z2).setColor(r, g, b, 1.0f).setNormal(pose, 0, 1, 0).setLineWidth(1.0f);
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

                buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, 1.0f).setNormal(pose, 0, 1, 0).setLineWidth(1.0f);
                buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, 1.0f).setNormal(pose, 0, 1, 0).setLineWidth(1.0f);
            }
        }
    }

}