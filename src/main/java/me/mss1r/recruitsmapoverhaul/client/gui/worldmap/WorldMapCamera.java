package me.mss1r.recruitsmapoverhaul.client.gui.worldmap;

import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

public final class WorldMapCamera {
    public static final double MIN_SCALE = 0.0625;
    public static final double MAX_SCALE = 50.0;
    public static final double DEFAULT_SCALE = 3.0;

    private static final double ZOOM_STEP_BASE = 1.2;
    private static final long ZOOM_ANIMATION_NANOS = 100_000_000L;

    private static boolean savedViewValid = false;
    private static double savedScale = DEFAULT_SCALE;
    private static double savedTargetScale = DEFAULT_SCALE;
    private static double savedCenterWorldX = 0.0;
    private static double savedCenterWorldZ = 0.0;
    private static double savedTargetCenterWorldX = 0.0;
    private static double savedTargetCenterWorldZ = 0.0;

    private final WorldMapScreen screen;
    private final WorldMapScreenAccess access;

    private double targetOffsetX = 0.0;
    private double targetOffsetZ = 0.0;
    private double targetScale = DEFAULT_SCALE;
    private long lastAnimationNanos = 0L;
    private boolean zoomAnchored = false;
    private long zoomAnimationStartNanos = 0L;
    private double zoomAnimationFromScale = DEFAULT_SCALE;
    private double zoomAnchorScreenX = 0.0;
    private double zoomAnchorScreenZ = 0.0;
    private double zoomAnchorWorldX = 0.0;
    private double zoomAnchorWorldZ = 0.0;
    private final PanInertia panInertia = new PanInertia();

    public WorldMapCamera(WorldMapScreen screen, WorldMapScreenAccess access) {
        this.screen = screen;
        this.access = access;
    }

    public void init(Player player, boolean resizeReinit) {
        if (resizeReinit && savedViewValid) {
            restoreSavedView();
        } else {
            setScale(savedViewValid ? clampScale(savedTargetScale) : DEFAULT_SCALE);
            targetScale = scale();
            centerOnPlayer(player);
        }

        lastAnimationNanos = 0L;
        panInertia.reset();
    }

    public void centerOnPlayer(Player player) {
        if (player == null) return;
        setOffsetX(screen.width / 2.0 - player.getX() * scale());
        setOffsetZ(screen.height / 2.0 - player.getZ() * scale());
        targetOffsetX = offsetX();
        targetOffsetZ = offsetZ();
        targetScale = scale();
        zoomAnchored = false;
        panInertia.reset();
        rememberCurrentView();
    }

    public void centerOnClaim(ChunkPos center) {
        if (center == null) return;
        setOffsetX(-(center.x * 16 * scale()) + screen.width / 2.0);
        setOffsetZ(-(center.z * 16 * scale()) + screen.height / 2.0);
        targetOffsetX = offsetX();
        targetOffsetZ = offsetZ();
        targetScale = scale();
        zoomAnchored = false;
        panInertia.reset();
        rememberCurrentView();
    }

    public void resetZoom(Player player) {
        targetScale = DEFAULT_SCALE;
        setScale(DEFAULT_SCALE);
        zoomAnchored = false;
        centerOnPlayer(player);
    }

    public void animate() {
        long now = System.nanoTime();
        if (lastAnimationNanos == 0L) {
            lastAnimationNanos = now;
            return;
        }

        double dt = Math.min(0.05, (now - lastAnimationNanos) / 1_000_000_000.0);
        lastAnimationNanos = now;

        if (access.recruitsmapoverhaul$isDragging()) return;

        if (zoomAnchored) {
            double progress = Math.min(1.0, (now - zoomAnimationStartNanos) / (double) ZOOM_ANIMATION_NANOS);
            double eased = 0.5 - Math.cos(progress * Math.PI) * 0.5;
            double newScale = smoothValue(zoomAnimationFromScale, targetScale, eased, 0.0005);
            setScale(newScale);
            setOffsetX(zoomAnchorScreenX - zoomAnchorWorldX * scale());
            setOffsetZ(zoomAnchorScreenZ - zoomAnchorWorldZ * scale());
            if (progress >= 1.0 || scale() == targetScale) {
                zoomAnchored = false;
                setScale(targetScale);
                setOffsetX(targetOffsetX);
                setOffsetZ(targetOffsetZ);
            }
            return;
        }

        if (panInertia.isActive()) {
            PanInertia.Offset offset = panInertia.currentOffset();
            setOffsetX(offset.x());
            setOffsetZ(offset.z());
            targetOffsetX = panInertia.targetOffsetX();
            targetOffsetZ = panInertia.targetOffsetZ();
            targetScale = scale();
            if (panInertia.isAtTarget(offset)) {
                panInertia.reset();
            }
            return;
        }

        double alpha = 1.0 - Math.pow(0.001, dt / 0.18);
        setScale(smoothValue(scale(), targetScale, alpha, 0.0005));
        setOffsetX(smoothValue(offsetX(), targetOffsetX, alpha, 0.02));
        setOffsetZ(smoothValue(offsetZ(), targetOffsetZ, alpha, 0.02));
    }

