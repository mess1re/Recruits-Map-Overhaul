package me.mss1r.recruitsmapoverhaul.client.gui.worldmap.render;

import com.mojang.blaze3d.systems.RenderSystem;
import me.mss1r.recruitsmapoverhaul.client.gui.worldmap.WorldMapCamera;
import me.mss1r.recruitsmapoverhaul.client.map.cache.ChunkTile;
import me.mss1r.recruitsmapoverhaul.client.map.cache.ChunkTileManager;
import net.minecraft.client.gui.GuiGraphics;

final class WorldMapTileRenderer {
    private static final int MAX_TILE_DRAWS_PER_FRAME = Integer.getInteger("recruitsmapoverhaul.maxTileDraws", 256);
    private static final int MAX_TILE_VISITS_PER_FRAME = Integer.getInteger("recruitsmapoverhaul.maxTileVisits", 4096);
    private static final int MAX_MISSING_TILE_FALLBACK_DEPTH =
            Integer.getInteger("recruitsmapoverhaul.maxMissingTileFallbackDepth", 4);
    private static final float CHILD_DETAIL_MIN_TILE_PIXELS =
            Float.parseFloat(System.getProperty("recruitsmapoverhaul.childDetailMinTilePixels", "640"));
    private static final float MISSING_TILE_FALLBACK_MIN_PIXELS =
            Float.parseFloat(System.getProperty("recruitsmapoverhaul.missingTileFallbackMinPixels", "16"));

    private final ChunkTileManager tileManager;

    WorldMapTileRenderer(ChunkTileManager tileManager) {
        this.tileManager = tileManager;
    }

    void render(GuiGraphics guiGraphics,
                WorldMapCamera camera,
                int screenWidth,
                int screenHeight,
                float brightness,
                MapFramebufferPass framebufferPass) {
        tileManager.prepareRenderFrame();

        double tileSize = ChunkTile.TILE_PIXEL_SIZE;
        MapFramebufferPass.Frame frame = framebufferPass.begin(guiGraphics, camera, screenWidth, screenHeight);
        double scaledTileSize = tileSize * frame.fboScale();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderBudget budget = new RenderBudget(MAX_TILE_DRAWS_PER_FRAME, MAX_TILE_VISITS_PER_FRAME);

        for (MapTileRenderPlan.Tile tilePos : MapTileRenderPlan.visibleTiles(
                frame.leftWorld(), frame.rightWorld(), frame.topWorld(), frame.bottomWorld(),
                tileSize, frame.screenWidth(), frame.screenHeight())) {
            if (!budget.hasRoom()) {
                break;
            }

            renderTile(guiGraphics, tilePos, frame, scaledTileSize, brightness, budget);
        }

        framebufferPass.endAndBlit(guiGraphics, frame);
    }

    private void renderTile(GuiGraphics guiGraphics,
                            MapTileRenderPlan.Tile tilePos,
                            MapFramebufferPass.Frame frame,
                            double scaledTileSize,
                            float brightness,
                            RenderBudget budget) {
        int level = tilePos.level();
        int tileX = tilePos.x();
        int tileZ = tilePos.z();
        double leveledTileSize = scaledTileSize * (1 << level);
        float x1 = (float) (frame.renderOffsetX() + tileX * leveledTileSize);
        float z1 = (float) (frame.renderOffsetZ() + tileZ * leveledTileSize);
        float x2 = (float) (frame.renderOffsetX() + (tileX + 1) * leveledTileSize);
        float z2 = (float) (frame.renderOffsetZ() + (tileZ + 1) * leveledTileSize);
        float width = Math.max(1.0f, x2 - x1);
        float height = Math.max(1.0f, z2 - z1);

        renderTileTree(guiGraphics, level, tileX, tileZ, x1, z1, width, height, brightness, budget,
                MAX_MISSING_TILE_FALLBACK_DEPTH);
    }

    private boolean renderTileTree(GuiGraphics guiGraphics,
                                   int level,
                                   int tileX,
                                   int tileZ,
                                   float x,
                                   float y,
                                   float width,
                                   float height,
                                   float brightness,
                                   RenderBudget budget,
                                   int missingFallbackDepth) {
        if (!budget.tryVisit()) {
            return false;
        }

        if (level > 0 && !tileManager.hasAnySourceTile(level, tileX, tileZ)) {
            return false;
        }

        ChunkTile tile = tileManager.getTileIfPresent(level, tileX, tileZ);
        boolean rendered = false;

        if (tile != null) {
            if (budget.tryUseDraw()) {
                tile.render(guiGraphics, x, y, width, height, brightness);
                rendered = true;
            }
            if (level <= 0 || !shouldRefineTransparentTile(tile, width, height)) {
                return rendered;
            }
        }

        if (level <= 0 || !budget.hasRoom()) {
            return rendered;
        }

        boolean missingTile = tile == null;
        if (missingTile && !shouldFallbackMissingTile(width, height, missingFallbackDepth)) {
            return false;
        }
        if (!missingTile && !shouldRefineTile(width, height)) {
            return rendered;
        }

        int childLevel = level - 1;
        float childWidth = width * 0.5f;
        float childHeight = height * 0.5f;
        int childMissingFallbackDepth = missingTile ? missingFallbackDepth - 1 : missingFallbackDepth;
        for (int childZ = 0; childZ < 2 && budget.hasRoom(); childZ++) {
            for (int childX = 0; childX < 2 && budget.hasRoom(); childX++) {
                rendered |= renderTileTree(
                        guiGraphics,
                        childLevel,
                        tileX * 2 + childX,
                        tileZ * 2 + childZ,
                        x + childX * childWidth,
                        y + childZ * childHeight,
                        childWidth,
                        childHeight,
                        brightness,
                        budget,
                        childMissingFallbackDepth
                );
            }
        }

        return rendered;
    }

    private boolean shouldRefineTransparentTile(ChunkTile tile, float width, float height) {
        return shouldRefineTile(width, height) && tile.hasTransparentPixels();
    }

    private boolean shouldRefineTile(float width, float height) {
        return Math.max(width, height) >= CHILD_DETAIL_MIN_TILE_PIXELS;
    }

    private boolean shouldFallbackMissingTile(float width, float height, int missingFallbackDepth) {
        return missingFallbackDepth > 0 && Math.max(width, height) >= MISSING_TILE_FALLBACK_MIN_PIXELS;
    }

    private static final class RenderBudget {
        private int remainingDraws;
        private int remainingVisits;

        private RenderBudget(int remainingDraws, int remainingVisits) {
            this.remainingDraws = Math.max(0, remainingDraws);
            this.remainingVisits = Math.max(0, remainingVisits);
        }

        private boolean hasRoom() {
            return remainingDraws > 0 && remainingVisits > 0;
        }

        private boolean tryVisit() {
            if (remainingVisits <= 0) {
                return false;
            }
            remainingVisits--;
            return true;
        }

        private boolean tryUseDraw() {
            if (remainingDraws <= 0) {
                return false;
            }
            remainingDraws--;
            return true;
        }
    }
}
