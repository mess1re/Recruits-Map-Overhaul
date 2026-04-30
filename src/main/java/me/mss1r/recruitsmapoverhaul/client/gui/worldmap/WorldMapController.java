package me.mss1r.recruitsmapoverhaul.client.gui.worldmap;

import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.client.gui.worldmap.ClaimInfoMenu;
import com.talhanation.recruits.client.gui.worldmap.WorldMapContextMenu;
import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import com.talhanation.recruits.config.RecruitsClientConfig;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsRoute;
import me.mss1r.recruitsmapoverhaul.client.gui.worldmap.render.WorldMapRenderer;
import me.mss1r.recruitsmapoverhaul.client.map.ChunkTileManager;
import me.mss1r.recruitsmapoverhaul.client.render.ClaimRenderer;
import me.mss1r.recruitsmapoverhaul.client.render.RouteRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.awt.Point;

public final class WorldMapController {
    private final WorldMapScreen screen;
    private final WorldMapScreenAccess access;
    private final ChunkTileManager tileManager = ChunkTileManager.getInstance();
    private final WorldMapCamera camera;
    private final WorldMapRenderer renderer;
    private final WorldMapRouteUi routeUi;

    private WorldMapContextMenu contextMenu;
    private ClaimInfoMenu claimInfoMenu;
    private boolean initializedOnce = false;
    private long lastVisibleTileUpdateNanos = 0L;

    @Nullable
    private RecruitsRoute.Waypoint draggingWaypoint;
    @Nullable
    private BlockPos dragOriginalPos;
    private boolean draggingWaypointActive = false;

    public WorldMapController(WorldMapScreen screen, WorldMapScreenAccess access) {
        this.screen = screen;
        this.access = access;
        this.camera = new WorldMapCamera(screen, access);
        this.renderer = new WorldMapRenderer(screen, access, camera, tileManager);
        this.routeUi = new WorldMapRouteUi(screen, access);
    }

    public void init() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = access.recruitsmapoverhaul$getPlayer();

        if (minecraft.level != null && player != null) {
            tileManager.initialize(minecraft.level);
            camera.init(player, initializedOnce);
            access.recruitsmapoverhaul$setHoverBlock(player.blockPosition().getX(), player.blockPosition().getZ());
            tileManager.warmupAroundPlayer(0);
        }

        initializedOnce = true;
        contextMenu = new WorldMapContextMenu(screen);
        claimInfoMenu = new ClaimInfoMenu(screen);
        claimInfoMenu.init();
        ClientManager.loadRoutes();
        routeUi.init(player);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        camera.animate();
        updateVisibleMapTiles();

        renderer.renderBackground(guiGraphics);
        guiGraphics.enableScissor(0, 0, screen.width, screen.height);

        renderer.renderMapTiles(guiGraphics);
        renderClaims(guiGraphics);
        renderContextPreview(guiGraphics);
        renderer.renderPlayerPosition(guiGraphics);
        renderSelection(guiGraphics);
        renderSelectedRoute(guiGraphics, mouseX, mouseY);

        guiGraphics.flush();
        guiGraphics.disableScissor();

