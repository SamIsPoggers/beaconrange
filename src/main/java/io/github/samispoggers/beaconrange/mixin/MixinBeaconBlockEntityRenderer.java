package io.github.samispoggers.beaconrange.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.samispoggers.beaconrange.client.BeaconRangeClient;
import io.github.samispoggers.beaconrange.config.ConfigManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Util;
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
import java.util.OptionalDouble;
import java.util.Random;
import java.util.function.Function;

import static net.minecraft.client.gl.RenderPipelines.RENDERTYPE_LINES_SNIPPET;
import static net.minecraft.client.render.RenderPhase.ITEM_ENTITY_TARGET;
import static net.minecraft.client.render.RenderPhase.VIEW_OFFSET_Z_LAYERING;

@Environment(EnvType.CLIENT)
@Mixin(BeaconBlockEntityRenderer.class)
public abstract class MixinBeaconBlockEntityRenderer {

    @Unique
    RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(
                    RENDERTYPE_LINES_SNIPPET).withLocation("pipeline/custom_line").withVertexShader("core/position_color")
            .withFragmentShader("core/position_color").withCull(false).withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
            .build()
    );

    @Unique
    Function<Double, RenderLayer.MultiPhase> phase = Util.memoize((lineWidth) -> RenderLayer.of("debug_lines", 1536, pipeline,
            RenderLayer.MultiPhaseParameters.builder().lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(lineWidth))).layering(VIEW_OFFSET_Z_LAYERING).target(ITEM_ENTITY_TARGET).build(false)));

    @Unique
    private final Map<BlockPos, Vector3f> beaconColors = new HashMap<>();

    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"))
    private void onRenderBeacon(BlockEntity entity, float tickDelta, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos, CallbackInfo ci) {
        if (entity instanceof BeaconBlockEntity beacon && BeaconRangeClient.myToggleBeaconVariable) {
            renderBoundingBox(beacon, matrices, vertexConsumers);
        }
    }

    @Unique
    private void renderBoundingBox(BeaconBlockEntity beacon, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        BlockPos blockPos = beacon.getPos();

        // Deterministic color per beacon
        Vector3f color = beaconColors.computeIfAbsent(blockPos, p -> {
            Random random = new Random(p.asLong());
            return new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat());
        });

        int level = ((BeaconBlockEntityAccessor) beacon).getLevel();
        if (level <= 0) return;

        Box box = getBox(beacon, level);

        // Use lines render layer - should not create connecting diagonals
        VertexConsumer buffer = vertexConsumers.getBuffer(phase.apply(2.0));

        VertexRendering.drawBox(matrices, buffer, box, color.x(), color.y(), color.z(), 1.0f);
    }

    @Unique
    private static @NotNull Box getBox(BeaconBlockEntity beacon, int level) {
        int[] levelRanges = {0, 20, 30, 40, 50}; // Example ranges by level
        int range = levelRanges[Math.min(level, levelRanges.length - 1)];

        // Determine height depending on config
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
}