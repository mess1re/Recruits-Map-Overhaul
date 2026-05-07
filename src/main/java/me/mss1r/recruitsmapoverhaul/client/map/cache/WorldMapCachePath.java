package me.mss1r.recruitsmapoverhaul.client.map.cache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class WorldMapCachePath {
    private final String storageId;
    private final File directory;

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
}
