package me.mss1r.recruitsmapoverhaul.client.map.cache;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AsyncOverviewTileBuilder {
    private static final int MAX_PENDING_BUILDS = Integer.getInteger("recruitsmapoverhaul.maxPendingOverviewBuilds", 192);
    private static final long FAILED_BUILD_RETRY_MS =
            Long.getLong("recruitsmapoverhaul.failedOverviewRetryMs", 2500L);

    private final Object lock = new Object();
    private final ArrayDeque<Request> pendingBuilds = new ArrayDeque<>();
    private final Set<String> pendingKeys = new HashSet<>();
    private final Map<String, Integer> generations = new HashMap<>();
    private final Map<String, Long> failedUntil = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RecruitsMapOverhaul Overview Builder");
        thread.setDaemon(true);
        return thread;
    });

    private boolean workerRunning = false;

    void request(WorldMapCachePath cachePath, int level, int tileX, int tileZ) {
        if (cachePath == null || level <= 0) {
            return;
        }

        File file = cachePath.tileFile(level, tileX, tileZ);
        if (file.exists() && file.length() > 0L) {
            return;
        }

        String key = file.getAbsolutePath();
        synchronized (lock) {
            Long retryAt = failedUntil.get(key);
            if (retryAt != null && retryAt > System.currentTimeMillis()) {
                return;
            }

            if (pendingKeys.contains(key) || pendingBuilds.size() >= MAX_PENDING_BUILDS) {
                return;
            }

            pendingKeys.add(key);
            pendingBuilds.addLast(new Request(cachePath, level, tileX, tileZ, file, key,
                    generations.getOrDefault(key, 0)));
            if (!workerRunning) {
                workerRunning = true;
                executor.execute(this::drainQueue);
            }
        }
    }

    void discard(File file) {
        if (file == null) {
            return;
        }

        String key = file.getAbsolutePath();
        synchronized (lock) {
            pendingKeys.remove(key);
            failedUntil.remove(key);
            generations.merge(key, 1, Integer::sum);
            for (Iterator<Request> iterator = pendingBuilds.iterator(); iterator.hasNext(); ) {
                if (iterator.next().key().equals(key)) {
                    iterator.remove();
                }
            }
        }
    }

    void clear() {
        synchronized (lock) {
            pendingBuilds.clear();
            pendingKeys.clear();
            generations.clear();
            failedUntil.clear();
        }
    }

    private void drainQueue() {
        while (true) {
            Request request;
            synchronized (lock) {
                request = pendingBuilds.pollFirst();
                if (request == null) {
                    workerRunning = false;
                    return;
                }
            }

            try {
                if (!isFresh(request)) {
                    continue;
                }

                NativeImage image = request.build();
                if (image == null) {
                    rememberFailedBuild(request);
                    continue;
                }

                try {
                    if (isFresh(request)) {
                        OverviewTileBuilder.write(request.file(), image);
                        clearFailedBuild(request);
                    }
                } finally {
                    try {
                        image.close();
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                synchronized (lock) {
                    pendingKeys.remove(request.key());
                }
            }
        }
    }

    private void rememberFailedBuild(Request request) {
        synchronized (lock) {
            if (isFreshLocked(request)) {
                failedUntil.put(request.key(), System.currentTimeMillis() + FAILED_BUILD_RETRY_MS);
            }
        }
    }

    private void clearFailedBuild(Request request) {
        synchronized (lock) {
            failedUntil.remove(request.key());
        }
    }

    private boolean isFresh(Request request) {
        synchronized (lock) {
            return isFreshLocked(request);
        }
    }

    private boolean isFreshLocked(Request request) {
        return generations.getOrDefault(request.key(), 0) == request.generation();
    }

    private record Request(WorldMapCachePath cachePath, int level, int tileX, int tileZ, File file, String key,
                           int generation) {
        NativeImage build() {
            return OverviewTileBuilder.build(cachePath, level, tileX, tileZ);
        }
    }
}
