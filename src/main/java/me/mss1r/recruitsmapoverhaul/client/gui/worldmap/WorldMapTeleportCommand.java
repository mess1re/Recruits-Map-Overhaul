package me.mss1r.recruitsmapoverhaul.client.gui.worldmap;

import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

public final class WorldMapTeleportCommand {
    private static final int SURFACE_CORRECTION_TIMEOUT_TICKS = 80;

    private static BlockPos pendingSurfaceCorrectionPos;
    private static int pendingSurfaceCorrectionTicks;

    private WorldMapTeleportCommand() {
    }

    public static boolean teleportFromMap(WorldMapScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null || minecraft.level == null) {
            return false;
        }

        BlockPos clickedPos = screen.getClickedBlockPos();
        minecraft.setScreen(null);

        if (!isChunkLoaded(minecraft.level, clickedPos)) {
            sendCommand(minecraft, buildSkyTeleportCommand(minecraft.level, clickedPos));
            scheduleSurfaceCorrection(clickedPos);
            return true;
        }

        sendCommand(minecraft, buildLoadedChunkTeleportCommand(minecraft.level, clickedPos));
        return true;
    }

    public static void tickPendingTeleport() {
        if (pendingSurfaceCorrectionPos == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.player.connection == null) {
            clearSurfaceCorrection();
            return;
        }

        if (++pendingSurfaceCorrectionTicks > SURFACE_CORRECTION_TIMEOUT_TICKS) {
            clearSurfaceCorrection();
            return;
        }

        if (!isChunkLoaded(minecraft.level, pendingSurfaceCorrectionPos)) {
            return;
        }

        sendCommand(minecraft, buildLoadedChunkTeleportCommand(minecraft.level, pendingSurfaceCorrectionPos));
        clearSurfaceCorrection();
    }

    private static boolean isChunkLoaded(ClientLevel level, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        return level.getChunkSource().getChunk(chunk.x, chunk.z, false) != null;
    }

    private static void scheduleSurfaceCorrection(BlockPos pos) {
        pendingSurfaceCorrectionPos = pos;
        pendingSurfaceCorrectionTicks = 0;
    }

    private static void clearSurfaceCorrection() {
        pendingSurfaceCorrectionPos = null;
        pendingSurfaceCorrectionTicks = 0;
    }

    private static void sendCommand(Minecraft minecraft, String command) {
        if (!minecraft.player.connection.sendUnsignedCommand(command)) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    private static String buildLoadedChunkTeleportCommand(ClientLevel level, BlockPos clickedPos) {
        int safeY = resolveSafeY(level, clickedPos.getX(), clickedPos.getZ());
        return String.format(Locale.ROOT,
                "tp @s %.1f %d %.1f",
                clickedPos.getX() + 0.5D,
                safeY,
                clickedPos.getZ() + 0.5D);
    }

    private static String buildSkyTeleportCommand(ClientLevel level, BlockPos clickedPos) {
        int skyY = level.getMaxBuildHeight() - 4;
        return String.format(Locale.ROOT,
                "tp @s %.1f %d %.1f",
                clickedPos.getX() + 0.5D,
                skyY,
                clickedPos.getZ() + 0.5D);
    }

    private static int resolveSafeY(ClientLevel level, int x, int z) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        int surfaceY = clamp(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), minY, maxY);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, maxY, z);
        for (int y = maxY; y >= minY; y--) {
            mutable.set(x, y, z);
            if (canStandAt(level, mutable)) {
                return y;
            }
        }

        return surfaceY;
    }

    private static boolean canStandAt(ClientLevel level, BlockPos feetPos) {
        return isOpen(level, feetPos)
                && isOpen(level, feetPos.above())
                && hasSupport(level, feetPos.below());
    }

    private static boolean isOpen(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty()
                && !state.getFluidState().is(FluidTags.LAVA);
    }

    private static boolean hasSupport(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty()
                || (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.LAVA));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
