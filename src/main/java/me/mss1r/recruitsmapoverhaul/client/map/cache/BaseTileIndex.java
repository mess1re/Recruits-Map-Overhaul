package me.mss1r.recruitsmapoverhaul.client.map.cache;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

final class BaseTileIndex {
    private final Set<Long> tiles;

    private BaseTileIndex(Set<Long> tiles) {
        this.tiles = tiles;
    }

    static BaseTileIndex scan(File directory) {
        Set<Long> tiles = new HashSet<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                addIfBaseTile(tiles, file);
            }
        }
        return new BaseTileIndex(tiles);
    }

    synchronized boolean hasAny(int minX, int maxX, int minZ, int maxZ) {
        for (long tile : tiles) {
            int x = unpackX(tile);
            int z = unpackZ(tile);
            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                return true;
            }
        }
        return false;
    }

    synchronized void add(int tileX, int tileZ) {
        tiles.add(pack(tileX, tileZ));
    }

    private static void addIfBaseTile(Set<Long> tiles, File file) {
        if (!file.isFile() || file.length() <= 0L) {
            return;
        }

        int[] coords = parseTileName(file.getName());
        if (coords != null) {
            tiles.add(pack(coords[0], coords[1]));
        }
    }

    private static int[] parseTileName(String name) {
        if (name == null || !name.endsWith(".png")) {
            return null;
        }

        int separator = name.indexOf('_');
        if (separator <= 0 || separator >= name.length() - 5) {
            return null;
        }

        try {
            int x = Integer.parseInt(name.substring(0, separator));
            int z = Integer.parseInt(name.substring(separator + 1, name.length() - 4));
            return new int[]{x, z};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
