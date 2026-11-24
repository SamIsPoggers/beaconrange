package io.github.samispoggers.beacon_bounding_boxes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.samispoggers.beacon_bounding_boxes.client.BeaconBoundingBoxesClient;
import io.github.samispoggers.beacon_bounding_boxes.client.BoundingBoxRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeaconRenderer.class)
public abstract class MixinBeaconRenderer {

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BeaconRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void bbb$onSubmitBeacon(
            BeaconRenderState beaconRenderState,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState cameraRenderState,
            CallbackInfo ci
    ) {
        if (!BeaconBoundingBoxesClient.renderBeaconBounds) {
            return;
        }

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(beaconRenderState.blockPos);
        if (!(blockEntity instanceof BeaconBlockEntity beacon)) {
            return;
        }

        Vec3 cameraPos = cameraRenderState != null ? cameraRenderState.pos : Vec3.ZERO;
        BoundingBoxRenderer.renderBeaconBox(beacon, poseStack, submitNodeCollector, cameraPos);
    }

    @Inject(
            method = "getRenderBoundingBox(Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/minecraft/world/phys/AABB;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            expect = 0
    )
    private void bbb$getBeaconRenderBoundingBox(
            BlockEntity beacon,
            CallbackInfoReturnable<AABB> cir
    ) {
        if (beacon != null) {
            cir.setReturnValue(BoundingBoxRenderer.getBeaconRenderBoundingBox(beacon.getBlockPos()));
        }
    }
}