    public void panByScreenDelta(double deltaX, double deltaZ) {
        panInertia.reset();
        setOffsetX(offsetX() + deltaX);
        setOffsetZ(offsetZ() + deltaZ);
        targetOffsetX = offsetX();
        targetOffsetZ = offsetZ();
        targetScale = scale();
        zoomAnchored = false;
        rememberCurrentView();
    }

    public void beginPanDrag(double mouseX, double mouseY) {
        panInertia.begin(mouseX, mouseY);
    }

    public void dragByScreenDelta(double mouseX, double mouseY, double deltaX, double deltaZ) {
        setOffsetX(offsetX() + deltaX);
        setOffsetZ(offsetZ() + deltaZ);
        targetOffsetX = offsetX();
        targetOffsetZ = offsetZ();
        targetScale = scale();
        zoomAnchored = false;
        panInertia.recordDrag(mouseX, mouseY, deltaX, deltaZ);

        rememberCurrentView();
    }

    public void finishPanDrag(double mouseX, double mouseY) {
        if (!panInertia.finish(mouseX, mouseY, scale(), MIN_SCALE, offsetX(), offsetZ())) {
            return;
        }

        targetOffsetX = panInertia.targetOffsetX();
        targetOffsetZ = panInertia.targetOffsetZ();
        targetScale = scale();
        rememberCurrentView();
    }

    public void zoomAt(double mouseX, double mouseY, double scrollY) {
        panInertia.reset();
        double zoomFactor = Math.pow(ZOOM_STEP_BASE, scrollY);
        double newScale = clampScale(targetScale * zoomFactor);

        double mouseWorldX = (mouseX - offsetX()) / scale();
        double mouseWorldZ = (mouseY - offsetZ()) / scale();
        targetScale = newScale;
        targetOffsetX = mouseX - mouseWorldX * targetScale;
        targetOffsetZ = mouseY - mouseWorldZ * targetScale;
        zoomAnchorScreenX = mouseX;
        zoomAnchorScreenZ = mouseY;
        zoomAnchorWorldX = mouseWorldX;
        zoomAnchorWorldZ = mouseWorldZ;
        zoomAnimationFromScale = scale();
        zoomAnimationStartNanos = System.nanoTime();
        zoomAnchored = true;
        rememberCurrentView();
    }

    public void rememberCurrentView() {
        if (screen.width <= 0 || screen.height <= 0 || scale() <= 0.0 || targetScale <= 0.0) return;

        savedViewValid = true;
        savedScale = clampScale(scale());
        savedTargetScale = clampScale(targetScale);
        savedCenterWorldX = (screen.width / 2.0 - offsetX()) / scale();
        savedCenterWorldZ = (screen.height / 2.0 - offsetZ()) / scale();
        savedTargetCenterWorldX = (screen.width / 2.0 - targetOffsetX) / targetScale;
        savedTargetCenterWorldZ = (screen.height / 2.0 - targetOffsetZ) / targetScale;
    }

    public double offsetX() {
        return access.recruitsmapoverhaul$getOffsetX();
    }

    public double offsetZ() {
        return access.recruitsmapoverhaul$getOffsetZ();
    }

    public double scale() {
        return access.recruitsmapoverhaul$getScale();
    }

    private void restoreSavedView() {
        setScale(clampScale(savedScale));
        targetScale = clampScale(savedTargetScale);
        setOffsetX(screen.width / 2.0 - savedCenterWorldX * scale());
        setOffsetZ(screen.height / 2.0 - savedCenterWorldZ * scale());
        targetOffsetX = screen.width / 2.0 - savedTargetCenterWorldX * targetScale;
        targetOffsetZ = screen.height / 2.0 - savedTargetCenterWorldZ * targetScale;
        zoomAnchored = false;
    }

    private void setOffsetX(double value) {
        access.recruitsmapoverhaul$setOffsetX(value);
    }

    private void setOffsetZ(double value) {
        access.recruitsmapoverhaul$setOffsetZ(value);
    }

    private void setScale(double value) {
        access.recruitsmapoverhaul$setScale(clampScale(value));
    }

    private static double smoothValue(double current, double target, double alpha, double snapDistance) {
        double delta = target - current;
        if (Math.abs(delta) <= snapDistance) return target;
        return current + delta * alpha;
    }

    public static double clampScale(double value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }
}
