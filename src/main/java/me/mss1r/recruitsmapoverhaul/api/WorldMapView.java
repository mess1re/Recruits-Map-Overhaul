package me.mss1r.recruitsmapoverhaul.api;

import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import me.mss1r.recruitsmapoverhaul.client.gui.worldmap.WorldMapCamera;
import me.mss1r.recruitsmapoverhaul.client.gui.worldmap.WorldMapScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

public final class WorldMapView {
    private final WorldMapScreen screen;
    private final WorldMapScreenAccess access;
    private final WorldMapCamera camera;

    public WorldMapView(WorldMapScreen screen, WorldMapScreenAccess access, WorldMapCamera camera) {
        this.screen = screen;
        this.access = access;
        this.camera = camera;
    }

    public WorldMapScreen screen() {
        return screen;
    }

    public WorldMapScreenAccess access() {
        return access;
    }

    public Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    public Player player() {
        return access.recruitsmapoverhaul$getPlayer();
    }

    public int screenWidth() {
        return screen.width;
    }

    public int screenHeight() {
        return screen.height;
    }

    public double offsetX() {
        return camera.offsetX();
    }

    public double offsetZ() {
        return camera.offsetZ();
    }

    public double scale() {
        return camera.scale();
    }

    public double worldToScreenX(double worldX) {
        return worldX * scale() + offsetX();
    }

    public double worldToScreenY(double worldZ) {
        return worldZ * scale() + offsetZ();
    }

    public double screenToWorldX(double screenX) {
        return (screenX - offsetX()) / scale();
    }

    public double screenToWorldZ(double screenY) {
        return (screenY - offsetZ()) / scale();
    }

    public BlockPos screenToWorld(double screenX, double screenY) {
        int worldX = (int) Math.floor(screenToWorldX(screenX));
        int worldZ = (int) Math.floor(screenToWorldZ(screenY));
        return new BlockPos(worldX, resolveSurfaceY(worldX, worldZ), worldZ);
    }

    public int resolveSurfaceY(int worldX, int worldZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return 64;

        ChunkPos chunk = new ChunkPos(worldX >> 4, worldZ >> 4);
        if (minecraft.level.getChunkSource().getChunk(chunk.x, chunk.z, false) == null) return 64;
        int y = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
        return Math.max(y, minecraft.level.getMinBuildHeight());
    }
}
