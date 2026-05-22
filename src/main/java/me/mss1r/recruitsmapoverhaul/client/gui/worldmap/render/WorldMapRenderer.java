package me.mss1r.recruitsmapoverhaul.client.gui.worldmap.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.talhanation.recruits.Main;
import com.talhanation.recruits.client.gui.worldmap.WorldMapScreen;
import com.talhanation.recruits.compat.smallships.SmallShips;
import me.mss1r.recruitsmapoverhaul.client.gui.worldmap.WorldMapCamera;
import me.mss1r.recruitsmapoverhaul.client.gui.worldmap.WorldMapScreenAccess;
import me.mss1r.recruitsmapoverhaul.client.map.cache.ChunkTile;
import me.mss1r.recruitsmapoverhaul.client.map.cache.ChunkTileManager;
import me.mss1r.recruitsmapoverhaul.client.gui.worldmap.render.MapRenderUtil;
import me.mss1r.recruitsmapoverhaul.config.RecruitsMapOverhaulClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;

public final class WorldMapRenderer {
    public static final int CHUNK_HIGHLIGHT_COLOR = 0x40FFFFFF;
    public static final int CHUNK_SELECTION_COLOR = 0xFFFFFFFF;
    public static final int DARK_GRAY_BG = 0xFF0D121A;

    private static final ResourceLocation MAP_ICONS = ResourceLocation.withDefaultNamespace("textures/map/map_icons.png");
    private static final int MAP_EDGE_COLOR = 0x55000000;
    private static final ItemStack BOAT_STACK = new ItemStack(Items.OAK_BOAT);
    private static final int[][] PLAYER_ARROW_SPANS = {
            {0, 2, 23, 25}, {0, 4, 21, 25}, {0, 6, 19, 25}, {1, 8, 17, 24}, {1, 10, 15, 24},
            {2, 23, -1, -1}, {2, 23, -1, -1}, {3, 22, -1, -1}, {3, 22, -1, -1},
            {4, 21, -1, -1}, {4, 21, -1, -1}, {5, 20, -1, -1}, {5, 20, -1, -1},
            {6, 19, -1, -1}, {6, 19, -1, -1}, {7, 18, -1, -1}, {7, 18, -1, -1},
            {7, 18, -1, -1}, {8, 17, -1, -1}, {9, 16, -1, -1}, {9, 16, -1, -1},
            {10, 15, -1, -1}, {10, 15, -1, -1}, {10, 15, -1, -1}, {11, 14, -1, -1},
            {11, 14, -1, -1}, {12, 13, -1, -1}
    };

    private final WorldMapScreen screen;
    private final WorldMapScreenAccess access;
    private final WorldMapCamera camera;
    private final ChunkTileManager tileManager;
    private final MapFramebufferPass framebufferPass = new MapFramebufferPass();

    private long lastFpsTime = 0L;
    private int fpsCounter = 0;
    private int currentFps = 0;

