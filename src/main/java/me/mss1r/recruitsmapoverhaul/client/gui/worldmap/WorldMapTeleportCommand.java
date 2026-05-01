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
    private WorldMapTeleportCommand() {
    }

    public static boolean teleportFromMap(WorldMapScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null || minecraft.level == null) {
            return false;
        }

        BlockPos clickedPos = screen.getClickedBlockPos();
        ChunkPos chunk = new ChunkPos(clickedPos);
        if (minecraft.level.getChunkSource().getChunk(chunk.x, chunk.z, false) == null) {
            return false;
        }

        int safeY = resolveSafeY(minecraft.level, clickedPos.getX(), clickedPos.getZ());
        String command = String.format(Locale.ROOT,
                "tp @s %.1f %d %.1f",
                clickedPos.getX() + 0.5D,
                safeY,
                clickedPos.getZ() + 0.5D);

        minecraft.setScreen(null);
        if (!minecraft.player.connection.sendUnsignedCommand(command)) {
            minecraft.player.connection.sendCommand(command);
        }
        return true;
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
