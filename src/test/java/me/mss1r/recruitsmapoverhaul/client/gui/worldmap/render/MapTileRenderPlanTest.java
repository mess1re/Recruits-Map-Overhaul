package me.mss1r.recruitsmapoverhaul.client.gui.worldmap.render;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTileRenderPlanTest {
    @Test
    void smallRangesKeepRowMajorOrder() {
        List<MapTileRenderPlan.Tile> tiles = MapTileRenderPlan.collectTiles(2, 3, 4, 5, 100);

        assertEquals(List.of(
                new MapTileRenderPlan.Tile(2, 4),
                new MapTileRenderPlan.Tile(3, 4),
                new MapTileRenderPlan.Tile(2, 5),
                new MapTileRenderPlan.Tile(3, 5)
        ), tiles);
    }

    @Test
    void hugeRangesAreCappedAndUnique() {
        List<MapTileRenderPlan.Tile> tiles = MapTileRenderPlan.collectTiles(-1000, 1000, -800, 800, 512);
        Set<MapTileRenderPlan.Tile> uniqueTiles = new HashSet<>(tiles);

        assertEquals(512, tiles.size());
        assertEquals(tiles.size(), uniqueTiles.size());
        assertTrue(tiles.contains(new MapTileRenderPlan.Tile(0, 0)));
        assertTrue(tiles.stream().allMatch(tile -> tile.x() >= -1000 && tile.x() <= 1000));
        assertTrue(tiles.stream().allMatch(tile -> tile.z() >= -800 && tile.z() <= 800));
    }

    @Test
    void visibleTilesUsesTheSameCapForZoomedOutScreens() {
        double tileSize = 160.0;
        double screenWorldWidth = 160.0 * 600.0;
        double screenWorldHeight = 160.0 * 400.0;

        List<MapTileRenderPlan.Tile> tiles = MapTileRenderPlan.visibleTiles(
                -screenWorldWidth / 2.0,
                screenWorldWidth / 2.0,
                -screenWorldHeight / 2.0,
                screenWorldHeight / 2.0,
                tileSize
        );

        assertEquals(MapTileRenderPlan.MAX_SOURCE_TILES_PER_FRAME, tiles.size());
        assertTrue(tiles.contains(new MapTileRenderPlan.Tile(0, 0)));
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
                        tileSize
                );

                assertTrue(tiles.size() <= MapTileRenderPlan.MAX_SOURCE_TILES_PER_FRAME);
                assertEquals(tiles.size(), new HashSet<>(tiles).size());
            }
        }
    }
}