        renderer.renderMapChrome(guiGraphics);
        renderer.renderCoordinatesAndZoom(guiGraphics, resolveSurfaceY(
                access.recruitsmapoverhaul$getHoverBlockX(),
                access.recruitsmapoverhaul$getHoverBlockZ()));
        renderer.renderFPS(guiGraphics);
        routeUi.render(guiGraphics, mouseX, mouseY, partialTicks);
        contextMenu.render(guiGraphics, screen);
        renderClaimInfo(guiGraphics);
        routeUi.renderPopups(guiGraphics, mouseX, mouseY);
        camera.rememberCurrentView();
    }

    public void renderBackground(GuiGraphics guiGraphics) {
        renderer.renderBackground(guiGraphics);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        access.recruitsmapoverhaul$setMouse(mouseX, mouseY);
        if (routeUi.handlePopupMouseClicked(mouseX, mouseY)) return true;
        if (routeUi.handleRouteMouseClicked(mouseX, mouseY, this::closeContextMenu)) return true;
        if (claimInfoMenu.isVisible() && claimInfoMenu.mouseClicked(mouseX, mouseY, button)) return true;
        if (contextMenu.isVisible() && contextMenu.mouseClicked(mouseX, mouseY, button, screen)) return true;

        if (access.recruitsmapoverhaul$getHoveredChunk() != null) {
            access.recruitsmapoverhaul$setSelectedChunk(access.recruitsmapoverhaul$getHoveredChunk());
        }

        updateClickedClaim(mouseX, mouseY);

        if (button == 1) {
            int clickedBlockX = (int) Math.floor((mouseX - camera.offsetX()) / camera.scale());
            int clickedBlockZ = (int) Math.floor((mouseY - camera.offsetZ()) / camera.scale());
            access.recruitsmapoverhaul$setClickedBlock(clickedBlockX, clickedBlockZ);
            access.recruitsmapoverhaul$setSnapshotWorld(clickedBlockX, clickedBlockZ);
            contextMenu = new WorldMapContextMenu(screen);
            contextMenu.openAt((int) mouseX, (int) mouseY);
            claimInfoMenu.close();
        }

        if (button == 0) {
            if (tryStartWaypointDrag(mouseX, mouseY)) return true;
            access.recruitsmapoverhaul$setLastMouseX(mouseX);
            access.recruitsmapoverhaul$setLastMouseY(mouseY);
            access.recruitsmapoverhaul$setDragging(true);
        }

        return button == 0 || button == 1;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        access.recruitsmapoverhaul$setMouse(mouseX, mouseY);
        if (contextMenu.isVisible()) return false;
        if (button == 0) {
            if (draggingWaypointActive && draggingWaypoint != null) {
                finishWaypointDrag();
                return true;
            }
            access.recruitsmapoverhaul$setDragging(false);
        }
        if (claimInfoMenu.isVisible()) claimInfoMenu.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        access.recruitsmapoverhaul$setMouse(mouseX, mouseY);
        if (routeUi.isPopupVisible()) return true;
        if (draggingWaypointActive && draggingWaypoint != null) {
            access.recruitsmapoverhaul$setHoveredChunk(null);
            access.recruitsmapoverhaul$setSelectedChunk(null);
            int newWorldX = (int) Math.floor((mouseX - camera.offsetX()) / camera.scale());
            int newWorldZ = (int) Math.floor((mouseY - camera.offsetZ()) / camera.scale());
            draggingWaypoint.setPosition(new BlockPos(newWorldX, resolveSurfaceY(newWorldX, newWorldZ), newWorldZ));
            return true;
        }
        if (access.recruitsmapoverhaul$isDragging()) {
            camera.panByScreenDelta(
                    mouseX - access.recruitsmapoverhaul$getLastMouseX(),
                    mouseY - access.recruitsmapoverhaul$getLastMouseY());
            access.recruitsmapoverhaul$setLastMouseX(mouseX);
            access.recruitsmapoverhaul$setLastMouseY(mouseY);
            if (claimInfoMenu.isVisible()) claimInfoMenu.close();
            return true;
        }
        if (claimInfoMenu.isVisible()) claimInfoMenu.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        access.recruitsmapoverhaul$setMouse(mouseX, mouseY);
        if (routeUi.isPopupVisible()) return true;
        if (claimInfoMenu.isVisible()) claimInfoMenu.close();
        if (contextMenu.isVisible()) contextMenu.close();
        camera.zoomAt(mouseX, mouseY, scrollY);
        return true;
    }

    public void mouseMoved(double mouseX, double mouseY) {
        access.recruitsmapoverhaul$setMouse(mouseX, mouseY);
        routeUi.mouseMoved(mouseX, mouseY);

        if (routeUi.isMouseBlockingMap(mouseX, mouseY)) {
            access.recruitsmapoverhaul$setHoveredChunk(null);
            return;
        }

        int hoverBlockX = (int) Math.floor((mouseX - camera.offsetX()) / camera.scale());
        int hoverBlockZ = (int) Math.floor((mouseY - camera.offsetZ()) / camera.scale());
        access.recruitsmapoverhaul$setHoverBlock(hoverBlockX, hoverBlockZ);
        access.recruitsmapoverhaul$setHoveredChunk(new ChunkPos(hoverBlockX >> 4, hoverBlockZ >> 4));
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (routeUi.keyPressed(keyCode)) return true;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (claimInfoMenu.isVisible()) {
                claimInfoMenu.close();
                return true;
            }
            if (contextMenu.isVisible()) {
                contextMenu.close();
                return true;
            }
            onClose();
            return true;
        }

        if (!contextMenu.isVisible() && !claimInfoMenu.isVisible()) {
            double moveSpeed = 40.0 / camera.scale();
            switch (keyCode) {
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> camera.panByScreenDelta(0.0, moveSpeed);
                case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> camera.panByScreenDelta(0.0, -moveSpeed);
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> camera.panByScreenDelta(moveSpeed, 0.0);
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> camera.panByScreenDelta(-moveSpeed, 0.0);
                case GLFW.GLFW_KEY_EQUAL -> mouseScrolled(screen.width / 2.0, screen.height / 2.0, 1);
                case GLFW.GLFW_KEY_MINUS -> mouseScrolled(screen.width / 2.0, screen.height / 2.0, -1);
                case GLFW.GLFW_KEY_C -> centerOnPlayer();
                case GLFW.GLFW_KEY_R -> resetZoom();
            }
        }
        return true;
    }

    public boolean charTyped(char chr, int modifiers) {
        return routeUi.charTyped(chr, modifiers);
    }

    public void tick() {
        routeUi.tick();
    }

    public void onClose() {
        renderer.close();
        tileManager.flush();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(null);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void centerOnPlayer() {
        camera.centerOnPlayer(access.recruitsmapoverhaul$getPlayer());
    }

    public void centerOnClaim(RecruitsClaim claim) {
        if (claim != null) camera.centerOnClaim(claim.getCenter());
    }

    public void resetZoom() {
        camera.resetZoom(access.recruitsmapoverhaul$getPlayer());
    }

    public void refreshRouteUI() {
        routeUi.refreshRouteDropdown();
    }

    public BlockPos getHoveredBlockPos() {
        int x = access.recruitsmapoverhaul$getHoverBlockX();
        int z = access.recruitsmapoverhaul$getHoverBlockZ();
        return new BlockPos(x, resolveSurfaceY(x, z), z);
    }

    public BlockPos getClickedBlockPos() {
        int x = access.recruitsmapoverhaul$getClickedBlockX();
        int z = access.recruitsmapoverhaul$getClickedBlockZ();
        return new BlockPos(x, resolveSurfaceY(x, z), z);
    }

    public void setSelectedChunk(ChunkPos chunk) {
        access.recruitsmapoverhaul$setSelectedChunk(chunk);
    }

    public double getScale() {
        return camera.scale();
    }

    public boolean canAddRoute() {
        return access.recruitsmapoverhaul$getSelectedRoute() != null;
    }

    public void addRoute() {
        routeUi.openRouteNamePopup();
        contextMenu.close();
    }

    public void addWaypointAtClicked() {
        RecruitsRoute selectedRoute = access.recruitsmapoverhaul$getSelectedRoute();
        if (selectedRoute == null) return;

        BlockPos pos = getClickedBlockPos();
        ChunkPos chunk = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getChunkSource().getChunk(chunk.x, chunk.z, false) == null) return;

        selectedRoute.addWaypoint(new RecruitsRoute.Waypoint("WP " + (selectedRoute.getWaypoints().size() + 1), pos, null));
        ClientManager.saveRoute(selectedRoute);
    }

    public boolean canPlaceWaypointAt(int worldX, int worldZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return false;
        ChunkPos chunk = new ChunkPos(worldX >> 4, worldZ >> 4);
        if (!tileManager.isChunkExplored(chunk)) return false;
        return minecraft.level.getChunkSource().getChunk(chunk.x, chunk.z, false) != null;
    }

    public void openWaypointEditPopup(double mouseX, double mouseY) {
        RecruitsRoute.Waypoint waypoint = RouteRenderer.getWaypointAt(
                access.recruitsmapoverhaul$getSelectedRoute(), mouseX, mouseY,
                camera.offsetX(), camera.offsetZ(), camera.scale());
        if (waypoint == null) return;
        routeUi.openWaypointEditPopup(waypoint);
        contextMenu.close();
    }

    public void removeWaypointAt(double mouseX, double mouseY) {
        RecruitsRoute selectedRoute = access.recruitsmapoverhaul$getSelectedRoute();
        if (selectedRoute == null) return;

        RecruitsRoute.Waypoint waypoint = RouteRenderer.getWaypointAt(
                selectedRoute, mouseX, mouseY, camera.offsetX(), camera.offsetZ(), camera.scale());
        if (waypoint != null) {
            selectedRoute.removeWaypoint(waypoint);
            ClientManager.saveRoute(selectedRoute);
        }
    }

    public boolean isWaypointHoveredAt(double mouseX, double mouseY) {
        RecruitsRoute selectedRoute = access.recruitsmapoverhaul$getSelectedRoute();
        return selectedRoute != null
                && RouteRenderer.getWaypointAt(selectedRoute, mouseX, mouseY, camera.offsetX(), camera.offsetZ(), camera.scale()) != null;
    }

    private void renderClaims(GuiGraphics guiGraphics) {
        RecruitsClaim selectedClaim = access.recruitsmapoverhaul$getSelectedClaim();
        if (access.recruitsmapoverhaul$isClaimTransparency() && access.recruitsmapoverhaul$getSelectedRoute() != null) {
            ClaimRenderer.renderClaimsOverlayTransparent(guiGraphics, selectedClaim, camera.offsetX(), camera.offsetZ(), camera.scale());
        } else {
            ClaimRenderer.renderClaimsOverlay(guiGraphics, selectedClaim, camera.offsetX(), camera.offsetZ(), camera.scale());
        }
    }

    private void renderContextPreview(GuiGraphics guiGraphics) {
        if (!contextMenu.isVisible()) return;

        String entryTag = contextMenu.getHoveredEntryTag();
        if (entryTag == null) return;

        ChunkPos selectedChunk = access.recruitsmapoverhaul$getSelectedChunk();
        if (entryTag.contains("bufferzone")) ClaimRenderer.renderBufferZone(guiGraphics, camera.offsetX(), camera.offsetZ(), camera.scale());
        if (entryTag.contains("area")) ClaimRenderer.renderAreaPreview(guiGraphics, screen.getClaimArea(selectedChunk), camera.offsetX(), camera.offsetZ(), camera.scale());
        if (entryTag.contains("chunk")) ClaimRenderer.renderAreaPreview(guiGraphics, screen.getClaimableChunks(selectedChunk, 16), camera.offsetX(), camera.offsetZ(), camera.scale());
    }

    private void renderSelection(GuiGraphics guiGraphics) {
        ChunkPos selectedChunk = access.recruitsmapoverhaul$getSelectedChunk();
        RecruitsClaim selectedClaim = access.recruitsmapoverhaul$getSelectedClaim();
        ChunkPos hoveredChunk = access.recruitsmapoverhaul$getHoveredChunk();

        if (selectedChunk != null && (selectedClaim == null || contextMenu.isVisible())) {
            renderer.renderChunkOutline(guiGraphics, selectedChunk.x, selectedChunk.z, WorldMapRenderer.CHUNK_SELECTION_COLOR);
        }
        if (hoveredChunk != null) renderer.renderChunkHighlight(guiGraphics, hoveredChunk.x, hoveredChunk.z);
    }

    private void renderSelectedRoute(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        RecruitsRoute selectedRoute = access.recruitsmapoverhaul$getSelectedRoute();
        if (selectedRoute == null) return;

        RouteRenderer.renderRoute(guiGraphics, selectedRoute, camera.offsetX(), camera.offsetZ(), camera.scale(), draggingWaypoint, -1);
        if (draggingWaypointActive && draggingWaypoint != null) {
            RouteRenderer.renderDragGhost(guiGraphics, draggingWaypoint, mouseX, mouseY);
        }
    }

    private void renderClaimInfo(GuiGraphics guiGraphics) {
        RecruitsClaim selectedClaim = access.recruitsmapoverhaul$getSelectedClaim();
        if (selectedClaim == null || !claimInfoMenu.isVisible()) return;

        Point point = screen.getClaimInfoMenuPosition(selectedClaim, claimInfoMenu.width, claimInfoMenu.height);
        claimInfoMenu.setPosition(point.x, point.y);
        claimInfoMenu.render(guiGraphics);
    }

    private void updateClickedClaim(double mouseX, double mouseY) {
        RecruitsClaim clickedClaim = ClaimRenderer.getClaimAtPosition(mouseX, mouseY, camera.offsetX(), camera.offsetZ(), camera.scale());
        if (clickedClaim != null) {
            boolean canInspect = !ClientManager.configFogOfWarEnabled
                    || screen.isPlayerAdminAndCreative()
                    || ClaimRenderer.isClaimExplored(clickedClaim);
            if (canInspect) {
                access.recruitsmapoverhaul$setSelectedClaim(clickedClaim);
                claimInfoMenu.openForClaim(clickedClaim, (int) mouseX, (int) mouseY);
            } else {
                access.recruitsmapoverhaul$setSelectedClaim(null);
                claimInfoMenu.close();
            }
        } else {
            access.recruitsmapoverhaul$setSelectedClaim(null);
            claimInfoMenu.close();
        }
    }

    private boolean tryStartWaypointDrag(double mouseX, double mouseY) {
        RecruitsRoute selectedRoute = access.recruitsmapoverhaul$getSelectedRoute();
        if (selectedRoute == null) return false;

        RecruitsRoute.Waypoint waypoint = RouteRenderer.getWaypointAt(selectedRoute, mouseX, mouseY, camera.offsetX(), camera.offsetZ(), camera.scale());
        if (waypoint == null) return false;

        draggingWaypoint = waypoint;
        dragOriginalPos = waypoint.getPosition();
        draggingWaypointActive = true;
        access.recruitsmapoverhaul$setHoveredChunk(null);
        access.recruitsmapoverhaul$setSelectedChunk(null);
        return true;
    }

    private void finishWaypointDrag() {
        BlockPos finalPos = draggingWaypoint.getPosition();
        if (canPlaceWaypointAt(finalPos.getX(), finalPos.getZ())) {
            ClientManager.saveRoute(access.recruitsmapoverhaul$getSelectedRoute());
        } else if (dragOriginalPos != null) {
            draggingWaypoint.setPosition(dragOriginalPos);
        }
        draggingWaypoint = null;
        dragOriginalPos = null;
        draggingWaypointActive = false;
    }

    private void updateVisibleMapTiles() {
        if (!RecruitsClientConfig.UpdateMapTiles.get()) return;
        long now = System.nanoTime();
        if (now - lastVisibleTileUpdateNanos < 16_000_000L) return;
        lastVisibleTileUpdateNanos = now;
        tileManager.updateVisibleArea(camera.offsetX(), camera.offsetZ(), camera.scale(), screen.width, screen.height);
    }

    private void closeContextMenu() {
        contextMenu.close();
    }

    private int resolveSurfaceY(int worldX, int worldZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return 64;

        ChunkPos chunk = new ChunkPos(worldX >> 4, worldZ >> 4);
        if (minecraft.level.getChunkSource().getChunk(chunk.x, chunk.z, false) == null) return 64;
        int y = minecraft.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
        return Math.max(y, minecraft.level.getMinBuildHeight());
    }
}