    public WorldMapRenderer(WorldMapScreen screen, WorldMapScreenAccess access, WorldMapCamera camera, ChunkTileManager tileManager) {
        this.screen = screen;
        this.access = access;
        this.camera = camera;
        this.tileManager = tileManager;
    }

    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, screen.width, screen.height, DARK_GRAY_BG);
    }

    public void renderMapTiles(GuiGraphics guiGraphics) {
        double tileSize = ChunkTile.TILE_PIXEL_SIZE;
        MapFramebufferPass.Frame frame = framebufferPass.begin(guiGraphics, camera, screen.width, screen.height);
        double scaledTileSize = tileSize * frame.fboScale();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float brightness = getMapBrightness();

        for (MapTileRenderPlan.Tile tilePos : MapTileRenderPlan.visibleTiles(
                frame.leftWorld(), frame.rightWorld(), frame.topWorld(), frame.bottomWorld(), tileSize)) {
            int tileX = tilePos.x();
            int tileZ = tilePos.z();
            ChunkTile tile = tileManager.getTileIfPresent(tileX, tileZ);
            if (tile == null) continue;

            float x1 = (float) (frame.renderOffsetX() + tileX * scaledTileSize);
            float z1 = (float) (frame.renderOffsetZ() + tileZ * scaledTileSize);
            float x2 = (float) (frame.renderOffsetX() + (tileX + 1) * scaledTileSize);
            float z2 = (float) (frame.renderOffsetZ() + (tileZ + 1) * scaledTileSize);
            tile.render(guiGraphics, x1, z1, Math.max(1.0f, x2 - x1), Math.max(1.0f, z2 - z1), brightness);
        }

        framebufferPass.endAndBlit(guiGraphics, frame);
    }

    public void renderMapChrome(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, screen.width, 1, MAP_EDGE_COLOR);
        guiGraphics.fill(0, screen.height - 1, screen.width, screen.height, MAP_EDGE_COLOR);
        guiGraphics.fill(0, 0, 1, screen.height, MAP_EDGE_COLOR);
        guiGraphics.fill(screen.width - 1, 0, screen.width, screen.height, MAP_EDGE_COLOR);
    }

    public void renderChunkHighlight(GuiGraphics guiGraphics, int chunkX, int chunkZ) {
        double x1 = camera.offsetX() + chunkX * 16.0 * camera.scale();
        double z1 = camera.offsetZ() + chunkZ * 16.0 * camera.scale();
        double size = 16.0 * camera.scale();
        MapRenderUtil.fill(guiGraphics, x1, z1, x1 + size, z1 + size, CHUNK_HIGHLIGHT_COLOR);
    }

    public void renderChunkOutline(GuiGraphics guiGraphics, int chunkX, int chunkZ, int color) {
        double x1 = camera.offsetX() + chunkX * 16.0 * camera.scale();
        double z1 = camera.offsetZ() + chunkZ * 16.0 * camera.scale();
        double x2 = x1 + 16.0 * camera.scale();
        double z2 = z1 + 16.0 * camera.scale();
        double thickness = Math.max(1.0, Math.min(2.0, camera.scale() * 0.35));
        MapRenderUtil.fill(guiGraphics, x1, z1, x2, z1 + thickness, color);
        MapRenderUtil.fill(guiGraphics, x1, z2 - thickness, x2, z2, color);
        MapRenderUtil.fill(guiGraphics, x1, z1, x1 + thickness, z2, color);
        MapRenderUtil.fill(guiGraphics, x2 - thickness, z1, x2, z2, color);
    }

    public void renderPlayerPosition(GuiGraphics guiGraphics) {
        Player player = access.recruitsmapoverhaul$getPlayer();
        if (player == null) return;

        double pixelX = camera.offsetX() + player.getX() * camera.scale();
        double pixelZ = camera.offsetZ() + player.getZ() * camera.scale();

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(pixelX, pixelZ, 0);
        if (player.getVehicle() instanceof Boat) renderPlayerBoat(pose, guiGraphics, player);
        else renderPlayerIcon(pose, guiGraphics, player);
        pose.popPose();

        renderPlayerNameTag(guiGraphics, player, pixelX, pixelZ);
    }

    public void renderCoordinatesAndZoom(GuiGraphics guiGraphics, int hoverY) {
        Font font = Minecraft.getInstance().font;
        int hoverBlockX = access.recruitsmapoverhaul$getHoverBlockX();
        int hoverBlockZ = access.recruitsmapoverhaul$getHoverBlockZ();
        String coords = String.format("X: %d, Y: %d, Z: %d", hoverBlockX, hoverY, hoverBlockZ);
        String zoom = String.format("Zoom: %.1fx", camera.scale());
        String combined = RecruitsMapOverhaulClientConfig.SHOW_COORDINATES.get() ? coords + " | " + zoom : zoom;
        int x = (screen.width - font.width(combined)) / 2;
        guiGraphics.drawString(font, combined, x, screen.height - 25, 0xFFFFFF, false);
    }

    public void renderFPS(GuiGraphics guiGraphics) {
        if (!RecruitsMapOverhaulClientConfig.SHOW_FPS_OVERLAY.get()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        fpsCounter++;
        if (currentTime - lastFpsTime >= 1000) {
            currentFps = fpsCounter;
            fpsCounter = 0;
            lastFpsTime = currentTime;
        }

        Font font = Minecraft.getInstance().font;
        String fpsText = String.format("FPS: %d", currentFps);
        int textWidth = font.width(fpsText);
        int x = screen.width - textWidth - 15;
        guiGraphics.drawString(font, fpsText, x, 7, 0x6CFF72);
    }

    public void close() {
        framebufferPass.close();
    }

    private float getMapBrightness() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimensionType() == null || !level.dimensionType().hasSkyLight()) {
            return 1.0f;
        }

        float ambient = Math.min(1.0f, 0.375f + level.dimensionType().ambientLight());
        float sunBrightness = (level.getSkyDarken(1.0f) - 0.2f) / 0.8f;
        return ambient + (1.0f - ambient) * Mth.clamp(sunBrightness, 0.0f, 1.0f);
    }

    private void renderPlayerBoat(PoseStack pose, GuiGraphics guiGraphics, Player player) {
        float yaw = player.getYRot() % 360f;
        if (yaw < -180f) yaw += 360f;
        if (yaw >= 180f) yaw -= 360f;
        boolean flipX = yaw > 0;

        pose.pushPose();
        if (flipX) pose.scale(-1f, 1f, 1f);
        pose.scale(1.5f, 1.5f, 1.5f);
        Lighting.setupForFlatItems();
        ItemStack boat = BOAT_STACK;
        if (Main.isSmallShipsLoaded && player.getVehicle() != null && SmallShips.isSmallShip(player.getVehicle())) {
            boat = SmallShips.getSmallShipsItem();
        }
        RenderSystem.disableCull();
        guiGraphics.renderItem(boat, -8, -8);
        RenderSystem.enableCull();
        pose.popPose();
    }

    private void renderPlayerIcon(PoseStack pose, GuiGraphics guiGraphics, Player player) {
        if (RecruitsMapOverhaulClientConfig.PLAYER_ARROW_STYLE.get()
                == RecruitsMapOverhaulClientConfig.PlayerArrowStyle.VANILLA) {
            renderVanillaPlayerIcon(pose, guiGraphics, player);
            return;
        }
        renderOverhauledPlayerIcon(pose, guiGraphics, player);
    }

    private void renderOverhauledPlayerIcon(PoseStack pose, GuiGraphics guiGraphics, Player player) {
        float arrowScale = getPlayerArrowScale();

        pose.pushPose();
        pose.translate(0, 2.0f * arrowScale, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(player.getYRot()));
        renderPlayerArrowGlyph(guiGraphics, 0xE0000000, arrowScale);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(player.getYRot()));
        renderPlayerArrowGlyph(guiGraphics, 0xFF2BEA68, arrowScale);
        pose.popPose();
    }

    private void renderVanillaPlayerIcon(PoseStack pose, GuiGraphics guiGraphics, Player player) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(player.getYRot()));
        pose.scale(5.0f, 5.0f, 5.0f);

        int iconIndex = 0;
        float u0 = (iconIndex % 16) / 16f;
        float v0 = (iconIndex / 16) / 16f;
        float u1 = u0 + 1f / 16f;
        float v1 = v0 + 1f / 16f;
        int color = 0xFFFFFFFF;
        int light = 0xF000F0;

        guiGraphics.flush();
        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.text(MAP_ICONS));
        Matrix4f matrix = pose.last().pose();
        consumer.vertex(matrix, -1f, 1f, 0f).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF).uv(u0, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
        consumer.vertex(matrix, 1f, 1f, 0f).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF).uv(u1, v0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
        consumer.vertex(matrix, 1f, -1f, 0f).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF).uv(u1, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
        consumer.vertex(matrix, -1f, -1f, 0f).color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >> 24) & 0xFF).uv(u0, v1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
        guiGraphics.flush();
        pose.popPose();
    }

    private void renderPlayerNameTag(GuiGraphics guiGraphics, Player player, double pixelX, double pixelZ) {
        if (camera.scale() <= 1.5) return;

        Font font = Minecraft.getInstance().font;
        String playerName = player.getName().getString();
        float textScale = (float) Math.min(1.0, camera.scale() / 1.25);
        int textWidth = font.width(playerName);
        int textHeight = font.lineHeight;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(pixelX - (textWidth * textScale) / 2.0, pixelZ - (textHeight * textScale) / 2.0 - 10, 0);
        guiGraphics.pose().scale(textScale, textScale, 1.0f);
        guiGraphics.fill(-3, -2, textWidth + 3, textHeight + 2, 0xA010161F);
        guiGraphics.renderOutline(-3, -2, textWidth + 6, textHeight + 4, 0x30000000);
        guiGraphics.drawString(font, playerName, 0, 0, 0xFFFFFF, false);
        guiGraphics.pose().popPose();
    }

    private float getPlayerArrowScale() {
        var window = Minecraft.getInstance().getWindow();
        double guiScale = Math.max(1.0, window.getGuiScale());
        double screenScale = Math.max(1.0, Math.min(window.getWidth(), window.getHeight()) / 1080.0);
        double farZoomDampening = Math.max(0.72, Math.min(1.0, Math.sqrt(Math.max(camera.scale(), WorldMapCamera.MIN_SCALE))));
        return (float) (screenScale / guiScale * farZoomDampening);
    }

    private void renderPlayerArrowGlyph(GuiGraphics guiGraphics, int color, float size) {
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.scale(size, size, 1.0f);
        for (int row = 0; row < PLAYER_ARROW_SPANS.length; row++) {
            int[] spans = PLAYER_ARROW_SPANS[row];
            drawPlayerArrowSpan(guiGraphics, spans[0], spans[1], row, color);
            if (spans[2] >= 0) drawPlayerArrowSpan(guiGraphics, spans[2], spans[3], row, color);
        }
        pose.popPose();
    }

    private void drawPlayerArrowSpan(GuiGraphics guiGraphics, int startX, int endX, int row, int color) {
        guiGraphics.fill(startX - 13, row - 5, endX - 12, row - 4, color);
    }
}
