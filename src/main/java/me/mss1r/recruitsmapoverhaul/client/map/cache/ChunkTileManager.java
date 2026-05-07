package me.mss1r.recruitsmapoverhaul.client.map.cache;

import me.mss1r.recruitsmapoverhaul.client.map.sampling.ChunkImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChunkTileManager {
    private static final String CACHE_VERSION = "v1";
    private static final long TILE_SAVE_INTERVAL_MS = 5000L;
    private static final long VISIBLE_QUEUE_INTERVAL_MS = 80L;
    private static final long BACKGROUND_QUEUE_INTERVAL_MS = 160L;
    private static final long CHUNK_PROCESS_INTERVAL_MS = 16L;
    private static final long CURRENT_CHUNK_REFRESH_INTERVAL_MS = 1500L;
    private static final long NEARBY_CHUNK_REFRESH_INTERVAL_MS = 5000L;
    private static final long CHUNK_UPDATE_TIME_BUDGET_NS = 3_500_000L;
    private static final int CHUNK_UPDATES_PER_PASS = 6;
    private static final int CHUNK_ATTEMPTS_PER_PASS = 96;
    private static final int MAX_VISIBLE_CHUNK_SCAN = 6144;
    private static final int NEARBY_REFRESH_RADIUS = 1;
    private static ChunkTileManager instance;
    private final Map<String, ChunkTile> loadedTiles = new HashMap<>();
    private final ArrayDeque<ChunkPos> dirtyChunkUpdates = new ArrayDeque<>();
    private final ArrayDeque<ChunkPos> pendingChunkUpdates = new ArrayDeque<>();
    private final Set<Long> dirtyChunkKeys = new HashSet<>();
    private final Set<Long> pendingChunkKeys = new HashSet<>();
    private final Set<String> missingTileKeys = new HashSet<>();
    private final AsyncTileSaver tileSaver = new AsyncTileSaver();
    private final Minecraft mc = Minecraft.getInstance();
    private WorldMapCachePath cachePath;
    private int currentTileX = Integer.MAX_VALUE;
    private int currentTileZ = Integer.MAX_VALUE;
    private final Map<String, Long> lastUpdateTimes = new HashMap<>();
    private final Map<String, Long> lastSaveTimes = new HashMap<>();
    private long lastVisibleQueueTime = 0L;
    private long lastBackgroundQueueTime = 0L;
    private long lastChunkProcessTime = 0L;

    public static ChunkTileManager getInstance() {
        if (instance == null) instance = new ChunkTileManager();
        return instance;
    }

    public void initialize(Level level) {
        if (level == null) return;
        WorldMapCachePath newCachePath = WorldMapCachePath.resolve(mc, CACHE_VERSION);

        if (this.cachePath == null || !this.cachePath.sameStorage(newCachePath)) {
            saveAndReleaseTiles();
            resetRuntimeState();
            this.cachePath = newCachePath;
        }

        this.cachePath.ensureDirectory();
    }

    public void updateCurrentTile() {
        if (mc.level == null || mc.player == null) return;
        if (cachePath == null) initialize(mc.level);

        int chunkX = mc.player.chunkPosition().x;
        int chunkZ = mc.player.chunkPosition().z;
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        String currentChunkKey = "chunk:" + chunkX + "_" + chunkZ;

        if (!isChunkReadyForMap(chunkPos)) return;
        if (isChunkExplored(chunkPos)) {
            if (shouldRefreshChunk(chunkPos, System.currentTimeMillis(), CURRENT_CHUNK_REFRESH_INTERVAL_MS)) {
                updateChunk(chunkPos);
            }
            currentTileX = chunkX;
            currentTileZ = chunkZ;
            return;
        }

        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTimes.get(currentChunkKey);

        if (chunkX != currentTileX || chunkZ != currentTileZ ||
                lastUpdate == null || currentTime - lastUpdate > 1000L) {
            updateChunk(chunkPos);
            currentTileX = chunkX;
            currentTileZ = chunkZ;
        }
    }

    public void warmupAroundPlayer(int tileRadius) {
        if (mc.level == null || mc.player == null) return;

        if (tileRadius <= 0) {
            ChunkPos playerChunk = mc.player.chunkPosition();
            if (!isChunkExplored(playerChunk)) {
                updateChunk(playerChunk);
            }
            return;
        }

        int centerTileX = ChunkTile.chunkToTileCoord(mc.player.chunkPosition().x);
        int centerTileZ = ChunkTile.chunkToTileCoord(mc.player.chunkPosition().z);

        for (int dz = -tileRadius; dz <= tileRadius; dz++) {
            for (int dx = -tileRadius; dx <= tileRadius; dx++) {
                updateTile(centerTileX + dx, centerTileZ + dz);
            }
        }
    }

    private void updateTile(int tileX, int tileZ) {
        ChunkTile tile = getOrCreateTile(tileX, tileZ);
        File tileFile = getTileFile(tileX, tileZ);
        if (tileFile.exists()) tile.mergeWithExistingTile(tileFile);
        updateOnlyLoadedChunks(tile);
        queueTileSave(tile);
        lastUpdateTimes.put("tile:" + tileX + "_" + tileZ, System.currentTimeMillis());
    }

    private void updateChunk(ChunkPos chunkPos) {
        if (!isChunkReadyForMap(chunkPos)) return;

        int tileX = ChunkTile.chunkToTileCoord(chunkPos.x);
        int tileZ = ChunkTile.chunkToTileCoord(chunkPos.z);
        missingTileKeys.remove(tileKey(tileX, tileZ));
        ChunkTile tile = getOrCreateTile(tileX, tileZ);
        int localChunkX = Math.floorMod(chunkPos.x, ChunkTile.TILE_SIZE);
        int localChunkZ = Math.floorMod(chunkPos.z, ChunkTile.TILE_SIZE);

        ChunkImage chunkImage = new ChunkImage(mc.level, chunkPos);
        tile.updateFromChunkImage(chunkImage, localChunkX, localChunkZ);
        chunkImage.close();
        saveTileIfDue(tile);
        lastUpdateTimes.put("chunk:" + chunkPos.x + "_" + chunkPos.z, System.currentTimeMillis());
    }

    private void saveTileIfDue(ChunkTile tile) {
        String key = tile.getTileX() + "_" + tile.getTileZ();
        long now = System.currentTimeMillis();
        Long lastSave = lastSaveTimes.get(key);
        if (lastSave != null && now - lastSave < TILE_SAVE_INTERVAL_MS) {
            return;
        }

        queueTileSave(tile);
        lastSaveTimes.put(key, now);
    }

    private void updateOnlyLoadedChunks(ChunkTile tile) {
        if (mc.level == null || mc.player == null) return;

        int startChunkX = ChunkTile.tileToChunkCoord(tile.getTileX());
        int startChunkZ = ChunkTile.tileToChunkCoord(tile.getTileZ());

        for (int cz = 0; cz < ChunkTile.TILE_SIZE; cz++) {
            for (int cx = 0; cx < ChunkTile.TILE_SIZE; cx++) {
                ChunkPos chunkPos = new ChunkPos(startChunkX + cx, startChunkZ + cz);
                if (isChunkReadyForMap(chunkPos)) {
                    ChunkImage chunkImage = new ChunkImage(mc.level, chunkPos);
                    tile.updateFromChunkImage(chunkImage, cx, cz);
                    chunkImage.close();
                }
            }
        }
    }

    public void updateVisibleArea(double offsetX, double offsetZ, double scale, int screenWidth, int screenHeight) {
        if (mc.level == null || mc.player == null || cachePath == null) return;

        long now = System.currentTimeMillis();
        if (now - lastVisibleQueueTime >= VISIBLE_QUEUE_INTERVAL_MS) {
            lastVisibleQueueTime = now;
            enqueueVisibleArea(offsetX, offsetZ, scale, screenWidth, screenHeight);
        }
        processPendingChunkUpdates();
    }

    public void updateAroundPlayer(int chunkRadius) {
        if (mc.level == null || mc.player == null) return;
        if (cachePath == null) initialize(mc.level);

        long now = System.currentTimeMillis();
        if (now - lastBackgroundQueueTime >= BACKGROUND_QUEUE_INTERVAL_MS) {
            lastBackgroundQueueTime = now;
            enqueueStaleNearbyChunks(now);
            enqueueAroundPlayer(chunkRadius);
        }
        processPendingChunkUpdates();
    }

    public void markBlockDirty(BlockPos pos) {
        if (pos == null || mc.level == null || mc.player == null) {
            return;
        }
        if (mc.level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (cachePath == null) {
            initialize(mc.level);
        }

        int chunkX = Math.floorDiv(pos.getX(), 16);
        int chunkZ = Math.floorDiv(pos.getZ(), 16);
        enqueueDirtyChunk(new ChunkPos(chunkX, chunkZ));

        int localX = Math.floorMod(pos.getX(), 16);
        int localZ = Math.floorMod(pos.getZ(), 16);
        if (localX == 0) enqueueDirtyChunk(new ChunkPos(chunkX - 1, chunkZ));
        if (localX == 15) enqueueDirtyChunk(new ChunkPos(chunkX + 1, chunkZ));
        if (localZ == 0) enqueueDirtyChunk(new ChunkPos(chunkX, chunkZ - 1));
        if (localZ == 15) enqueueDirtyChunk(new ChunkPos(chunkX, chunkZ + 1));
        if (localX == 0 && localZ == 0) enqueueDirtyChunk(new ChunkPos(chunkX - 1, chunkZ - 1));
        if (localX == 0 && localZ == 15) enqueueDirtyChunk(new ChunkPos(chunkX - 1, chunkZ + 1));
        if (localX == 15 && localZ == 0) enqueueDirtyChunk(new ChunkPos(chunkX + 1, chunkZ - 1));
        if (localX == 15 && localZ == 15) enqueueDirtyChunk(new ChunkPos(chunkX + 1, chunkZ + 1));
    }

    private void enqueueVisibleArea(double offsetX, double offsetZ, double scale, int screenWidth, int screenHeight) {
        double minWorldX = (-offsetX) / scale;
        double maxWorldX = (screenWidth - offsetX) / scale;
        double minWorldZ = (-offsetZ) / scale;
        double maxWorldZ = (screenHeight - offsetZ) / scale;

        int minChunkX = (int) Math.floor(minWorldX / 16.0) - 1;
        int maxChunkX = (int) Math.ceil(maxWorldX / 16.0) + 1;
        int minChunkZ = (int) Math.floor(minWorldZ / 16.0) - 1;
        int maxChunkZ = (int) Math.ceil(maxWorldZ / 16.0) + 1;

        double centerChunkX = ((screenWidth / 2.0 - offsetX) / scale) / 16.0;
        double centerChunkZ = ((screenHeight / 2.0 - offsetZ) / scale) / 16.0;

        long visibleChunkCount = (long) (maxChunkX - minChunkX + 1) * (long) (maxChunkZ - minChunkZ + 1);
        if (visibleChunkCount > MAX_VISIBLE_CHUNK_SCAN) {
            enqueueAround((int) Math.floor(centerChunkX), (int) Math.floor(centerChunkZ), 8);
            return;
        }

        List<ChunkPos> candidates = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                if (!isChunkReadyForMap(chunkPos)) continue;
                if (isChunkExplored(chunkPos)) continue;
                candidates.add(chunkPos);
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> {
            double dx = pos.x + 0.5 - centerChunkX;
            double dz = pos.z + 0.5 - centerChunkZ;
            return dx * dx + dz * dz;
        }));

        for (ChunkPos candidate : candidates) {
            long key = candidate.toLong();
            if (pendingChunkKeys.add(key)) {
                pendingChunkUpdates.addLast(candidate);
            }
        }
    }

    private void enqueueAroundPlayer(int chunkRadius) {
        ChunkPos playerChunk = mc.player.chunkPosition();
        enqueueAround(playerChunk.x, playerChunk.z, chunkRadius);
    }

    private void enqueueStaleNearbyChunks(long now) {
        ChunkPos playerChunk = mc.player.chunkPosition();
        for (int dz = -NEARBY_REFRESH_RADIUS; dz <= NEARBY_REFRESH_RADIUS; dz++) {
            for (int dx = -NEARBY_REFRESH_RADIUS; dx <= NEARBY_REFRESH_RADIUS; dx++) {
                ChunkPos chunkPos = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                if (!isChunkReadyForMap(chunkPos)) continue;
                if (!isChunkExplored(chunkPos)) continue;
                if (shouldRefreshChunk(chunkPos, now, NEARBY_CHUNK_REFRESH_INTERVAL_MS)) {
                    enqueueChunkRefresh(chunkPos);
                }
            }
        }
    }

    private void enqueueAround(int centerChunkX, int centerChunkZ, int chunkRadius) {
        List<ChunkPos> candidates = new ArrayList<>();

        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                ChunkPos chunkPos = new ChunkPos(centerChunkX + dx, centerChunkZ + dz);
                if (!isChunkReadyForMap(chunkPos)) continue;
                if (isChunkExplored(chunkPos)) continue;
                candidates.add(chunkPos);
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> {
            double dx = pos.x + 0.5 - centerChunkX;
            double dz = pos.z + 0.5 - centerChunkZ;
            return dx * dx + dz * dz;
        }));

        for (ChunkPos candidate : candidates) {
            long key = candidate.toLong();
            if (pendingChunkKeys.add(key)) {
                pendingChunkUpdates.addLast(candidate);
            }
        }
    }

    private void processPendingChunkUpdates() {
        long now = System.currentTimeMillis();
        if (now - lastChunkProcessTime < CHUNK_PROCESS_INTERVAL_MS) return;
        lastChunkProcessTime = now;

        int updates = 0;
        int attempts = 0;
        long startNanos = System.nanoTime();
        while (updates < CHUNK_UPDATES_PER_PASS && attempts < CHUNK_ATTEMPTS_PER_PASS && !dirtyChunkUpdates.isEmpty()) {
            attempts++;
            ChunkPos chunkPos = dirtyChunkUpdates.removeFirst();
            dirtyChunkKeys.remove(chunkPos.toLong());
            if (isChunkReadyForMap(chunkPos)) {
                updateChunk(chunkPos);
                updates++;
                if (System.nanoTime() - startNanos >= CHUNK_UPDATE_TIME_BUDGET_NS) {
                    break;
                }
            }
        }

        while (updates < CHUNK_UPDATES_PER_PASS && attempts < CHUNK_ATTEMPTS_PER_PASS && !pendingChunkUpdates.isEmpty()) {
            attempts++;
            ChunkPos chunkPos = pendingChunkUpdates.removeFirst();
            pendingChunkKeys.remove(chunkPos.toLong());
            if (isChunkReadyForMap(chunkPos)) {
                updateChunk(chunkPos);
                updates++;
                if (System.nanoTime() - startNanos >= CHUNK_UPDATE_TIME_BUDGET_NS) {
                    break;
                }
            }
        }
    }

    private boolean isChunkLoaded(ChunkPos chunkPos) {
        if (mc.level == null || mc.player == null) return false;
        try {
            return mc.level.getChunkSource().getChunk(chunkPos.x, chunkPos.z, false) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isChunkReadyForMap(ChunkPos chunkPos) {
        if (!isChunkLoaded(chunkPos)) {
            return false;
        }

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!isChunkLoaded(new ChunkPos(chunkPos.x + dx, chunkPos.z + dz))) {
                    return false;
                }
            }
        }

        return true;
    }

    public ChunkTile getOrCreateTile(int tileX, int tileZ) {
        String key = tileKey(tileX, tileZ);
        missingTileKeys.remove(key);
        ChunkTile tile = loadedTiles.get(key);
        if (tile == null) {
            tile = new ChunkTile(tileX, tileZ);
            tile.loadOrCreate(getTileFile(tileX, tileZ));
            loadedTiles.put(key, tile);
        }
        tile.markAccessed();
        return tile;
    }

    public ChunkTile getTileIfPresent(int tileX, int tileZ) {
        String key = tileKey(tileX, tileZ);
        ChunkTile tile = loadedTiles.get(key);
        if (tile != null) {
            tile.markAccessed();
            return tile;
        }

        if (missingTileKeys.contains(key) || cachePath == null) return null;
        File tileFile = getTileFile(tileX, tileZ);
        if (!tileFile.exists() || tileFile.length() <= 0L) {
            missingTileKeys.add(key);
            return null;
        }
        return getOrCreateTile(tileX, tileZ);
    }

    private File getTileFile(int tileX, int tileZ) {
        return cachePath.tileFile(tileX, tileZ);
    }

    public void close() {
        saveAndReleaseTiles();
        resetRuntimeState();
        this.cachePath = null;
    }

    public void flush() {
        saveTiles();
    }

    private void saveAndReleaseTiles() {
        saveTiles();
        tileSaver.flush();
        for (ChunkTile tile : loadedTiles.values()) {
            tile.close();
        }
    }

    private void saveTiles() {
        if (this.cachePath == null) return;
        for (ChunkTile tile : loadedTiles.values()) {
            queueTileSave(tile);
        }
    }

    private void queueTileSave(ChunkTile tile) {
        if (tile == null) return;
        tileSaver.saveLater(getTileFile(tile.getTileX(), tile.getTileZ()), tile.createSaveSnapshot());
    }

    private void resetRuntimeState() {
        loadedTiles.clear();
        missingTileKeys.clear();
        lastUpdateTimes.clear();
        lastSaveTimes.clear();
        pendingChunkUpdates.clear();
        pendingChunkKeys.clear();
        dirtyChunkUpdates.clear();
        dirtyChunkKeys.clear();
        currentTileX = Integer.MAX_VALUE;
        currentTileZ = Integer.MAX_VALUE;
        lastVisibleQueueTime = 0L;
        lastBackgroundQueueTime = 0L;
        lastChunkProcessTime = 0L;
    }

    public Map<String, ChunkTile> getLoadedTiles() {
        return loadedTiles;
    }

    public boolean isChunkExplored(ChunkPos chunk) {
        if (cachePath == null) return false;
        int tileX = ChunkTile.chunkToTileCoord(chunk.x);
        int tileZ = ChunkTile.chunkToTileCoord(chunk.z);
        ChunkTile tile = getOrCreateTile(tileX, tileZ);
        if (tile == null || tile.getImage() == null) return false;

        int localX = Math.floorMod(chunk.x, ChunkTile.TILE_SIZE) * ChunkTile.PIXELS_PER_CHUNK + ChunkTile.PIXELS_PER_CHUNK / 2;
        int localZ = Math.floorMod(chunk.z, ChunkTile.TILE_SIZE) * ChunkTile.PIXELS_PER_CHUNK + ChunkTile.PIXELS_PER_CHUNK / 2;

        return (tile.getImage().getPixelRGBA(localX, localZ) >> 24 & 0xFF) > 0;
    }

    private static String tileKey(int tileX, int tileZ) {
        return tileX + "_" + tileZ;
    }

    private void enqueueDirtyChunk(ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        pendingChunkKeys.remove(key);
        if (dirtyChunkKeys.add(key)) {
            dirtyChunkUpdates.addLast(chunkPos);
        }
    }

    private void enqueueChunkRefresh(ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        if (dirtyChunkKeys.contains(key)) {
            return;
        }
        if (pendingChunkKeys.add(key)) {
            pendingChunkUpdates.addLast(chunkPos);
        }
    }

    private boolean shouldRefreshChunk(ChunkPos chunkPos, long now, long refreshIntervalMs) {
        Long lastUpdate = lastUpdateTimes.get(chunkKey(chunkPos));
        return lastUpdate == null || now - lastUpdate >= refreshIntervalMs;
    }

    private static String chunkKey(ChunkPos chunkPos) {
        return "chunk:" + chunkPos.x + "_" + chunkPos.z;
    }
}

