package me.mss1r.recruitsmapoverhaul.client.map;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

final class MapStateSampler {
    private MapStateSampler() {
    }

    static MapSample findTopMapSample(ClientLevel level, int worldX, int worldZ) {
        int startY = Math.min(level.getMaxBuildHeight() - 1, getSurfaceHeight(level, worldX, worldZ) + 3);
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(worldX, startY, worldZ);

        for (int y = startY; y >= minY; y--) {
            mutable.setY(y);
            BlockState state = level.getBlockState(mutable);
            if (isRenderableMapState(level, mutable, state)) {
                return new MapSample(mutable.immutable(), state, y);
            }
        }

        return null;
    }

    static MapSample findUnderWaterSample(ClientLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos mutable = pos.mutable();
        int minY = level.getMinBuildHeight();
        int scanned = 0;

        while (mutable.getY() > minY && scanned < 16) {
            mutable.move(Direction.DOWN);
            scanned++;
            BlockState state = level.getBlockState(mutable);
            if (state.isAir() || isWaterLike(state)) {
                continue;
            }
            if (isRenderableMapState(level, mutable, state)) {
                return new MapSample(mutable.immutable(), state, mutable.getY());
            }
        }

        return null;
    }

    static int getSurfaceHeight(ClientLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    static int getRenderableHeight(ClientLevel level, int x, int z, int fallback) {
        MapSample sample = findTopMapSample(level, x, z);
        return sample != null ? sample.height() : fallback;
    }

    static int getWaterDepth(ClientLevel level, BlockPos pos) {
        int depth = 0;
        BlockPos.MutableBlockPos mutable = pos.mutable();

        while (isWaterLike(level.getBlockState(mutable)) && mutable.getY() > level.getMinBuildHeight()) {
            depth++;
            mutable.move(Direction.DOWN);
        }

        return depth;
    }

    static int countWaterNeighbors(ClientLevel level, BlockPos pos) {
        int count = 0;
        if (isWaterLike(level.getBlockState(pos.north()))) {
            count++;
        }
        if (isWaterLike(level.getBlockState(pos.south()))) {
            count++;
        }
        if (isWaterLike(level.getBlockState(pos.east()))) {
            count++;
        }
        if (isWaterLike(level.getBlockState(pos.west()))) {
            count++;
        }
        return count;
    }

    static boolean isWaterLike(BlockState state) {
        return state.getFluidState().is(Fluids.WATER);
    }

    private static boolean isRenderableMapState(ClientLevel level, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }

        if (!state.getFluidState().isEmpty()) {
            return true;
        }

        Block block = state.getBlock();
        if (block == Blocks.GRASS || block == Blocks.TORCH || block == Blocks.GLASS || block == Blocks.GLASS_PANE) {
            return false;
        }

        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return false;
        }

        MapColor mapColor = state.getMapColor(level, pos);
        return mapColor != null && mapColor.col != 0;
    }
}
