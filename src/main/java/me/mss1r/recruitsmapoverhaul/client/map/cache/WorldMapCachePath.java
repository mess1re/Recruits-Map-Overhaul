package me.mss1r.recruitsmapoverhaul.client.map.cache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class WorldMapCachePath {
    private static final int OVERVIEW_CACHE_VERSION = 1;
    private static final String OVERVIEW_DIRECTORY = "overview_v" + OVERVIEW_CACHE_VERSION;

    private final String storageId;
    private final File directory;
    private volatile BaseTileIndex baseTileIndex;

    private WorldMapCachePath(String storageId, File directory) {
        this.storageId = storageId;
        this.directory = directory;
    }

    static WorldMapCachePath resolve(Minecraft minecraft, String cacheVersion) {
        String storageId = detectStorageId(minecraft, cacheVersion);
        if (storageId == null || storageId.isBlank()) {
            storageId = "unknown_" + cacheVersion;
        }
        return new WorldMapCachePath(storageId, new File(minecraft.gameDirectory, "recruits/worldmap/" + storageId));
    }

    boolean sameStorage(WorldMapCachePath other) {
        return other != null && storageId.equals(other.storageId);
    }

    void ensureDirectory() {
        directory.mkdirs();
    }

    File tileFile(int tileX, int tileZ) {
        return new File(directory, tileX + "_" + tileZ + ".png");
    }

    File tileFile(int level, int tileX, int tileZ) {
        if (level <= 0) {
            return tileFile(tileX, tileZ);
        }
        return new File(new File(directory, OVERVIEW_DIRECTORY + "/l" + level), tileX + "_" + tileZ + ".png");
    }

    boolean hasAnyBaseTileInSubtree(int level, int tileX, int tileZ) {
        if (level <= 0) {
            File file = tileFile(tileX, tileZ);
            return file.exists() && file.length() > 0L;
        }

        int span = 1 << Math.min(level, ChunkTile.MAX_OVERVIEW_LEVEL);
        int minX = tileX * span;
        int minZ = tileZ * span;
        return baseIndex().hasAny(minX, minX + span - 1, minZ, minZ + span - 1);
    }

    void noteBaseTileChanged(int tileX, int tileZ) {
        baseIndex().add(tileX, tileZ);
    }

    private static String detectStorageId(Minecraft minecraft, String cacheVersion) {
        try {
            if (minecraft.getSingleplayerServer() != null) {
                var server = minecraft.getSingleplayerServer();
                java.nio.file.Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                long seed = 0L;
                try {
                    seed = server.overworld().getSeed();
                } catch (Exception ignored) {
                }

                String rawId = "sp|" + root + "|seed=" + seed + "|" + cacheVersion;
                return "sp_" + UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8));
            }

            ServerData serverData = minecraft.getCurrentServer();
            if (serverData != null && serverData.ip != null && !serverData.ip.isEmpty()) {
                String rawId = "mp|" + serverData.ip + "|" + cacheVersion;
                return "mp_" + UUID.nameUUIDFromBytes(rawId.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
        return "unknown_" + cacheVersion;
    }

    private BaseTileIndex baseIndex() {
        BaseTileIndex index = baseTileIndex;
        if (index != null) {
            return index;
        }

        synchronized (this) {
            if (baseTileIndex == null) {
                baseTileIndex = BaseTileIndex.scan(directory);
            }
            return baseTileIndex;
        }
    }
}
