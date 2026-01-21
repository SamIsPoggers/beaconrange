package io.github.samispoggers.beacon_bounding_boxes.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.samispoggers.beacon_bounding_boxes.client.BeaconBoundingBoxesClient;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
@Mixin(BeaconRenderer.class)
public abstract class MixinBeaconRenderer {

    @Unique
    private static final RenderPipeline TRANSLUCENT_BOX_PIPELINE =
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation("pipeline/translucent_box")
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .withCull(false)
                    .withDepthWrite(false)
                    .build();

    @Unique
    private static final RenderType TRANSLUCENT_BOX = RenderType.create(
            "beacon_bounding_boxes:translucent_box",
            RenderSetup.builder(
                    TRANSLUCENT_BOX_PIPELINE
            ).createRenderSetup()
    );

    @Unique
    private final Map<BlockPos, Vector3f> beaconColors = new HashMap<>();

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BeaconRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"))
    private void onSubmitBeacon(
            BeaconRenderState beaconRenderState,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        if (!BeaconBoundingBoxesClient.renderBeaconBounds) return;

        var world = Minecraft.getInstance().level;
        if (world == null) return;

        BlockEntity be = world.getBlockEntity(beaconRenderState.blockPos);
        if (!(be instanceof BeaconBlockEntity beacon)) return;

        renderBoundingBox(beacon, poseStack, submitNodeCollector);
    }

    @Unique
    private void renderBoundingBox(
            BeaconBlockEntity beacon,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector
    ) {
        BlockPos blockPos = beacon.getBlockPos();

        // Deterministic color per beacon
        Vector3f color = beaconColors.computeIfAbsent(blockPos, p -> {
            Random random = new Random(p.asLong());
            return new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
        });

        int level = ((BeaconBlockEntityAccessor) beacon).getLevels();
        if (level <= 0) return;

        AABB box = getBox(beacon, level);

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5); // Center on the beacon block

        if (BeaconBoundingBoxesConfig.get().boxMode == BeaconBoundingBoxesConfig.BoxMode.BOX) {
            submitNodeCollector.submitCustomGeometry(poseStack, TRANSLUCENT_BOX, (pose, consumer) ->
                    drawBoxFaces(pose, consumer, box, color.x(), color.y(), color.z(), 0.3f)
            );
        }

        // Then render the outline on top
        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) ->
                renderLineBox(pose, consumer, box, color.x(), color.y(), color.z(), 1.0f)
        );

        poseStack.popPose();
    }

    @Unique
    private static @NotNull AABB getBox(BeaconBlockEntity beacon, int level) {
        // Vanilla ranges
        int[] levelRanges = {0, 20, 30, 40, 50};
        int range = levelRanges[Math.min(level, levelRanges.length - 1)];

        int yMin = -range;
        int yMax = switch (BeaconBoundingBoxesConfig.get().heightMode) {
            case WORLD_HEIGHT -> 319 - beacon.getBlockPos().getY();
            case CUSTOM -> BeaconBoundingBoxesConfig.get().customYLevel - beacon.getBlockPos().getY();
            default -> range + 1;
        };

        Vec3 min = new Vec3(-range, yMin, -range);
        Vec3 max = new Vec3(range + 1, yMax, range + 1);
        return new AABB(min, max);
    }

    @Unique
    private void renderLineBox(PoseStack.Pose pose, VertexConsumer consumer, AABB box,
                               float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Bottom square
        addLine(consumer, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        addLine(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        addLine(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        addLine(consumer, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // Top square
        addLine(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(consumer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        addLine(consumer, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // Vertical edges
        addLine(consumer, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        addLine(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        addLine(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        addLine(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    @Unique
    private void addLine(VertexConsumer consumer, PoseStack.Pose pose,
                         float x1, float y1, float z1, float x2, float y2, float z2,
                         float r, float g, float b, float a) {
        Vector3f normal = new Vector3f(x2 - x1, y2 - y1, z2 - z1).normalize();
        consumer.addVertex(pose, x1, y1, z1)
                .setColor(r, g, b, a)
                .setNormal(pose, normal.x, normal.y, normal.z)
                .setLineWidth(1.0f);
        consumer.addVertex(pose, x2, y2, z2)
                .setColor(r, g, b, a)
                .setNormal(pose, normal.x, normal.y, normal.z)
                .setLineWidth(1.0f);
    }

    @Unique
    private void drawBoxFaces(PoseStack.Pose pose, VertexConsumer buffer, AABB box,
                              float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // bottom (y = minY)
        addVertex(buffer, pose, minX, minY, minZ, r, g, b, a, 0, -1, 0);
        addVertex(buffer, pose, maxX, minY, minZ, r, g, b, a, 0, -1, 0);
        addVertex(buffer, pose, maxX, minY, maxZ, r, g, b, a, 0, -1, 0);
        addVertex(buffer, pose, minX, minY, maxZ, r, g, b, a, 0, -1, 0);

        // top (y = maxY)
        addVertex(buffer, pose, minX, maxY, minZ, r, g, b, a, 0, 1, 0);
        addVertex(buffer, pose, minX, maxY, maxZ, r, g, b, a, 0, 1, 0);
        addVertex(buffer, pose, maxX, maxY, maxZ, r, g, b, a, 0, 1, 0);
        addVertex(buffer, pose, maxX, maxY, minZ, r, g, b, a, 0, 1, 0);

        // north (z = minZ)
        addVertex(buffer, pose, minX, minY, minZ, r, g, b, a, 0, 0, -1);
        addVertex(buffer, pose, minX, maxY, minZ, r, g, b, a, 0, 0, -1);
        addVertex(buffer, pose, maxX, maxY, minZ, r, g, b, a, 0, 0, -1);
        addVertex(buffer, pose, maxX, minY, minZ, r, g, b, a, 0, 0, -1);

        // south (z = maxZ)
        addVertex(buffer, pose, minX, minY, maxZ, r, g, b, a, 0, 0, 1);
        addVertex(buffer, pose, maxX, minY, maxZ, r, g, b, a, 0, 0, 1);
        addVertex(buffer, pose, maxX, maxY, maxZ, r, g, b, a, 0, 0, 1);
        addVertex(buffer, pose, minX, maxY, maxZ, r, g, b, a, 0, 0, 1);

        // west (x = minX)
        addVertex(buffer, pose, minX, minY, minZ, r, g, b, a, -1, 0, 0);
        addVertex(buffer, pose, minX, minY, maxZ, r, g, b, a, -1, 0, 0);
        addVertex(buffer, pose, minX, maxY, maxZ, r, g, b, a, -1, 0, 0);
        addVertex(buffer, pose, minX, maxY, minZ, r, g, b, a, -1, 0, 0);

        // east (x = maxX)
        addVertex(buffer, pose, maxX, minY, minZ, r, g, b, a, 1, 0, 0);
        addVertex(buffer, pose, maxX, maxY, minZ, r, g, b, a, 1, 0, 0);
        addVertex(buffer, pose, maxX, maxY, maxZ, r, g, b, a, 1, 0, 0);
        addVertex(buffer, pose, maxX, minY, maxZ, r, g, b, a, 1, 0, 0);
    }

    @Unique
    private void addVertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                           float r, float g, float b, float a, float normalX, float normalY, float normalZ) {
        buffer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}