package me.mss1r.recruitsmapoverhaul.client.gui.worldmap.render;

import java.util.ArrayList;
import java.util.List;

final class MapTileRenderPlan {
    static final int MAX_SOURCE_TILES_PER_FRAME = Integer.getInteger("recruitsmapoverhaul.maxRenderTiles", 512);

    private MapTileRenderPlan() {
    }

    static List<Tile> visibleTiles(double leftWorld,
                                   double rightWorld,
                                   double topWorld,
                                   double bottomWorld,
                                   double tileSize) {
        int startTileX = (int) Math.floor(leftWorld / tileSize) - 1;
        int endTileX = (int) Math.ceil(rightWorld / tileSize) + 1;
        int startTileZ = (int) Math.floor(topWorld / tileSize) - 1;
        int endTileZ = (int) Math.ceil(bottomWorld / tileSize) + 1;
        return collectTiles(startTileX, endTileX, startTileZ, endTileZ, MAX_SOURCE_TILES_PER_FRAME);
    }

    static List<Tile> collectTiles(int startTileX, int endTileX, int startTileZ, int endTileZ, int maxTiles) {
        if (endTileX < startTileX || endTileZ < startTileZ || maxTiles <= 0) {
            return List.of();
        }

        long total = (long) (endTileX - startTileX + 1) * (long) (endTileZ - startTileZ + 1);
        int expected = (int) Math.min(total, maxTiles);
        List<Tile> tiles = new ArrayList<>(expected);
        if (total <= maxTiles) {
            for (int tileZ = startTileZ; tileZ <= endTileZ; tileZ++) {
                for (int tileX = startTileX; tileX <= endTileX; tileX++) {
                    tiles.add(new Tile(tileX, tileZ));
                }
            }
            return tiles;
        }

        int centerTileX = Math.floorDiv(startTileX + endTileX, 2);
        int centerTileZ = Math.floorDiv(startTileZ + endTileZ, 2);
        int maxRadius = Math.max(
                Math.max(Math.abs(centerTileX - startTileX), Math.abs(centerTileX - endTileX)),
                Math.max(Math.abs(centerTileZ - startTileZ), Math.abs(centerTileZ - endTileZ))
        );

        addIfVisible(tiles, centerTileX, centerTileZ, startTileX, endTileX, startTileZ, endTileZ, maxTiles);
        for (int radius = 1; radius <= maxRadius && tiles.size() < maxTiles; radius++) {
            int minX = centerTileX - radius;
            int maxX = centerTileX + radius;
            int minZ = centerTileZ - radius;
            int maxZ = centerTileZ + radius;

            for (int tileX = minX; tileX <= maxX && tiles.size() < maxTiles; tileX++) {
                addIfVisible(tiles, tileX, minZ, startTileX, endTileX, startTileZ, endTileZ, maxTiles);
            }
            for (int tileZ = minZ + 1; tileZ <= maxZ - 1 && tiles.size() < maxTiles; tileZ++) {
                addIfVisible(tiles, maxX, tileZ, startTileX, endTileX, startTileZ, endTileZ, maxTiles);
            }
            for (int tileX = maxX; tileX >= minX && tiles.size() < maxTiles; tileX--) {
                addIfVisible(tiles, tileX, maxZ, startTileX, endTileX, startTileZ, endTileZ, maxTiles);
            }
            for (int tileZ = maxZ - 1; tileZ >= minZ + 1 && tiles.size() < maxTiles; tileZ--) {
                addIfVisible(tiles, minX, tileZ, startTileX, endTileX, startTileZ, endTileZ, maxTiles);
            }
        }
        return tiles;
    }

    private static void addIfVisible(List<Tile> tiles,
                                     int tileX,
                                     int tileZ,
                                     int startTileX,
                                     int endTileX,
                                     int startTileZ,
                                     int endTileZ,
                                     int maxTiles) {
        if (tiles.size() >= maxTiles) {
            return;
        }
        if (tileX < startTileX || tileX > endTileX || tileZ < startTileZ || tileZ > endTileZ) {
            return;
        }
        tiles.add(new Tile(tileX, tileZ));
    }

    record Tile(int x, int z) {
    }
}
