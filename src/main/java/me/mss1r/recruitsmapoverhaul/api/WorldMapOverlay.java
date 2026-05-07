package me.mss1r.recruitsmapoverhaul.api;

import net.minecraft.client.gui.GuiGraphics;

public interface WorldMapOverlay {
    default void renderMap(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, WorldMapView view) {
    }

    default void renderUi(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, WorldMapView view) {
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button, WorldMapView view) {
        return false;
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button, WorldMapView view) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, WorldMapView view) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollY, WorldMapView view) {
        return false;
    }

    default void mouseMoved(double mouseX, double mouseY, WorldMapView view) {
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers, WorldMapView view) {
        return false;
    }

    default boolean isMouseBlockingMap(double mouseX, double mouseY, WorldMapView view) {
        return false;
    }
}
