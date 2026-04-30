package me.mss1r.recruitsmapoverhaul.mixin;

import me.mss1r.recruitsmapoverhaul.client.map.ChunkTileManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "setBlock", at = @At("RETURN"))
    private void recruitsmapoverhaul$markChangedBlockDirty(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            ChunkTileManager.getInstance().markBlockDirty(pos);
        }
    }
}
