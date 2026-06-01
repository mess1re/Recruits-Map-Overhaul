package me.mss1r.recruitsmapoverhaul.client.map.cache;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.File;
import java.io.IOException;

final class OverviewTileBuilder {
    private OverviewTileBuilder() {
    }

    static NativeImage build(WorldMapCachePath cachePath, int level, int tileX, int tileZ) {
        if (cachePath == null || level <= 0) {
            return null;
        }

        NativeImage[][] children = new NativeImage[2][2];
        try {
            boolean hasSource = false;
            for (int childZ = 0; childZ < 2; childZ++) {
                for (int childX = 0; childX < 2; childX++) {
                    int childLevel = level - 1;
                    int sourceTileX = tileX * 2 + childX;
                    int sourceTileZ = tileZ * 2 + childZ;
                    if (!cachePath.hasAnyBaseTileInSubtree(childLevel, sourceTileX, sourceTileZ)) {
                        continue;
                    }

                    NativeImage child = loadOrBuildChild(cachePath, childLevel, sourceTileX, sourceTileZ);
                    hasSource |= child != null;
                    children[childX][childZ] = child;
                }
            }

            if (!hasSource) {
                return null;
            }

            NativeImage overview = new NativeImage(NativeImage.Format.RGBA, ChunkTile.TILE_PIXEL_SIZE, ChunkTile.TILE_PIXEL_SIZE, false);
            boolean hasVisiblePixel = false;
            for (int y = 0; y < ChunkTile.TILE_PIXEL_SIZE; y++) {
                for (int x = 0; x < ChunkTile.TILE_PIXEL_SIZE; x++) {
                    int pixel = averagePixel(children, x, y);
                    overview.setPixelRGBA(x, y, pixel);
                    hasVisiblePixel |= ((pixel >> 24) & 0xFF) > 0;
                }
            }

            if (!hasVisiblePixel) {
                overview.close();
                return null;
            }
            return overview;
        } finally {
            for (NativeImage[] column : children) {
                for (NativeImage child : column) {
                    try {
                        if (child != null) child.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private static NativeImage loadOrBuildChild(WorldMapCachePath cachePath, int level, int tileX, int tileZ) {
        File file = cachePath.tileFile(level, tileX, tileZ);
        NativeImage loaded = load(file);
        if (loaded != null || level <= 0) {
            return loaded;
        }

        NativeImage generated = build(cachePath, level, tileX, tileZ);
        if (generated != null) {
            write(file, generated);
        }
        return generated;
    }

    private static NativeImage load(File file) {
        if (file == null || !file.exists() || file.length() <= 0L) {
            return null;
        }

        try {
            return ChunkTile.readImage(file);
        } catch (Exception ignored) {
        }
        return null;
    }

    static void write(File file, NativeImage image) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            image.writeToFile(file);
        } catch (IOException ignored) {
        }
    }

    private static int averagePixel(NativeImage[][] children, int x, int y) {
        int alpha = 0;
        int blue = 0;
        int green = 0;
        int red = 0;
        int count = 0;

        for (int sampleZ = 0; sampleZ < 2; sampleZ++) {
            for (int sampleX = 0; sampleX < 2; sampleX++) {
                int sourceX = x * 2 + sampleX;
                int sourceY = y * 2 + sampleZ;
                NativeImage child = children[sourceX / ChunkTile.TILE_PIXEL_SIZE][sourceY / ChunkTile.TILE_PIXEL_SIZE];
                if (child == null) {
                    continue;
                }

                int pixel = child.getPixelRGBA(sourceX % ChunkTile.TILE_PIXEL_SIZE, sourceY % ChunkTile.TILE_PIXEL_SIZE);
                int pixelAlpha = (pixel >> 24) & 0xFF;
                if (pixelAlpha == 0) {
                    continue;
                }

                alpha += pixelAlpha;
                blue += (pixel >> 16) & 0xFF;
                green += (pixel >> 8) & 0xFF;
                red += pixel & 0xFF;
                count++;
            }
        }

        if (count == 0) {
            return 0x00000000;
        }
        return ((alpha / count) << 24)
                | ((blue / count) << 16)
                | ((green / count) << 8)
                | (red / count);
    }
}
