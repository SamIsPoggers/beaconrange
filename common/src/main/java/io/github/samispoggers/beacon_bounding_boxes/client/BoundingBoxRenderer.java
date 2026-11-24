package io.github.samispoggers.beacon_bounding_boxes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.samispoggers.beacon_bounding_boxes.BeaconBoundingBoxes;
import io.github.samispoggers.beacon_bounding_boxes.compat.IrisCompat;
import io.github.samispoggers.beacon_bounding_boxes.config.BeaconBoundingBoxesConfig;
import io.github.samispoggers.beacon_bounding_boxes.mixin.BeaconBlockEntityAccessor;
import io.github.samispoggers.beacon_bounding_boxes.mixin.ConduitBlockEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

public final class BoundingBoxRenderer {
    public static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath(
            BeaconBoundingBoxes.MOD_ID,
            "textures/misc/white.png"
    );
    public static final double MAX_CONDUIT_RENDER_PADDING = 100.0;
    public static final double MAX_BEACON_RENDER_PADDING = 64.0;

    // 0x00F000F0 (block light 15, sky light 15); LightTexture does not exist in MC 26.1-26.3.
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int[] BEACON_LEVEL_RANGES = {0, 20, 30, 40, 50};
    private static final int SPHERE_SEGMENTS = 48;
    private static final int LATITUDE_BANDS = 4;
    private static final int SPHERE_FILL_STACKS = 20;
    private static final int SPHERE_FILL_SLICES = SPHERE_SEGMENTS;
    private static final float BOX_FILL_ALPHA = 0.30f;
    private static final float SPHERE_FILL_ALPHA = 0.25f;

    @FunctionalInterface
    private interface SegmentEmitter {
        void emit(float x1, float y1, float z1, float x2, float y2, float z2);
    }

    private BoundingBoxRenderer() {
    }

    public static RenderType getFillRenderType() {
        return RenderTypes.beaconBeam(WHITE_TEXTURE, true);
    }

    public static RenderType getShaderOutlineRenderType() {
        return RenderTypes.beaconBeam(WHITE_TEXTURE, false);
    }

