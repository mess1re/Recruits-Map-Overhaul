package me.mss1r.recruitsmapoverhaul.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WorldMapOverlayRegistry {
    private static final List<WorldMapOverlay> OVERLAYS = new CopyOnWriteArrayList<>();

    private WorldMapOverlayRegistry() {
    }

    public static void register(WorldMapOverlay overlay) {
        if (overlay != null && !OVERLAYS.contains(overlay)) {
            OVERLAYS.add(overlay);
        }
    }

    public static List<WorldMapOverlay> overlays() {
        return List.copyOf(OVERLAYS);
    }
}
