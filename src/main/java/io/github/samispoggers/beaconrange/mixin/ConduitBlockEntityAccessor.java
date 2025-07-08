package io.github.samispoggers.beaconrange.mixin;

import net.minecraft.block.entity.ConduitBlockEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ConduitBlockEntity.class)
public interface ConduitBlockEntityAccessor {
    @Accessor("activatingBlocks")
    List<BlockPos> getActivatingBlocks();
}
