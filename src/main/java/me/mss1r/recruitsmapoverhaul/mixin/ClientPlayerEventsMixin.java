package me.mss1r.recruitsmapoverhaul.mixin;

import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = com.talhanation.recruits.client.events.ClientPlayerEvents.class, remap = false)
public abstract class ClientPlayerEventsMixin {
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true)
    private void recruitsmapoverhaul$skipOriginalMapUpdater(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof com.talhanation.recruits.client.gui.worldmap.WorldMapScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "onWorldLoad", at = @At("HEAD"), cancellable = true)
    private void recruitsmapoverhaul$skipOriginalMapWorldLoad(LevelEvent.Load event, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "onWorldUnload", at = @At("HEAD"), cancellable = true)
    private void recruitsmapoverhaul$skipOriginalMapWorldUnload(LevelEvent.Unload event, CallbackInfo ci) {
        ci.cancel();
    }
}