    public static int getRenderDistanceBlocks() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) {
            return 256;
        }
        return Math.max(16, client.options.getEffectiveRenderDistance() * 16);
    }

    public static boolean isWithinRenderDistance(BlockPos pos, Vec3 cameraPos, int maxDistance) {
        if (pos == null || cameraPos == null || maxDistance <= 0) {
            return false;
        }
        double centerX = pos.getX() + 0.5;
        double centerZ = pos.getZ() + 0.5;
        double dx = centerX - cameraPos.x();
        double dz = centerZ - cameraPos.z();
        double limit = maxDistance;
        return (dx * dx + dz * dz) < (limit * limit);
    }

    public static AABB getConduitRenderBoundingBox(BlockPos pos) {
        return new AABB(
                pos.getX() - MAX_CONDUIT_RENDER_PADDING,
                pos.getY() - MAX_CONDUIT_RENDER_PADDING,
                pos.getZ() - MAX_CONDUIT_RENDER_PADDING,
                pos.getX() + 1.0 + MAX_CONDUIT_RENDER_PADDING,
                pos.getY() + 1.0 + MAX_CONDUIT_RENDER_PADDING,
                pos.getZ() + 1.0 + MAX_CONDUIT_RENDER_PADDING
        );
    }

    public static AABB getBeaconRenderBoundingBox(BlockPos pos) {
        return new AABB(
                pos.getX() - MAX_BEACON_RENDER_PADDING,
                pos.getY() - MAX_BEACON_RENDER_PADDING,
                pos.getZ() - MAX_BEACON_RENDER_PADDING,
                pos.getX() + 1.0 + MAX_BEACON_RENDER_PADDING,
                2048.0,
                pos.getZ() + 1.0 + MAX_BEACON_RENDER_PADDING
        );
    }

    public static Vector3f colorForPos(BlockPos pos) {
        Random random = new Random(pos.asLong());
        return new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
    }

    public static void renderBeaconBox(
            BeaconBlockEntity beacon,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            Vec3 cameraPos
    ) {
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }

        int level = ((BeaconBlockEntityAccessor) beacon).bbb$getLevels();
        if (level <= 0) {
            return;
        }

        BlockPos pos = beacon.getBlockPos();
        Vector3f color = colorForPos(pos);
        AABB box = getBeaconBox(beacon, level);
        Vec3 camRel = cameraPos != null
                ? cameraPos.subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                : Vec3.ZERO;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        if (IrisCompat.isShaderPackInUse()) {
            // Render opaque outlines before translucent faces so shaderpacks composite both cleanly.
            submitNodeCollector.submitCustomGeometry(poseStack, getShaderOutlineRenderType(), (pose, consumer) ->
                    renderQuadLineBox(pose, consumer, box, camRel, color.x(), color.y(), color.z(), 1.0f)
            );
        } else {
            submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, consumer) ->
                    renderLineBox(pose, consumer, box, color.x(), color.y(), color.z(), 1.0f)
            );
        }

        if (BeaconBoundingBoxesConfig.get().boxMode == BeaconBoundingBoxesConfig.BoxMode.BOX) {
            submitNodeCollector.submitCustomGeometry(poseStack, getFillRenderType(), (pose, consumer) ->
                    drawBoxFaces(pose, consumer, box, color.x(), color.y(), color.z(), BOX_FILL_ALPHA)
            );
        }

        poseStack.popPose();
    }

    public static void renderConduitSphere(
            ConduitBlockEntity conduit,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            Vec3 cameraPos
    ) {
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }

        List<BlockPos> effectBlocks = ((ConduitBlockEntityAccessor) conduit).bbb$getEffectBlocks();
        if (effectBlocks == null) {
            return;
        }

        int activatingBlocks = effectBlocks.size();
        if (activatingBlocks < 16) {
            return;
        }

        BlockPos pos = conduit.getBlockPos();
        Vector3f color = colorForPos(pos);
        int rangeSteps = Math.min((activatingBlocks - 1) / 7, 5);
        int range = (rangeSteps * 16) - 1;
        Vec3 camRel = cameraPos != null
                ? cameraPos.subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                : Vec3.ZERO;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        if (IrisCompat.isShaderPackInUse()) {
            submitNodeCollector.submitCustomGeometry(poseStack, getShaderOutlineRenderType(), (pose, buffer) ->
                    drawSphereQuadOutline(pose, buffer, range, camRel, color.x(), color.y(), color.z(), SPHERE_SEGMENTS)
            );
        } else {
            submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) ->
                    drawSphereOutline(pose, buffer, range, color.x(), color.y(), color.z(), SPHERE_SEGMENTS)
            );
        }

        if (BeaconBoundingBoxesConfig.get().conduitMode == BeaconBoundingBoxesConfig.ConduitMode.FILLED) {
            float fillRadius = Math.max(0.0f, range - 0.02f);
            submitNodeCollector.submitCustomGeometry(poseStack, getFillRenderType(), (pose, buffer) ->
                    drawSphereFaces(pose, buffer, fillRadius, color.x(), color.y(), color.z(), SPHERE_FILL_ALPHA)
            );
        }

        poseStack.popPose();
    }

    public static AABB getBeaconBox(BeaconBlockEntity beacon, int level) {
        int clampedLevel = Math.max(0, Math.min(level, BEACON_LEVEL_RANGES.length - 1));
        int range = BEACON_LEVEL_RANGES[clampedLevel];
        BlockPos pos = beacon.getBlockPos();
        Level world = beacon.getLevel();
        int worldMaxY = world != null ? world.getMaxY() : BeaconBoundingBoxesConfig.MAX_CUSTOM_Y;

        int yMin = -range;
        int yMax = switch (BeaconBoundingBoxesConfig.get().heightMode) {
            case WORLD_HEIGHT -> worldMaxY - pos.getY();
            case CUSTOM -> BeaconBoundingBoxesConfig.get().customYLevel - pos.getY();
            case RADIUS -> range + 1;
        };

        Vec3 min = new Vec3(-range, yMin, -range);
        Vec3 max = new Vec3(range + 1, yMax, range + 1);
        return new AABB(min, max);
    }

    public static void renderLineBox(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            AABB box,
            float r,
            float g,
            float b,
            float a
    ) {
        emitBoxEdges(box, (x1, y1, z1, x2, y2, z2) ->
                addLine(consumer, pose, x1, y1, z1, x2, y2, z2, r, g, b, a)
        );
    }

    public static void renderQuadLineBox(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            AABB box,
            Vec3 camRel,
            float r,
            float g,
            float b,
            float a
    ) {
        emitBoxEdges(box, (x1, y1, z1, x2, y2, z2) ->
                addBillboardLineQuad(consumer, pose, x1, y1, z1, x2, y2, z2, camRel, r, g, b, a)
        );
    }

    public static void drawBoxFaces(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            AABB box,
            float r,
            float g,
            float b,
            float a
    ) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Bottom (y = minY)
        addDoubleSidedQuad(buffer, pose,
                minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ,
                r, g, b, a);
        // Top (y = maxY)
        addDoubleSidedQuad(buffer, pose,
                minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ,
                r, g, b, a);
        // North (z = minZ)
        addDoubleSidedQuad(buffer, pose,
                minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ,
                r, g, b, a);
        // South (z = maxZ)
        addDoubleSidedQuad(buffer, pose,
                minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ,
                r, g, b, a);
        // West (x = minX)
        addDoubleSidedQuad(buffer, pose,
                minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ,
                r, g, b, a);
        // East (x = maxX)
        addDoubleSidedQuad(buffer, pose,
                maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ,
                r, g, b, a);
    }

    public static void drawSphereFaces(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float radius,
            float r,
            float g,
            float b,
            float a
    ) {
        if (radius <= 0.0f) {
            return;
        }

        for (int stack = 0; stack < SPHERE_FILL_STACKS; stack++) {
            double phi0 = -Math.PI / 2.0 + Math.PI * stack / SPHERE_FILL_STACKS;
            double phi1 = -Math.PI / 2.0 + Math.PI * (stack + 1) / SPHERE_FILL_STACKS;

            float y0 = (float) (radius * Math.sin(phi0));
            float y1 = (float) (radius * Math.sin(phi1));
            float r0 = (float) (radius * Math.cos(phi0));
            float r1 = (float) (radius * Math.cos(phi1));

            for (int slice = 0; slice < SPHERE_FILL_SLICES; slice++) {
                double theta0 = 2.0 * Math.PI * slice / SPHERE_FILL_SLICES;
                double theta1 = 2.0 * Math.PI * (slice + 1) / SPHERE_FILL_SLICES;

                float cosT0 = (float) Math.cos(theta0);
                float sinT0 = (float) Math.sin(theta0);
                float cosT1 = (float) Math.cos(theta1);
                float sinT1 = (float) Math.sin(theta1);

                addDoubleSidedQuad(
                        buffer,
                        pose,
                        r0 * cosT0, y0, r0 * sinT0,
                        r0 * cosT1, y0, r0 * sinT1,
                        r1 * cosT1, y1, r1 * sinT1,
                        r1 * cosT0, y1, r1 * sinT0,
                        r, g, b, a
                );
            }
        }
    }

    public static void drawSphereOutline(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float radius,
            float r,
            float g,
            float b,
            int segments
    ) {
        emitSphereOutlineSegments(radius, segments, (x1, y1, z1, x2, y2, z2) ->
                addLine(buffer, pose, x1, y1, z1, x2, y2, z2, r, g, b, 1.0f)
        );
    }

    public static void drawSphereQuadOutline(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float radius,
            Vec3 camRel,
            float r,
            float g,
            float b,
            int segments
    ) {
        emitSphereOutlineSegments(radius, segments, (x1, y1, z1, x2, y2, z2) ->
                addBillboardLineQuad(buffer, pose, x1, y1, z1, x2, y2, z2, camRel, r, g, b, 1.0f)
        );
    }

    private static void emitBoxEdges(AABB box, SegmentEmitter emitter) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // Bottom square
        emitter.emit(minX, minY, minZ, maxX, minY, minZ);
        emitter.emit(maxX, minY, minZ, maxX, minY, maxZ);
        emitter.emit(maxX, minY, maxZ, minX, minY, maxZ);
        emitter.emit(minX, minY, maxZ, minX, minY, minZ);

        // Top square
        emitter.emit(minX, maxY, minZ, maxX, maxY, minZ);
        emitter.emit(maxX, maxY, minZ, maxX, maxY, maxZ);
        emitter.emit(maxX, maxY, maxZ, minX, maxY, maxZ);
        emitter.emit(minX, maxY, maxZ, minX, maxY, minZ);

        // Vertical edges
        emitter.emit(minX, minY, minZ, minX, maxY, minZ);
        emitter.emit(maxX, minY, minZ, maxX, maxY, minZ);
        emitter.emit(maxX, minY, maxZ, maxX, maxY, maxZ);
        emitter.emit(minX, minY, maxZ, minX, maxY, maxZ);
    }

    private static void emitSphereOutlineSegments(float radius, int segments, SegmentEmitter emitter) {
        if (radius <= 0.0f || segments <= 0) {
            return;
        }

        // Equator ring
        for (int i = 0; i < segments; i++) {
            double theta1 = 2.0 * Math.PI * i / segments;
            double theta2 = 2.0 * Math.PI * (i + 1) / segments;

            float x1 = (float) (radius * Math.cos(theta1));
            float z1 = (float) (radius * Math.sin(theta1));
            float x2 = (float) (radius * Math.cos(theta2));
            float z2 = (float) (radius * Math.sin(theta2));

            emitter.emit(x1, 0.0f, z1, x2, 0.0f, z2);
        }

        // Latitude rings above and below equator
        for (int band = 1; band <= LATITUDE_BANDS; band++) {
            double phi = (Math.PI / 2.0) * (band / (double) (LATITUDE_BANDS + 1));
            float y = (float) (radius * Math.sin(phi));
            float bandRadius = (float) (radius * Math.cos(phi));

            for (int i = 0; i < segments; i++) {
                double theta1 = 2.0 * Math.PI * i / segments;
                double theta2 = 2.0 * Math.PI * (i + 1) / segments;

                float x1 = (float) (bandRadius * Math.cos(theta1));
                float z1 = (float) (bandRadius * Math.sin(theta1));
                float x2 = (float) (bandRadius * Math.cos(theta2));
                float z2 = (float) (bandRadius * Math.sin(theta2));

                emitter.emit(x1, y, z1, x2, y, z2);
                emitter.emit(x1, -y, z1, x2, -y, z2);
            }
        }

        // Longitude rings
        for (int i = 0; i < segments; i++) {
            double theta = 2.0 * Math.PI * i / segments;
            double cosTheta = Math.cos(theta);
            double sinTheta = Math.sin(theta);

            for (int j = 0; j < segments; j++) {
                double phi1 = Math.PI * j / segments - Math.PI / 2.0;
                double phi2 = Math.PI * (j + 1) / segments - Math.PI / 2.0;

                float x1 = (float) (radius * Math.cos(phi1) * cosTheta);
                float y1 = (float) (radius * Math.sin(phi1));
                float z1 = (float) (radius * Math.cos(phi1) * sinTheta);

                float x2 = (float) (radius * Math.cos(phi2) * cosTheta);
                float y2 = (float) (radius * Math.sin(phi2));
                float z2 = (float) (radius * Math.cos(phi2) * sinTheta);

                emitter.emit(x1, y1, z1, x2, y2, z2);
            }
        }
    }

    private static void addLine(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float r,
            float g,
            float b,
            float a
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq < 1e-8f) {
            return;
        }
        float invLen = (float) (1.0 / Math.sqrt(lenSq));
        float nx = dx * invLen;
        float ny = dy * invLen;
        float nz = dz * invLen;

        consumer.addVertex(pose, x1, y1, z1)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(1.0f);
        consumer.addVertex(pose, x2, y2, z2)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(1.0f);
    }

    private static void addBillboardLineQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            Vec3 camRel,
            float r,
            float g,
            float b,
            float a
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float lenSq = dx * dx + dy * dy + dz * dz;
        if (lenSq < 1e-8f) {
            return;
        }

        double midX = (x1 + x2) * 0.5;
        double midY = (y1 + y2) * 0.5;
        double midZ = (z1 + z2) * 0.5;

        double toCamX = camRel.x() - midX;
        double toCamY = camRel.y() - midY;
        double toCamZ = camRel.z() - midZ;
        float dist = (float) Math.sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ);
        float halfWidth = Math.max(0.015f, Math.min(0.28f, dist * 0.0024f));

        double sideX = dy * toCamZ - dz * toCamY;
        double sideY = dz * toCamX - dx * toCamZ;
        double sideZ = dx * toCamY - dy * toCamX;
        double sideLenSq = sideX * sideX + sideY * sideY + sideZ * sideZ;

        if (sideLenSq > 1e-8) {
            float scale = (float) (halfWidth / Math.sqrt(sideLenSq));
            float sx = (float) (sideX * scale);
            float sy = (float) (sideY * scale);
            float sz = (float) (sideZ * scale);

            // (P1 - side, P1 + side, P2 + side, P2 - side) has CCW normal facing camRel.
            addQuad(
                    consumer,
                    pose,
                    x1 - sx, y1 - sy, z1 - sz,
                    x1 + sx, y1 + sy, z1 + sz,
                    x2 + sx, y2 + sy, z2 + sz,
                    x2 - sx, y2 - sy, z2 - sz,
                    r, g, b, a
            );
            return;
        }

        // Segment is nearly collinear with the view vector; emit a double-sided perpendicular quad.
        float invLen = (float) (1.0 / Math.sqrt(lenSq));
        float ux = dx * invLen;
        float uy = dy * invLen;
        float uz = dz * invLen;
        float rx = Math.abs(uy) < 0.9f ? 0.0f : 1.0f;
        float ry = Math.abs(uy) < 0.9f ? 1.0f : 0.0f;

        double fx = uy * 0.0 - uz * ry;
        double fy = uz * rx - ux * 0.0;
        double fz = ux * ry - uy * rx;
        float fScale = (float) (halfWidth / Math.sqrt(fx * fx + fy * fy + fz * fz));
        float sx = (float) (fx * fScale);
        float sy = (float) (fy * fScale);
        float sz = (float) (fz * fScale);

        addDoubleSidedQuad(
                consumer,
                pose,
                x1 - sx, y1 - sy, z1 - sz,
                x1 + sx, y1 + sy, z1 + sz,
                x2 + sx, y2 + sy, z2 + sz,
                x2 - sx, y2 - sy, z2 - sz,
                r, g, b, a
        );
    }

    private static void addDoubleSidedQuad(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float r, float g, float b, float a
    ) {
        addQuad(buffer, pose, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a);
        addQuad(buffer, pose, x0, y0, z0, x3, y3, z3, x2, y2, z2, x1, y1, z1, r, g, b, a);
    }

    private static void addQuad(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float r, float g, float b, float a
    ) {
        addBeamVertex(buffer, pose, x0, y0, z0, r, g, b, a);
        addBeamVertex(buffer, pose, x1, y1, z1, r, g, b, a);
        addBeamVertex(buffer, pose, x2, y2, z2, r, g, b, a);
        addBeamVertex(buffer, pose, x3, y3, z3, r, g, b, a);
    }

    private static void addBeamVertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float r,
            float g,
            float b,
            float a
    ) {
        buffer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }
}
