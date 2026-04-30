package me.mss1r.recruitsmapoverhaul.mixin;

import com.talhanation.recruits.client.gui.worldmap.WorldMapContextMenu;
import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Method;
import java.util.List;

@Mixin(value = WorldMapContextMenu.class, remap = false)
public abstract class WorldMapContextMenuMixin {
    @Shadow private int x;
    @Shadow private int y;
    @Shadow private boolean visible;
    @Shadow @Final private int width;
    @Shadow @Final private int entryHeight;
    @Shadow @Final private WorldMapScreen worldMapScreen;
    @Shadow @Final private List<?> entries;
    @Shadow private double snapshotMouseX;
    @Shadow private double snapshotMouseY;

    @Unique private static Method recruitsmapoverhaul$shouldShowMethod;

    /**
     * The original clamps against all entries, including hidden admin/claim actions.
     * That can pin the menu near the top when only a few entries are actually visible.
     *
     * @author mss1r
     * @reason Keep the Recruits world map context menu anchored at the clicked map position.
     */
    @Overwrite
    public void openAt(int mouseX, int mouseY) {
        int visibleEntries = Math.max(1, recruitsmapoverhaul$countVisibleEntries());
        int menuHeight = visibleEntries * entryHeight;
        this.x = Math.max(10, Math.min(mouseX, worldMapScreen.width - width - 10));
        this.y = Math.max(10, Math.min(mouseY, worldMapScreen.height - menuHeight - 10));
        this.visible = true;
        this.snapshotMouseX = worldMapScreen.mouseX;
        this.snapshotMouseY = worldMapScreen.mouseY;
    }

    @Unique
    private int recruitsmapoverhaul$countVisibleEntries() {
        int count = 0;
        for (Object entry : entries) {
            if (recruitsmapoverhaul$shouldShow(entry)) {
                count++;
            }
        }
        return count;
    }

    @Unique
    private boolean recruitsmapoverhaul$shouldShow(Object entry) {
        try {
            Method method = recruitsmapoverhaul$shouldShowMethod;
            if (method == null) {
                method = entry.getClass().getDeclaredMethod("shouldShow", WorldMapScreen.class);
                method.setAccessible(true);
                recruitsmapoverhaul$shouldShowMethod = method;
            }
            return Boolean.TRUE.equals(method.invoke(entry, worldMapScreen));
        } catch (Exception ignored) {
            return true;
        }
    }
}
