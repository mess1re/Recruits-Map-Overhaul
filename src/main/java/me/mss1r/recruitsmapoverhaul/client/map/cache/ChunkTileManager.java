package me.mss1r.recruitsmapoverhaul.client.map.cache;

import com.mojang.blaze3d.platform.NativeImage;
import me.mss1r.recruitsmapoverhaul.client.map.sampling.ChunkImage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
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
    private static final int MAX_LOADED_TILES = Integer.getInteger("recruitsmapoverhaul.maxLoadedTiles", 768);
    private static final int MAX_MISSING_TILE_KEYS = Integer.getInteger("recruitsmapoverhaul.maxMissingTiles", 4096);
    private static final int MAX_LAST_UPDATE_TIMES = Integer.getInteger("recruitsmapoverhaul.maxLastUpdateTimes", 8192);
    private static final int MAX_LAST_SAVE_TIMES = Integer.getInteger("recruitsmapoverhaul.maxLastSaveTimes", 2048);
    private static final int TILE_LOAD_RESULTS_PER_FRAME = Integer.getInteger("recruitsmapoverhaul.tileLoadResultsPerFrame", 12);
    private static final int NEARBY_REFRESH_RADIUS = 1;
    private static ChunkTileManager instance;
    private final AsyncTileSaver tileSaver = new AsyncTileSaver();
    private final AsyncTileLoader tileLoader = new AsyncTileLoader();
    private final AsyncOverviewTileBuilder overviewBuilder = new AsyncOverviewTileBuilder();
    private final BoundedTileCache<String, ChunkTile> loadedTiles =
            new BoundedTileCache<>(MAX_LOADED_TILES, (key, tile) -> {
                queueTileSave(tile);
                tile.close();
            });
    private final ArrayDeque<ChunkPos> dirtyChunkUpdates = new ArrayDeque<>();
    private final ArrayDeque<ChunkPos> pendingChunkUpdates = new ArrayDeque<>();
    private final Set<Long> dirtyChunkKeys = new HashSet<>();
    private final Set<Long> pendingChunkKeys = new HashSet<>();
    private final Set<String> missingTileKeys = new LinkedHashSet<>();
    private final Minecraft mc = Minecraft.getInstance();
    private WorldMapCachePath cachePath;
    private int currentTileX = Integer.MAX_VALUE;
    private int currentTileZ = Integer.MAX_VALUE;
    private final BoundedTileCache<String, Long> lastUpdateTimes = new BoundedTileCache<>(MAX_LAST_UPDATE_TIMES, null);
    private final BoundedTileCache<String, Long> lastSaveTimes = new BoundedTileCache<>(MAX_LAST_SAVE_TIMES, null);
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
        ChunkTile tile = getOrRequestTileForUpdate(tileX, tileZ);
        if (tile == null) return;
        updateOnlyLoadedChunks(tile);
        queueTileSave(tile);
        lastUpdateTimes.put("tile:" + tileX + "_" + tileZ, System.currentTimeMillis());
    }

    private boolean updateChunk(ChunkPos chunkPos) {
        if (!isChunkReadyForMap(chunkPos)) return false;

        int tileX = ChunkTile.chunkToTileCoord(chunkPos.x);
        int tileZ = ChunkTile.chunkToTileCoord(chunkPos.z);
        missingTileKeys.remove(tileKey(0, tileX, tileZ));
        ChunkTile tile = getOrRequestTileForUpdate(tileX, tileZ);
        if (tile == null) {
            return false;
        }

        int localChunkX = Math.floorMod(chunkPos.x, ChunkTile.TILE_SIZE);
        int localChunkZ = Math.floorMod(chunkPos.z, ChunkTile.TILE_SIZE);

        ChunkImage chunkImage = new ChunkImage(mc.level, chunkPos);
        tile.updateFromChunkImage(chunkImage, localChunkX, localChunkZ);
        chunkImage.close();
        invalidateOverviewAncestors(tileX, tileZ);
        saveTileIfDue(tile);
        lastUpdateTimes.put("chunk:" + chunkPos.x + "_" + chunkPos.z, System.currentTimeMillis());
        return true;
    }

    private void saveTileIfDue(ChunkTile tile) {
        String key = tileKey(tile.getLevel(), tile.getTileX(), tile.getTileZ());
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

        drainTileLoadResults(TILE_LOAD_RESULTS_PER_FRAME);

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

        drainTileLoadResults(TILE_LOAD_RESULTS_PER_FRAME);

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
                if (updateChunk(chunkPos)) {
                    updates++;
                } else {
                    deferChunkUpdate(chunkPos);
                    break;
                }
                if (updates > 0 && System.nanoTime() - startNanos >= CHUNK_UPDATE_TIME_BUDGET_NS) {
                    break;
                }
            }
        }

        while (updates < CHUNK_UPDATES_PER_PASS && attempts < CHUNK_ATTEMPTS_PER_PASS && !pendingChunkUpdates.isEmpty()) {
            attempts++;
            ChunkPos chunkPos = pendingChunkUpdates.removeFirst();
            pendingChunkKeys.remove(chunkPos.toLong());
            if (isChunkReadyForMap(chunkPos)) {
                if (updateChunk(chunkPos)) {
                    updates++;
                } else {
                    deferChunkUpdate(chunkPos);
                    break;
                }
                if (updates > 0 && System.nanoTime() - startNanos >= CHUNK_UPDATE_TIME_BUDGET_NS) {
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
        return getOrRequestTileForUpdate(tileX, tileZ);
    }

    private ChunkTile getOrRequestTileForUpdate(int tileX, int tileZ) {
        String key = tileKey(0, tileX, tileZ);
        missingTileKeys.remove(key);
        ChunkTile tile = loadedTiles.get(key);
        if (tile != null) {
            tile.markAccessed();
            return tile;
        }

        File tileFile = getTileFile(0, tileX, tileZ);
        if (tileFile.exists() && tileFile.length() > 0L) {
            tileLoader.request(cachePath, tileFile, 0, tileX, tileZ);
            return null;
        }

        tile = new ChunkTile(tileX, tileZ);
        tile.loadOrCreate(tileFile);
        loadedTiles.put(key, tile);
        tile.markAccessed();
        return tile;
    }

    public ChunkTile getTileIfPresent(int tileX, int tileZ) {
        return getTileIfPresent(0, tileX, tileZ);
    }

    public ChunkTile getLoadedTile(int level, int tileX, int tileZ) {
        level = Math.max(0, Math.min(ChunkTile.MAX_OVERVIEW_LEVEL, level));
        ChunkTile tile = loadedTiles.get(tileKey(level, tileX, tileZ));
        if (tile != null) {
            tile.markAccessed();
        }
        return tile;
    }

    public boolean hasAnySourceTile(int level, int tileX, int tileZ) {
        if (cachePath == null) {
            return false;
        }
        return cachePath.hasAnyBaseTileInSubtree(level, tileX, tileZ);
    }

    public ChunkTile getTileIfPresent(int level, int tileX, int tileZ) {
        level = Math.max(0, Math.min(ChunkTile.MAX_OVERVIEW_LEVEL, level));
        String key = tileKey(level, tileX, tileZ);
        ChunkTile tile = loadedTiles.get(key);
        if (tile != null) {
            tile.markAccessed();
            return tile;
        }

        if (missingTileKeys.contains(key) || cachePath == null) return null;
        File tileFile = getTileFile(level, tileX, tileZ);
        if (tileFile.exists() && tileFile.length() > 0L) {
            tileLoader.request(cachePath, tileFile, level, tileX, tileZ);
            return null;
        }

        if (level <= 0) {
            rememberMissingTileKey(key);
            return null;
        }

        overviewBuilder.request(cachePath, level, tileX, tileZ);
        return null;
    }

    public void prepareRenderFrame() {
        drainTileLoadResults(TILE_LOAD_RESULTS_PER_FRAME);
    }

    private void drainTileLoadResults(int maxResults) {
        if (cachePath == null) {
            return;
        }

        for (AsyncTileLoader.Result result : tileLoader.drainCompleted(maxResults)) {
            if (result.cachePath() != cachePath) {
                result.close();
                continue;
            }

            int level = result.level();
            int tileX = result.tileX();
            int tileZ = result.tileZ();
            String key = tileKey(level, tileX, tileZ);
            NativeImage image = result.takeImage();
            if (image == null) {
                if (level <= 0) {
                    rememberMissingTileKey(key);
                }
                continue;
            }

            ChunkTile existing = loadedTiles.get(key);
            if (existing != null) {
                try {
                    image.close();
                } catch (Exception ignored) {
                }
                existing.markAccessed();
                continue;
            }

            ChunkTile tile = new ChunkTile(tileX, tileZ, level);
            tile.replaceImage(image, false);
            tile.markAccessed();
            missingTileKeys.remove(key);
            loadedTiles.put(key, tile);
        }
    }

    private File getTileFile(int level, int tileX, int tileZ) {
        return cachePath.tileFile(level, tileX, tileZ);
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
        loadedTiles.clearWithEviction();
        tileSaver.flush();
    }

    private void saveTiles() {
        if (this.cachePath == null) return;
        for (ChunkTile tile : loadedTiles.valuesSnapshot()) {
            queueTileSave(tile);
        }
    }

    private void queueTileSave(ChunkTile tile) {
        if (tile == null || cachePath == null) return;
        if (tile.getLevel() <= 0) {
            cachePath.noteBaseTileChanged(tile.getTileX(), tile.getTileZ());
        }
        tileSaver.saveLater(getTileFile(tile.getLevel(), tile.getTileX(), tile.getTileZ()), tile.createSaveSnapshot());
    }

    private void resetRuntimeState() {
        loadedTiles.clearWithoutEviction();
        missingTileKeys.clear();
        lastUpdateTimes.clearWithoutEviction();
        lastSaveTimes.clearWithoutEviction();
        pendingChunkUpdates.clear();
        pendingChunkKeys.clear();
        dirtyChunkUpdates.clear();
        dirtyChunkKeys.clear();
        tileLoader.clear();
        overviewBuilder.clear();
        currentTileX = Integer.MAX_VALUE;
        currentTileZ = Integer.MAX_VALUE;
        lastVisibleQueueTime = 0L;
        lastBackgroundQueueTime = 0L;
        lastChunkProcessTime = 0L;
    }

    public Map<String, ChunkTile> getLoadedTiles() {
        return loadedTiles.snapshot();
    }

    public boolean isChunkExplored(ChunkPos chunk) {
        if (cachePath == null) return false;
        int tileX = ChunkTile.chunkToTileCoord(chunk.x);
        int tileZ = ChunkTile.chunkToTileCoord(chunk.z);
        ChunkTile tile = getTileIfPresent(tileX, tileZ);
        if (tile == null || tile.getImage() == null) return false;

        int localX = Math.floorMod(chunk.x, ChunkTile.TILE_SIZE) * ChunkTile.PIXELS_PER_CHUNK + ChunkTile.PIXELS_PER_CHUNK / 2;
        int localZ = Math.floorMod(chunk.z, ChunkTile.TILE_SIZE) * ChunkTile.PIXELS_PER_CHUNK + ChunkTile.PIXELS_PER_CHUNK / 2;

        return (tile.getImage().getPixelRGBA(localX, localZ) >> 24 & 0xFF) > 0;
    }

    private void invalidateOverviewAncestors(int baseTileX, int baseTileZ) {
        for (int level = 1; level <= ChunkTile.MAX_OVERVIEW_LEVEL; level++) {
            int divisor = 1 << level;
            int tileX = Math.floorDiv(baseTileX, divisor);
            int tileZ = Math.floorDiv(baseTileZ, divisor);
            String key = tileKey(level, tileX, tileZ);
            missingTileKeys.remove(key);

            ChunkTile staleTile = loadedTiles.removeWithoutEviction(key);
            if (staleTile != null) {
                staleTile.close();
            }

            File file = getTileFile(level, tileX, tileZ);
            overviewBuilder.discard(file);
            tileSaver.discard(file);
            if (file.exists() && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    private static String tileKey(int level, int tileX, int tileZ) {
        return "l" + level + ":" + tileX + "_" + tileZ;
    }

    private void rememberMissingTileKey(String key) {
        missingTileKeys.add(key);
        while (missingTileKeys.size() > MAX_MISSING_TILE_KEYS) {
            Iterator<String> iterator = missingTileKeys.iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
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

    private void deferChunkUpdate(ChunkPos chunkPos) {
        long key = chunkPos.toLong();
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

