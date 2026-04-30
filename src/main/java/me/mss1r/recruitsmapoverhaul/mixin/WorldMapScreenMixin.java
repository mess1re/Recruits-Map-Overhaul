package me.mss1r.recruitsmapoverhaul.mixin;

import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsRoute;
import me.mss1r.recruitsmapoverhaul.client.gui.worldmap.WorldMapController;
import me.mss1r.recruitsmapoverhaul.client.gui.worldmap.WorldMapScreenAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = WorldMapScreen.class, remap = false)
public abstract class WorldMapScreenMixin extends Screen implements WorldMapScreenAccess {
    @Shadow @Final private Player player;
    @Shadow private double offsetX;
    @Shadow private double offsetZ;
    @Shadow public static double scale;
    @Shadow public double lastMouseX;
    @Shadow public double lastMouseY;
    @Shadow private boolean isDragging;
    @Shadow private ChunkPos hoveredChunk;
    @Shadow private ChunkPos selectedChunk;
    @Shadow private int clickedBlockX;
    @Shadow private int clickedBlockZ;
    @Shadow private int hoverBlockX;
    @Shadow private int hoverBlockZ;
    @Shadow private RecruitsClaim selectedClaim;
    @Shadow public RecruitsRoute selectedRoute;
    @Shadow private int snapshotWorldX;
    @Shadow private int snapshotWorldZ;
    @Shadow public boolean claimTransparency;
    @Shadow public double mouseX;
    @Shadow public double mouseY;

    @Unique private WorldMapController recruitsmapoverhaul$controller;

