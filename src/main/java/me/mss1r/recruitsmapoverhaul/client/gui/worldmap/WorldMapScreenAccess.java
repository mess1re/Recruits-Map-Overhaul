package me.mss1r.recruitsmapoverhaul.client.gui.worldmap;

import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsRoute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

public interface WorldMapScreenAccess {
    Player recruitsmapoverhaul$getPlayer();

    double recruitsmapoverhaul$getOffsetX();
    void recruitsmapoverhaul$setOffsetX(double value);

    double recruitsmapoverhaul$getOffsetZ();
    void recruitsmapoverhaul$setOffsetZ(double value);

    double recruitsmapoverhaul$getScale();
    void recruitsmapoverhaul$setScale(double value);

    double recruitsmapoverhaul$getLastMouseX();
    void recruitsmapoverhaul$setLastMouseX(double value);

    double recruitsmapoverhaul$getLastMouseY();
    void recruitsmapoverhaul$setLastMouseY(double value);

    boolean recruitsmapoverhaul$isDragging();
    void recruitsmapoverhaul$setDragging(boolean value);

    ChunkPos recruitsmapoverhaul$getHoveredChunk();
    void recruitsmapoverhaul$setHoveredChunk(ChunkPos value);

    ChunkPos recruitsmapoverhaul$getSelectedChunk();
    void recruitsmapoverhaul$setSelectedChunk(ChunkPos value);

    int recruitsmapoverhaul$getClickedBlockX();
    int recruitsmapoverhaul$getClickedBlockZ();
    void recruitsmapoverhaul$setClickedBlock(int x, int z);

    int recruitsmapoverhaul$getHoverBlockX();
    int recruitsmapoverhaul$getHoverBlockZ();
    void recruitsmapoverhaul$setHoverBlock(int x, int z);

    RecruitsClaim recruitsmapoverhaul$getSelectedClaim();
    void recruitsmapoverhaul$setSelectedClaim(RecruitsClaim value);

    RecruitsRoute recruitsmapoverhaul$getSelectedRoute();
    void recruitsmapoverhaul$setSelectedRoute(RecruitsRoute value);

    int recruitsmapoverhaul$getSnapshotWorldX();
    int recruitsmapoverhaul$getSnapshotWorldZ();
    void recruitsmapoverhaul$setSnapshotWorld(int x, int z);

    boolean recruitsmapoverhaul$isClaimTransparency();
    void recruitsmapoverhaul$setClaimTransparency(boolean value);

    double recruitsmapoverhaul$getMouseX();
    double recruitsmapoverhaul$getMouseY();
    void recruitsmapoverhaul$setMouse(double x, double y);
}
