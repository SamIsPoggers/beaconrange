package io.github.samispoggers.beacon_bounding_boxes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.samispoggers.beacon_bounding_boxes.client.BeaconBoundingBoxesClient;
import io.github.samispoggers.beacon_bounding_boxes.client.BoundingBoxRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.blockentity.state.ConduitRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConduitRenderer.class)
public abstract class MixinConduitRenderer {

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/blockentity/state/ConduitRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void bbb$onSubmitConduit(
            ConduitRenderState conduitRenderState,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        if (!BeaconBoundingBoxesClient.renderConduitBounds) {
            return;
        }

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(conduitRenderState.blockPos);
        if (!(blockEntity instanceof ConduitBlockEntity conduit)) {
            return;
        }

        Vec3 cameraPos = cameraRenderState != null ? cameraRenderState.pos : Vec3.ZERO;
        BoundingBoxRenderer.renderConduitSphere(conduit, poseStack, submitNodeCollector, cameraPos);
    }

    public boolean shouldRenderOffScreen() {
        return true;
    }

    public int getViewDistance() {
        return BoundingBoxRenderer.getRenderDistanceBlocks();
    }

    public boolean shouldRender(BlockEntity blockEntity, Vec3 cameraPos) {
        if (blockEntity == null || cameraPos == null) {
            return false;
        }
        return BoundingBoxRenderer.isWithinRenderDistance(blockEntity.getBlockPos(), cameraPos, this.getViewDistance());
    }

    @Inject(
            method = "getRenderBoundingBox(Lnet/minecraft/world/level/block/entity/ConduitBlockEntity;)Lnet/minecraft/world/phys/AABB;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            expect = 0
    )
    private void bbb$getConduitRenderBoundingBox(
            ConduitBlockEntity conduit,
            CallbackInfoReturnable<AABB> cir
    ) {
        if (conduit != null) {
            cir.setReturnValue(BoundingBoxRenderer.getConduitRenderBoundingBox(conduit.getBlockPos()));
        }
    }
}
