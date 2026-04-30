package me.mss1r.recruitsmapoverhaul.client.gui.worldmap;

import com.talhanation.recruits.client.ClientManager;
import com.talhanation.recruits.client.gui.widgets.DropDownMenu;
import com.talhanation.recruits.client.gui.worldmap.RouteEditPopup;
import com.talhanation.recruits.client.gui.worldmap.RouteNamePopup;
import com.talhanation.recruits.client.gui.worldmap.WaypointEditPopup;
import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import com.talhanation.recruits.world.RecruitsRoute;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public final class WorldMapRouteUi {
    private static final int ROUTE_UI_X = 10;
    private static final int ROUTE_UI_Y = 10;
    private static final int ROUTE_DROPDOWN_W = 140;
    private static final int ROUTE_BTN_SIZE = 20;
    private static final int ROUTE_BTN_GAP = 3;
    private static final int BUTTON_BG = 0x80222222;
    private static final int BUTTON_BG_HOVERED = 0x80444444;
    private static final int BUTTON_BG_SELECTED = 0x80555555;
    private static final int BUTTON_OUTLINE = 0x40FFFFFF;
    private static final int BUTTON_OUTLINE_HOVERED = 0x80FFFFFF;

    private final WorldMapScreen screen;
    private final WorldMapScreenAccess access;

    private DropDownMenu<RecruitsRoute> routeDropDown;
    private RouteNamePopup routeNamePopup;
    private RouteEditPopup routeEditPopup;
    private WaypointEditPopup waypointEditPopup;

    public WorldMapRouteUi(WorldMapScreen screen, WorldMapScreenAccess access) {
        this.screen = screen;
        this.access = access;
    }

    public void init(Player player) {
        refreshRouteDropdown();
        routeNamePopup = new RouteNamePopup(screen);
        routeEditPopup = new RouteEditPopup(screen, player);
        waypointEditPopup = new WaypointEditPopup(screen);
    }

    public void refreshRouteDropdown() {
        List<RecruitsRoute> routes = ClientManager.getRoutesList();
        List<RecruitsRoute> options = new ArrayList<>();
        options.add(null);
        options.addAll(routes);

        routeDropDown = new DropDownMenu<>(
                access.recruitsmapoverhaul$getSelectedRoute(),
                ROUTE_UI_X,
                ROUTE_UI_Y,
                ROUTE_DROPDOWN_W,
                ROUTE_BTN_SIZE,
                options,
                route -> route == null ? "Routes" : route.getName(),
                route -> {
                    access.recruitsmapoverhaul$setSelectedRoute(route);
                    if (route == null) access.recruitsmapoverhaul$setClaimTransparency(false);
                }
        );

        routeDropDown.setBgFill(BUTTON_BG);
        routeDropDown.setBgFillHovered(BUTTON_BG_HOVERED);
        routeDropDown.setBgFillSelected(BUTTON_BG_SELECTED);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        renderRouteDropdown(guiGraphics, mouseX, mouseY, partialTicks);

        int addX = getAddBtnX();
        boolean addHovered = isOver(mouseX, mouseY, addX, ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE);
        guiGraphics.fill(addX, ROUTE_UI_Y, addX + ROUTE_BTN_SIZE, ROUTE_UI_Y + ROUTE_BTN_SIZE,
                addHovered ? BUTTON_BG_HOVERED : BUTTON_BG);
        guiGraphics.renderOutline(addX, ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE,
                addHovered ? BUTTON_OUTLINE_HOVERED : BUTTON_OUTLINE);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, "+", addX + ROUTE_BTN_SIZE / 2, ROUTE_UI_Y + 6, 0xFFFFFF);

        if (access.recruitsmapoverhaul$getSelectedRoute() == null) return;

        int editX = getEditBtnX();
        boolean editHovered = isOver(mouseX, mouseY, editX, ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE);
        guiGraphics.fill(editX, ROUTE_UI_Y, editX + ROUTE_BTN_SIZE, ROUTE_UI_Y + ROUTE_BTN_SIZE,
                editHovered ? BUTTON_BG_HOVERED : BUTTON_BG);
        guiGraphics.renderOutline(editX, ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE,
                editHovered ? BUTTON_OUTLINE_HOVERED : BUTTON_OUTLINE);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, "\u2699", editX + ROUTE_BTN_SIZE / 2, ROUTE_UI_Y + 6, 0xFFFFFF);

        int transX = getTransBtnX();
        boolean transHovered = isOver(mouseX, mouseY, transX, ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE);
        boolean transparent = access.recruitsmapoverhaul$isClaimTransparency();
        int transBg = transparent ? BUTTON_BG_SELECTED : (transHovered ? BUTTON_BG_HOVERED : BUTTON_BG);
        int transColor = transparent ? 0xFFFFAA00 : 0xFFFFFF;
        guiGraphics.fill(transX, ROUTE_UI_Y, transX + ROUTE_BTN_SIZE, ROUTE_UI_Y + ROUTE_BTN_SIZE, transBg);
        guiGraphics.renderOutline(transX, ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE,
                transHovered || transparent ? BUTTON_OUTLINE_HOVERED : BUTTON_OUTLINE);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, "\u25A1", transX + ROUTE_BTN_SIZE / 2, ROUTE_UI_Y + 6, transColor);
    }

    public void renderPopups(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (routeNamePopup.isVisible()) routeNamePopup.render(guiGraphics, mouseX, mouseY);
        if (routeEditPopup.isVisible()) routeEditPopup.render(guiGraphics, mouseX, mouseY);
        if (waypointEditPopup.isVisible()) waypointEditPopup.render(guiGraphics, mouseX, mouseY);
    }

    public boolean handlePopupMouseClicked(double mouseX, double mouseY) {
        if (routeNamePopup.isVisible()) return routeNamePopup.mouseClicked(mouseX, mouseY);
        if (routeEditPopup.isVisible()) return routeEditPopup.mouseClicked(mouseX, mouseY);
        if (waypointEditPopup.isVisible()) return waypointEditPopup.mouseClicked(mouseX, mouseY);
        return false;
    }

    public boolean handleRouteMouseClicked(double mouseX, double mouseY, Runnable closeContextMenu) {
        if (isOver(mouseX, mouseY, getAddBtnX(), ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE)) {
            access.recruitsmapoverhaul$setHoveredChunk(null);
            access.recruitsmapoverhaul$setSelectedChunk(null);
            routeNamePopup.open();
            closeContextMenu.run();
            return true;
        }

        if (access.recruitsmapoverhaul$getSelectedRoute() != null
                && isOver(mouseX, mouseY, getEditBtnX(), ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE)) {
            access.recruitsmapoverhaul$setHoveredChunk(null);
            access.recruitsmapoverhaul$setSelectedChunk(null);
            routeEditPopup.open(access.recruitsmapoverhaul$getSelectedRoute());
            return true;
        }

        if (access.recruitsmapoverhaul$getSelectedRoute() != null
                && isOver(mouseX, mouseY, getTransBtnX(), ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE)) {
            access.recruitsmapoverhaul$setClaimTransparency(!access.recruitsmapoverhaul$isClaimTransparency());
            return true;
        }

        if (routeDropDown != null && isRouteHeaderHovered(mouseX, mouseY)) {
            access.recruitsmapoverhaul$setHoveredChunk(null);
            access.recruitsmapoverhaul$setSelectedChunk(null);
            routeDropDown.onMouseClick(mouseX, mouseY);
            return true;
        }

        if (routeDropDown != null && routeDropDown.isOpen() && routeDropDown.isMouseOver(mouseX, mouseY)) {
            access.recruitsmapoverhaul$setHoveredChunk(null);
            access.recruitsmapoverhaul$setSelectedChunk(null);
            routeDropDown.onMouseClick(mouseX, mouseY);
            return true;
        }

        return false;
    }

    public void mouseMoved(double mouseX, double mouseY) {
        if (routeDropDown != null && routeDropDown.isOpen()) routeDropDown.onMouseMove(mouseX, mouseY);
    }

    public boolean isMouseBlockingMap(double mouseX, double mouseY) {
        return isPopupVisible()
                || isRouteHeaderHovered(mouseX, mouseY)
                || isRouteButtonHovered(mouseX, mouseY)
                || (routeDropDown != null && routeDropDown.isOpen() && routeDropDown.isMouseOver(mouseX, mouseY));
    }

    public boolean isPopupVisible() {
        return routeNamePopup != null && routeNamePopup.isVisible()
                || routeEditPopup != null && routeEditPopup.isVisible()
                || waypointEditPopup != null && waypointEditPopup.isVisible();
    }

    public boolean keyPressed(int keyCode) {
        if (waypointEditPopup.isVisible()) return waypointEditPopup.keyPressed(keyCode);
        if (routeEditPopup.isVisible()) return routeEditPopup.keyPressed(keyCode);
        if (routeNamePopup.isVisible()) return routeNamePopup.keyPressed(keyCode);
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (waypointEditPopup.isVisible()) return waypointEditPopup.charTyped(chr);
        if (routeEditPopup.isVisible()) return routeEditPopup.charTyped(chr, modifiers);
        if (routeNamePopup.isVisible()) return routeNamePopup.charTyped(chr, modifiers);
        return false;
    }

    public void tick() {
        if (routeNamePopup != null) routeNamePopup.tick();
        if (routeEditPopup != null) routeEditPopup.tick();
        if (waypointEditPopup != null) waypointEditPopup.tick();
    }

    public void openWaypointEditPopup(RecruitsRoute.Waypoint waypoint) {
        if (waypoint != null) waypointEditPopup.open(waypoint);
    }

    public void openRouteNamePopup() {
        routeNamePopup.open();
    }

    private void renderRouteDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (routeDropDown == null) return;

        if (routeDropDown.isOpen()) {
            routeDropDown.renderWidget(guiGraphics, mouseX, mouseY, partialTicks);
            return;
        }

        RecruitsRoute selectedRoute = access.recruitsmapoverhaul$getSelectedRoute();
        String label = selectedRoute != null ? selectedRoute.getName() : "Routes";
        boolean hovered = isRouteHeaderHovered(mouseX, mouseY);
        guiGraphics.fill(ROUTE_UI_X, ROUTE_UI_Y,
                ROUTE_UI_X + ROUTE_DROPDOWN_W,
                ROUTE_UI_Y + ROUTE_BTN_SIZE,
                hovered ? BUTTON_BG_HOVERED : BUTTON_BG);
        guiGraphics.renderOutline(ROUTE_UI_X, ROUTE_UI_Y, ROUTE_DROPDOWN_W, ROUTE_BTN_SIZE,
                hovered ? BUTTON_OUTLINE_HOVERED : BUTTON_OUTLINE);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, label,
                ROUTE_UI_X + ROUTE_DROPDOWN_W / 2,
                ROUTE_UI_Y + (ROUTE_BTN_SIZE - 8) / 2,
                0xFFFFFF);
    }

    private int getAddBtnX() {
        return ROUTE_UI_X + ROUTE_DROPDOWN_W + ROUTE_BTN_GAP;
    }

    private int getEditBtnX() {
        return getAddBtnX() + ROUTE_BTN_SIZE + ROUTE_BTN_GAP;
    }

    private int getTransBtnX() {
        return getEditBtnX() + ROUTE_BTN_SIZE + ROUTE_BTN_GAP;
    }

    private static boolean isRouteHeaderHovered(double mouseX, double mouseY) {
        return isOver(mouseX, mouseY, ROUTE_UI_X, ROUTE_UI_Y, ROUTE_DROPDOWN_W, ROUTE_BTN_SIZE);
    }

    private boolean isRouteButtonHovered(double mouseX, double mouseY) {
        if (isOver(mouseX, mouseY, getAddBtnX(), ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE)) return true;
        if (access.recruitsmapoverhaul$getSelectedRoute() == null) return false;
        return isOver(mouseX, mouseY, getEditBtnX(), ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE)
                || isOver(mouseX, mouseY, getTransBtnX(), ROUTE_UI_Y, ROUTE_BTN_SIZE, ROUTE_BTN_SIZE);
    }

    private static boolean isOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
