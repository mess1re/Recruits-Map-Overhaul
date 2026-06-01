package me.mss1r.recruitsmapoverhaul.client.map.cache;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AsyncTileSaver {
    private static final int MAX_PENDING_SAVES = Integer.getInteger("recruitsmapoverhaul.maxPendingTileSaves", 256);

    private final Object lock = new Object();
    private final Map<String, SaveRequest> pendingSaves = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RecruitsMapOverhaul Tile Saver");
        thread.setDaemon(true);
        return thread;
    });

    private boolean workerRunning = false;

    void saveLater(File file, NativeImage snapshot) {
        if (file == null || snapshot == null) {
            return;
        }

        SaveRequest replaced;
        List<SaveRequest> discarded = new ArrayList<>();
        synchronized (lock) {
            replaced = pendingSaves.put(file.getAbsolutePath(), new SaveRequest(file, snapshot));
            trimPendingSaves(discarded);
            if (!workerRunning) {
                workerRunning = true;
                executor.execute(this::drainQueue);
            }
        }

        if (replaced != null) {
            replaced.close();
        }
        discarded.forEach(SaveRequest::close);
    }

    void discard(File file) {
        if (file == null) {
            return;
        }

        SaveRequest discarded;
        synchronized (lock) {
            discarded = pendingSaves.remove(file.getAbsolutePath());
        }

        if (discarded != null) {
            discarded.close();
        }
    }

    void flush() {
        synchronized (lock) {
            while (workerRunning || !pendingSaves.isEmpty()) {
                try {
                    lock.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void drainQueue() {
        while (true) {
            SaveRequest request;
            synchronized (lock) {
                if (pendingSaves.isEmpty()) {
                    workerRunning = false;
                    lock.notifyAll();
                    return;
                }

                Iterator<SaveRequest> iterator = pendingSaves.values().iterator();
                request = iterator.next();
                iterator.remove();
            }

            request.writeAndClose();
        }
    }

    private void trimPendingSaves(List<SaveRequest> discarded) {
        Iterator<SaveRequest> iterator = pendingSaves.values().iterator();
        while (pendingSaves.size() > MAX_PENDING_SAVES && iterator.hasNext()) {
            discarded.add(iterator.next());
            iterator.remove();
        }
    }

    private record SaveRequest(File file, NativeImage image) {
        void writeAndClose() {
            try {
                File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                image.writeToFile(file);
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }

        void close() {
            try {
                image.close();
            } catch (Exception ignored) {
            }
        }
    }
}
