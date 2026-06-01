package me.mss1r.recruitsmapoverhaul.client.gui.worldmap.render;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTileRenderPlanTest {
    @Test
    void smallRangesKeepRowMajorOrder() {
        List<MapTileRenderPlan.Tile> tiles = MapTileRenderPlan.collectTiles(0, 2, 3, 4, 5);

        assertEquals(List.of(
                new MapTileRenderPlan.Tile(2, 4),
                new MapTileRenderPlan.Tile(3, 4),
                new MapTileRenderPlan.Tile(2, 5),
                new MapTileRenderPlan.Tile(3, 5)
        ), tiles);
    }

    @Test
    void zoomedOutScreensUseOverviewTilesInsteadOfClippingSourceTiles() {
        double tileSize = 160.0;
        int screenWidth = 1920;
        int screenHeight = 1080;
        double scale = 0.0625;
        double halfWidth = screenWidth / scale / 2.0;
        double halfHeight = screenHeight / scale / 2.0;

        List<MapTileRenderPlan.Tile> tiles = MapTileRenderPlan.visibleTiles(
                -halfWidth,
                halfWidth,
                -halfHeight,
                halfHeight,
                tileSize,
                screenWidth,
                screenHeight
        );
        Set<MapTileRenderPlan.Tile> uniqueTiles = new HashSet<>(tiles);

        assertTrue(tiles.size() <= MapTileRenderPlan.FAR_ZOOM_MAX_SOURCE_TILES);
        assertEquals(tiles.size(), uniqueTiles.size());
        assertTrue(tiles.stream().allMatch(tile -> tile.level() == 4));
        assertCoversWorldRange(tiles, -halfWidth, halfWidth, -halfHeight, halfHeight, tileSize);
    }

    @Test
    void farZoomPrefersCoarserOverviewTilesToAvoidTextureThrashing() {
        double tileSize = 160.0;
        int screenWidth = 1920;
        int screenHeight = 1080;
        double scale = 0.1;
        double halfWidth = screenWidth / scale / 2.0;
        double halfHeight = screenHeight / scale / 2.0;

        List<MapTileRenderPlan.Tile> tiles = MapTileRenderPlan.visibleTiles(
                -halfWidth,
                halfWidth,
                -halfHeight,
                halfHeight,
                tileSize,
                screenWidth,
                screenHeight
        );

        assertTrue(tiles.size() <= MapTileRenderPlan.FAR_ZOOM_MAX_SOURCE_TILES);
        assertTrue(tiles.stream().allMatch(tile -> tile.level() == 4));
        assertCoversWorldRange(tiles, -halfWidth, halfWidth, -halfHeight, halfHeight, tileSize);
    }

    @Test
    void visibleTilesKeepDetailedLevelWhenItFitsTheBudget() {
        double tileSize = 160.0;
        double screenWorldWidth = 160.0 * 10.0;
        double screenWorldHeight = 160.0 * 8.0;

        List<MapTileRenderPlan.Tile> tiles = MapTileRenderPlan.visibleTiles(
                -screenWorldWidth / 2.0,
                screenWorldWidth / 2.0,
                -screenWorldHeight / 2.0,
                screenWorldHeight / 2.0,
                tileSize,
                1920,
                1080
        );

        assertTrue(tiles.size() < MapTileRenderPlan.MAX_SOURCE_TILES_PER_FRAME);
        assertTrue(tiles.stream().allMatch(tile -> tile.level() == 0));
        assertTrue(tiles.contains(new MapTileRenderPlan.Tile(0, 0)));
    }

    @Test
    void minimumZoomOnLargeDisplaysStillCoversTheScreenWithinBudget() {
        double tileSize = 160.0;
        int screenWidth = 7680;
        int screenHeight = 4320;
        double scale = 0.0625;
        double halfWidth = screenWidth / scale / 2.0;
        double halfHeight = screenHeight / scale / 2.0;

        List<MapTileRenderPlan.Tile> tiles = MapTileRenderPlan.visibleTiles(
                -halfWidth,
                halfWidth,
                -halfHeight,
                halfHeight,
                tileSize,
                screenWidth,
                screenHeight
        );

        assertTrue(tiles.size() <= MapTileRenderPlan.MAX_SOURCE_TILES_PER_FRAME);
        assertTrue(tiles.stream().allMatch(tile -> tile.level() == 6));
        assertCoversWorldRange(tiles, -halfWidth, halfWidth, -halfHeight, halfHeight, tileSize);
    }

    @Test
    void stressZoomOutAndPanNeverExceedsFrameTileBudget() {
        double tileSize = 160.0;
        int screenWidth = 1920;
        int screenHeight = 1080;
        double[] scales = {4.0, 1.0, 0.5, 0.25, 0.125, 0.0625};

        for (double scale : scales) {
            for (int step = 0; step < 2_000; step++) {
                double centerWorldX = (step - 1_000) * 512.0;
                double centerWorldZ = Math.sin(step * 0.11) * 64_000.0;
                double halfWidth = screenWidth / scale / 2.0;
                double halfHeight = screenHeight / scale / 2.0;

                List<MapTileRenderPlan.Tile> tiles = MapTileRenderPlan.visibleTiles(
                        centerWorldX - halfWidth,
                        centerWorldX + halfWidth,
                        centerWorldZ - halfHeight,
                        centerWorldZ + halfHeight,
                        tileSize,
                        screenWidth,
                        screenHeight
                );

                assertTrue(tiles.size() <= MapTileRenderPlan.MAX_SOURCE_TILES_PER_FRAME);
                assertEquals(tiles.size(), new HashSet<>(tiles).size());
                assertCoversWorldRange(tiles,
                        centerWorldX - halfWidth,
                        centerWorldX + halfWidth,
                        centerWorldZ - halfHeight,
                        centerWorldZ + halfHeight,
                        tileSize
                );
            }
        }
    }

    private static void assertCoversWorldRange(List<MapTileRenderPlan.Tile> tiles,
                                               double leftWorld,
                                               double rightWorld,
                                               double topWorld,
                                               double bottomWorld,
                                               double baseTileSize) {
        assertFalse(tiles.isEmpty());

        int level = tiles.get(0).level();
        assertTrue(tiles.stream().allMatch(tile -> tile.level() == level));

        double tileSize = MapTileRenderPlan.tileSizeForLevel(baseTileSize, level);
        int startTileX = (int) Math.floor(leftWorld / tileSize) - 1;
        int endTileX = (int) Math.ceil(rightWorld / tileSize) + 1;
        int startTileZ = (int) Math.floor(topWorld / tileSize) - 1;
        int endTileZ = (int) Math.ceil(bottomWorld / tileSize) + 1;

        assertEquals(startTileX, tiles.stream().mapToInt(MapTileRenderPlan.Tile::x).min().orElseThrow());
        assertEquals(endTileX, tiles.stream().mapToInt(MapTileRenderPlan.Tile::x).max().orElseThrow());
        assertEquals(startTileZ, tiles.stream().mapToInt(MapTileRenderPlan.Tile::z).min().orElseThrow());
        assertEquals(endTileZ, tiles.stream().mapToInt(MapTileRenderPlan.Tile::z).max().orElseThrow());
        assertEquals((long) (endTileX - startTileX + 1) * (long) (endTileZ - startTileZ + 1), tiles.size());
    }
}
