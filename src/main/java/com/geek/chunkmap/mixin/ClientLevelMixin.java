package com.geek.chunkmap.mixin;

import com.geek.chunkmap.event.DirtyChunkTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "setBlocksDirty", at = @At("HEAD"), require = 0)
    private void chunkmap$markChunkDirty(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (oldState.getBlock() != newState.getBlock()) {
            DirtyChunkTracker.markDirty(new ChunkPos(pos));
        }
    }
}