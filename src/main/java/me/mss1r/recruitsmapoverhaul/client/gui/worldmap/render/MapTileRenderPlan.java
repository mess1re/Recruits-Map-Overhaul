package me.mss1r.recruitsmapoverhaul.client.gui.worldmap.render;

import me.mss1r.recruitsmapoverhaul.client.map.cache.ChunkTile;

import java.util.ArrayList;
import java.util.List;

final class MapTileRenderPlan {
    static final int MAX_SOURCE_TILES_PER_FRAME = Integer.getInteger("recruitsmapoverhaul.maxRenderTiles", 256);
    static final int FAR_ZOOM_MAX_SOURCE_TILES = Integer.getInteger("recruitsmapoverhaul.farZoomMaxRenderTiles", 192);
    static final int MIN_TILE_SCREEN_PIXELS = Integer.getInteger("recruitsmapoverhaul.minTileScreenPixels", 64);
    static final int FAR_ZOOM_BASE_TILE_PIXELS = Integer.getInteger("recruitsmapoverhaul.farZoomBaseTilePixels", 48);

    private MapTileRenderPlan() {
    }

    static List<Tile> visibleTiles(double leftWorld,
                                   double rightWorld,
                                   double topWorld,
                                   double bottomWorld,
                                   double baseTileSize,
                                   int screenWidth,
                                   int screenHeight) {
        int level = chooseOverviewLevel(leftWorld, rightWorld, topWorld, bottomWorld,
                baseTileSize, screenWidth, screenHeight);
        double tileSize = tileSizeForLevel(baseTileSize, level);
        int startTileX = (int) Math.floor(leftWorld / tileSize) - 1;
        int endTileX = (int) Math.ceil(rightWorld / tileSize) + 1;
        int startTileZ = (int) Math.floor(topWorld / tileSize) - 1;
        int endTileZ = (int) Math.ceil(bottomWorld / tileSize) + 1;
        return collectTiles(level, startTileX, endTileX, startTileZ, endTileZ);
    }

    static List<Tile> collectTiles(int level, int startTileX, int endTileX, int startTileZ, int endTileZ) {
        if (endTileX < startTileX || endTileZ < startTileZ) {
            return List.of();
        }

        long total = (long) (endTileX - startTileX + 1) * (long) (endTileZ - startTileZ + 1);
        int expected = (int) Math.min(total, Integer.MAX_VALUE);
        List<Tile> tiles = new ArrayList<>(expected);
        for (int tileZ = startTileZ; tileZ <= endTileZ; tileZ++) {
            for (int tileX = startTileX; tileX <= endTileX; tileX++) {
                tiles.add(new Tile(level, tileX, tileZ));
            }
        }
        return tiles;
    }

    static int chooseOverviewLevel(double leftWorld,
                                   double rightWorld,
                                   double topWorld,
                                   double bottomWorld,
                                   double baseTileSize,
                                   int screenWidth,
                                   int screenHeight) {
        double worldWidth = Math.max(1.0, rightWorld - leftWorld);
        double worldHeight = Math.max(1.0, bottomWorld - topWorld);
        double baseTilePixelsX = baseTileSize * Math.max(1, screenWidth) / worldWidth;
        double baseTilePixelsZ = baseTileSize * Math.max(1, screenHeight) / worldHeight;
        int tileBudget = Math.min(baseTilePixelsX, baseTilePixelsZ) < FAR_ZOOM_BASE_TILE_PIXELS
                ? FAR_ZOOM_MAX_SOURCE_TILES
                : MAX_SOURCE_TILES_PER_FRAME;
        for (int level = 0; level <= ChunkTile.MAX_OVERVIEW_LEVEL; level++) {
            double tileSize = tileSizeForLevel(baseTileSize, level);
            int startTileX = (int) Math.floor(leftWorld / tileSize) - 1;
            int endTileX = (int) Math.ceil(rightWorld / tileSize) + 1;
            int startTileZ = (int) Math.floor(topWorld / tileSize) - 1;
            int endTileZ = (int) Math.ceil(bottomWorld / tileSize) + 1;
            long tileCount = (long) (endTileX - startTileX + 1) * (long) (endTileZ - startTileZ + 1);
            double tilePixelsX = tileSize * Math.max(1, screenWidth) / worldWidth;
            double tilePixelsZ = tileSize * Math.max(1, screenHeight) / worldHeight;
            if (tileCount <= tileBudget
                    && Math.min(tilePixelsX, tilePixelsZ) >= MIN_TILE_SCREEN_PIXELS) {
                return level;
            }
        }
        return ChunkTile.MAX_OVERVIEW_LEVEL;
    }

    static double tileSizeForLevel(double baseTileSize, int level) {
        return baseTileSize * (1 << Math.max(0, Math.min(ChunkTile.MAX_OVERVIEW_LEVEL, level)));
    }

    record Tile(int level, int x, int z) {
        Tile(int x, int z) {
            this(0, x, z);
        }
    }
}
