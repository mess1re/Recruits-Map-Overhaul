package me.mss1r.recruitsmapoverhaul.client;

import com.talhanation.recruits.config.RecruitsClientConfig;
import me.mss1r.recruitsmapoverhaul.client.map.ChunkTileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MapOverhaulClientEvents {
    private static final int BACKGROUND_MAP_RADIUS = 8;
    private int mapTickCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!RecruitsClientConfig.UpdateMapTiles.get()) return;
        if ((mapTickCounter++ & 1) != 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        ChunkTileManager manager = ChunkTileManager.getInstance();
        manager.initialize(mc.level);
        if (mc.screen instanceof com.talhanation.recruits.client.gui.worldmap.WorldMapScreen) {
            manager.updateCurrentTile();
        } else {
            manager.updateAroundPlayer(BACKGROUND_MAP_RADIUS);
        }
    }

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide() && event.getLevel() instanceof Level level) {
            ChunkTileManager.getInstance().initialize(level);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ChunkTileManager.getInstance().close();
        }
    }
}
