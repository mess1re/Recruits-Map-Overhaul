package me.mss1r.recruitsmapoverhaul.client.gui.worldmap;

import me.mss1r.recruitsmapoverhaul.api.WorldMapOverlay;
import me.mss1r.recruitsmapoverhaul.api.WorldMapOverlayRegistry;
import me.mss1r.recruitsmapoverhaul.api.WorldMapView;
import net.minecraft.client.gui.GuiGraphics;

final class WorldMapOverlayDispatcher {
    private final WorldMapView view;

    WorldMapOverlayDispatcher(WorldMapView view) {
        this.view = view;
    }

    void renderMap(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        for (WorldMapOverlay overlay : WorldMapOverlayRegistry.overlays()) {
            overlay.renderMap(graphics, mouseX, mouseY, partialTicks, view);
        }
    }

    void renderUi(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        for (WorldMapOverlay overlay : WorldMapOverlayRegistry.overlays()) {
            overlay.renderUi(graphics, mouseX, mouseY, partialTicks, view);
        }
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (WorldMapOverlay overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.mouseClicked(mouseX, mouseY, button, view)) return true;
        }
        return false;
    }

    boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (WorldMapOverlay overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.mouseReleased(mouseX, mouseY, button, view)) return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (WorldMapOverlay overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.mouseDragged(mouseX, mouseY, button, dragX, dragY, view)) return true;
        }
        return false;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        for (WorldMapOverlay overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.mouseScrolled(mouseX, mouseY, scrollY, view)) return true;
        }
        return false;
    }

    void mouseMoved(double mouseX, double mouseY) {
        for (WorldMapOverlay overlay : WorldMapOverlayRegistry.overlays()) {
            overlay.mouseMoved(mouseX, mouseY, view);
        }
    }

    boolean blocksMouse(double mouseX, double mouseY) {
        for (WorldMapOverlay overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.isMouseBlockingMap(mouseX, mouseY, view)) return true;
        }
        return false;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (WorldMapOverlay overlay : WorldMapOverlayRegistry.overlays()) {
            if (overlay.keyPressed(keyCode, scanCode, modifiers, view)) return true;
        }
        return false;
    }
}
