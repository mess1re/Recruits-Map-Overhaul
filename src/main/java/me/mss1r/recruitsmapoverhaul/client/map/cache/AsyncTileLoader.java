package me.mss1r.recruitsmapoverhaul.client.map.cache;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AsyncTileLoader {
    private static final int MAX_PENDING_LOADS = Integer.getInteger("recruitsmapoverhaul.maxPendingTileLoads", 512);
    private static final int MAX_COMPLETED_LOADS = Integer.getInteger("recruitsmapoverhaul.maxCompletedTileLoads", 128);

    private final Object lock = new Object();
    private final ArrayDeque<Request> pendingLoads = new ArrayDeque<>();
    private final ArrayDeque<Result> completedLoads = new ArrayDeque<>();
    private final Set<String> pendingKeys = new HashSet<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RecruitsMapOverhaul Tile Loader");
        thread.setDaemon(true);
        return thread;
    });

    private boolean workerRunning = false;

    void request(WorldMapCachePath cachePath, File file, int level, int tileX, int tileZ) {
        if (cachePath == null || file == null || !file.exists() || file.length() <= 0L) {
            return;
        }

        String key = file.getAbsolutePath();
        synchronized (lock) {
            if (pendingKeys.contains(key) || pendingLoads.size() >= MAX_PENDING_LOADS) {
                return;
            }

            pendingKeys.add(key);
            pendingLoads.addLast(new Request(cachePath, file, level, tileX, tileZ, key));
            if (!workerRunning) {
                workerRunning = true;
                executor.execute(this::drainQueue);
            }
        }
    }

    List<Result> drainCompleted(int maxResults) {
        if (maxResults <= 0) {
            return List.of();
        }

        List<Result> results = new ArrayList<>(Math.min(maxResults, MAX_COMPLETED_LOADS));
        synchronized (lock) {
            while (results.size() < maxResults && !completedLoads.isEmpty()) {
                results.add(completedLoads.removeFirst());
            }
        }
        return results;
    }

    void clear() {
        List<Result> completed;
        synchronized (lock) {
            pendingLoads.clear();
            pendingKeys.clear();
            completed = new ArrayList<>(completedLoads);
            completedLoads.clear();
        }
        completed.forEach(Result::close);
    }

    private void drainQueue() {
        while (true) {
            Request request;
            synchronized (lock) {
                request = pendingLoads.pollFirst();
                if (request == null) {
                    workerRunning = false;
                    return;
                }
            }

            NativeImage image = null;
            try {
                image = ChunkTile.readImage(request.file());
            } catch (Throwable ignored) {
                image = null;
            }

            Result result = new Result(
                    request.cachePath(),
                    request.level(),
                    request.tileX(),
                    request.tileZ(),
                    request.key(),
                    image
            );

            Result discarded = null;
            synchronized (lock) {
                pendingKeys.remove(request.key());
                if (completedLoads.size() >= MAX_COMPLETED_LOADS) {
                    discarded = completedLoads.removeFirst();
                }
                completedLoads.addLast(result);
            }

            if (discarded != null) {
                discarded.close();
            }
        }
    }

    private record Request(WorldMapCachePath cachePath, File file, int level, int tileX, int tileZ, String key) {
    }

    static final class Result {
        private final WorldMapCachePath cachePath;
        private final int level;
        private final int tileX;
        private final int tileZ;
        private final String key;
        private NativeImage image;

        private Result(WorldMapCachePath cachePath, int level, int tileX, int tileZ, String key, NativeImage image) {
            this.cachePath = cachePath;
            this.level = level;
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.key = key;
            this.image = image;
        }

        WorldMapCachePath cachePath() {
            return cachePath;
        }

        int level() {
            return level;
        }

        int tileX() {
            return tileX;
        }

        int tileZ() {
            return tileZ;
        }

        String key() {
            return key;
        }

        NativeImage takeImage() {
            NativeImage taken = image;
            image = null;
            return taken;
        }

        void close() {
            try {
                if (image != null) {
                    image.close();
                    image = null;
                }
            } catch (Exception ignored) {
            }
        }
    }
}