    protected WorldMapScreenMixin(Component title) {
        super(title);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    protected void init() {
        recruitsmapoverhaul$controller().init();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        recruitsmapoverhaul$controller().render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void renderBackground(GuiGraphics guiGraphics) {
        recruitsmapoverhaul$controller().renderBackground(guiGraphics);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return recruitsmapoverhaul$controller().mouseClicked(mouseX, mouseY, button);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return recruitsmapoverhaul$controller().mouseReleased(mouseX, mouseY, button);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return recruitsmapoverhaul$controller().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return recruitsmapoverhaul$controller().mouseScrolled(mouseX, mouseY, scrollY);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void mouseMoved(double mouseX, double mouseY) {
        recruitsmapoverhaul$controller().mouseMoved(mouseX, mouseY);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return recruitsmapoverhaul$controller().keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean charTyped(char chr, int modifiers) {
        return recruitsmapoverhaul$controller().charTyped(chr, modifiers);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void tick() {
        recruitsmapoverhaul$controller().tick();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void onClose() {
        recruitsmapoverhaul$controller().onClose();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean isPauseScreen() {
        return recruitsmapoverhaul$controller().isPauseScreen();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public BlockPos getHoveredBlockPos() {
        return recruitsmapoverhaul$controller().getHoveredBlockPos();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public BlockPos getClickedBlockPos() {
        return recruitsmapoverhaul$controller().getClickedBlockPos();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public double getScale() {
        return recruitsmapoverhaul$controller().getScale();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void setSelectedChunk(ChunkPos chunk) {
        recruitsmapoverhaul$controller().setSelectedChunk(chunk);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void refreshRouteUI() {
        recruitsmapoverhaul$controller().refreshRouteUI();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void centerOnPlayer() {
        recruitsmapoverhaul$controller().centerOnPlayer();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void centerOnClaim(RecruitsClaim claim) {
        recruitsmapoverhaul$controller().centerOnClaim(claim);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void resetZoom() {
        recruitsmapoverhaul$controller().resetZoom();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean canAddRoute() {
        return recruitsmapoverhaul$controller().canAddRoute();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void addRoute() {
        recruitsmapoverhaul$controller().addRoute();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void addWaypointAtClicked() {
        recruitsmapoverhaul$controller().addWaypointAtClicked();
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean canPlaceWaypointAt(int worldX, int worldZ) {
        return recruitsmapoverhaul$controller().canPlaceWaypointAt(worldX, worldZ);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void openWaypointEditPopup(double mouseX, double mouseY) {
        recruitsmapoverhaul$controller().openWaypointEditPopup(mouseX, mouseY);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public void removeWaypointAt(double mouseX, double mouseY) {
        recruitsmapoverhaul$controller().removeWaypointAt(mouseX, mouseY);
    }

    /**
     * @author mss1r
     * @reason Delegate the original Recruits world map behavior to the overhaul controller.
     */
    @Overwrite
    public boolean isWaypointHoveredAt(double mouseX, double mouseY) {
        return recruitsmapoverhaul$controller().isWaypointHoveredAt(mouseX, mouseY);
    }

    @Unique
    private WorldMapController recruitsmapoverhaul$controller() {
        if (recruitsmapoverhaul$controller == null) {
            recruitsmapoverhaul$controller = new WorldMapController((WorldMapScreen) (Object) this, this);
        }
        return recruitsmapoverhaul$controller;
    }

    @Override
    public Player recruitsmapoverhaul$getPlayer() {
        return player;
    }

    @Override
    public double recruitsmapoverhaul$getOffsetX() {
        return offsetX;
    }

    @Override
    public void recruitsmapoverhaul$setOffsetX(double value) {
        offsetX = value;
    }

    @Override
    public double recruitsmapoverhaul$getOffsetZ() {
        return offsetZ;
    }

    @Override
    public void recruitsmapoverhaul$setOffsetZ(double value) {
        offsetZ = value;
    }

    @Override
    public double recruitsmapoverhaul$getScale() {
        return scale;
    }

    @Override
    public void recruitsmapoverhaul$setScale(double value) {
        scale = value;
    }

    @Override
    public double recruitsmapoverhaul$getLastMouseX() {
        return lastMouseX;
    }

    @Override
    public void recruitsmapoverhaul$setLastMouseX(double value) {
        lastMouseX = value;
    }

    @Override
    public double recruitsmapoverhaul$getLastMouseY() {
        return lastMouseY;
    }

    @Override
    public void recruitsmapoverhaul$setLastMouseY(double value) {
        lastMouseY = value;
    }

    @Override
    public boolean recruitsmapoverhaul$isDragging() {
        return isDragging;
    }

    @Override
    public void recruitsmapoverhaul$setDragging(boolean value) {
        isDragging = value;
    }

    @Override
    public ChunkPos recruitsmapoverhaul$getHoveredChunk() {
        return hoveredChunk;
    }

    @Override
    public void recruitsmapoverhaul$setHoveredChunk(ChunkPos value) {
        hoveredChunk = value;
    }

    @Override
    public ChunkPos recruitsmapoverhaul$getSelectedChunk() {
        return selectedChunk;
    }

    @Override
    public void recruitsmapoverhaul$setSelectedChunk(ChunkPos value) {
        selectedChunk = value;
    }

    @Override
    public int recruitsmapoverhaul$getClickedBlockX() {
        return clickedBlockX;
    }

    @Override
    public int recruitsmapoverhaul$getClickedBlockZ() {
        return clickedBlockZ;
    }

    @Override
    public void recruitsmapoverhaul$setClickedBlock(int x, int z) {
        clickedBlockX = x;
        clickedBlockZ = z;
    }

    @Override
    public int recruitsmapoverhaul$getHoverBlockX() {
        return hoverBlockX;
    }

    @Override
    public int recruitsmapoverhaul$getHoverBlockZ() {
        return hoverBlockZ;
    }

    @Override
    public void recruitsmapoverhaul$setHoverBlock(int x, int z) {
        hoverBlockX = x;
        hoverBlockZ = z;
    }

    @Override
    public RecruitsClaim recruitsmapoverhaul$getSelectedClaim() {
        return selectedClaim;
    }

    @Override
    public void recruitsmapoverhaul$setSelectedClaim(RecruitsClaim value) {
        selectedClaim = value;
    }

    @Override
    public RecruitsRoute recruitsmapoverhaul$getSelectedRoute() {
        return selectedRoute;
    }

    @Override
    public void recruitsmapoverhaul$setSelectedRoute(RecruitsRoute value) {
        selectedRoute = value;
    }

    @Override
    public int recruitsmapoverhaul$getSnapshotWorldX() {
        return snapshotWorldX;
    }

    @Override
    public int recruitsmapoverhaul$getSnapshotWorldZ() {
        return snapshotWorldZ;
    }

    @Override
    public void recruitsmapoverhaul$setSnapshotWorld(int x, int z) {
        snapshotWorldX = x;
        snapshotWorldZ = z;
    }

    @Override
    public boolean recruitsmapoverhaul$isClaimTransparency() {
        return claimTransparency;
    }

    @Override
    public void recruitsmapoverhaul$setClaimTransparency(boolean value) {
        claimTransparency = value;
    }

    @Override
    public double recruitsmapoverhaul$getMouseX() {
        return mouseX;
    }

    @Override
    public double recruitsmapoverhaul$getMouseY() {
        return mouseY;
    }

    @Override
    public void recruitsmapoverhaul$setMouse(double x, double y) {
        mouseX = x;
        mouseY = y;
    }
}
