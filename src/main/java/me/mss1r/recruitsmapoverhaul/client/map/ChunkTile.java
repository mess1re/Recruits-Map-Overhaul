package me.mss1r.recruitsmapoverhaul.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;

public class ChunkTile {
    private static final int MAX_MIP_LEVEL = 3;

    private final int tileX, tileZ;
    private NativeImage image;
    private final NativeImage[] mipImages = new NativeImage[MAX_MIP_LEVEL + 1];
    private final DynamicTexture[] textures = new DynamicTexture[MAX_MIP_LEVEL + 1];
    private final ResourceLocation[] textureIds = new ResourceLocation[MAX_MIP_LEVEL + 1];
    private final boolean[] textureDirty = new boolean[MAX_MIP_LEVEL + 1];
    private final boolean[] xaeroSamplingApplied = new boolean[MAX_MIP_LEVEL + 1];
    private boolean needsUpdate = false;

    public static final int TILE_SIZE = 10;
    public static final int PIXELS_PER_CHUNK = 16;
    public static final int TILE_PIXEL_SIZE = TILE_SIZE * PIXELS_PER_CHUNK;

    public ChunkTile(int tileX, int tileZ) {
        this.tileX = tileX;
        this.tileZ = tileZ;
    }

    public void loadOrCreate(File tileFile) {
        try {
            if (tileFile.exists() && tileFile.length() > 0) {
                byte[] fileData = java.nio.file.Files.readAllBytes(tileFile.toPath());
                this.image = NativeImage.read(fileData);
                if (this.image.getWidth() != TILE_PIXEL_SIZE ||
                        this.image.getHeight() != TILE_PIXEL_SIZE) {
                    this.image.close();
                    this.image = null;
                }
            }
        } catch (IOException ignored) {
            this.image = null;
        }

        if (this.image == null) {
            this.image = new NativeImage(NativeImage.Format.RGBA, TILE_PIXEL_SIZE, TILE_PIXEL_SIZE, false);
            for (int i = 0; i < TILE_PIXEL_SIZE * TILE_PIXEL_SIZE; i++) {
                this.image.setPixelRGBA(i % TILE_PIXEL_SIZE, i / TILE_PIXEL_SIZE, 0x00000000);
            }
            this.needsUpdate = true;
        }
        invalidateTextures(true);
    }

    public void updateFromChunkImage(ChunkImage chunkImage, int chunkXInTile, int chunkZInTile) {
        if (this.image == null || chunkImage == null || !chunkImage.isMeaningful()) return;

        NativeImage chunkImg = chunkImage.getNativeImage();
        int startX = chunkXInTile * PIXELS_PER_CHUNK;
        int startZ = chunkZInTile * PIXELS_PER_CHUNK;

        for (int x = 0; x < PIXELS_PER_CHUNK; x++) {
            for (int z = 0; z < PIXELS_PER_CHUNK; z++) {
                this.image.setPixelRGBA(startX + x, startZ + z, chunkImg.getPixelRGBA(x, z));
            }
        }

        this.needsUpdate = true;
        invalidateTextures(true);
    }

    public void mergeWithExistingTile(File existingTileFile) {
        if (!existingTileFile.exists() || this.image == null) return;

        try {
            byte[] existingData = java.nio.file.Files.readAllBytes(existingTileFile.toPath());
            NativeImage existingImage = NativeImage.read(existingData);

            if (existingImage.getWidth() == TILE_PIXEL_SIZE &&
                    existingImage.getHeight() == TILE_PIXEL_SIZE) {
                for (int i = 0; i < TILE_PIXEL_SIZE * TILE_PIXEL_SIZE; i++) {
                    int x = i % TILE_PIXEL_SIZE;
                    int y = i / TILE_PIXEL_SIZE;
                    int currentPixel = this.image.getPixelRGBA(x, y);
                    if (((currentPixel >> 24) & 0xFF) == 0) {
                        this.image.setPixelRGBA(x, y, existingImage.getPixelRGBA(x, y));
                    }
                }
                this.needsUpdate = true;
                invalidateTextures(true);
            }
            existingImage.close();
        } catch (IOException ignored) {}
    }

    private ResourceLocation ensureTextureReady(int mipLevel) {
        if (this.image == null) return null;
        mipLevel = Math.max(0, Math.min(MAX_MIP_LEVEL, mipLevel));

        Minecraft mc = Minecraft.getInstance();
        NativeImage levelImage = getMipImage(mipLevel);
        if (levelImage == null) return null;

        if (this.textures[mipLevel] == null || this.textureIds[mipLevel] == null) {
            this.textures[mipLevel] = new DynamicTexture(copyImage(levelImage));
            this.textures[mipLevel].setFilter(false, false);
            this.textureIds[mipLevel] = mc.getTextureManager().register(
                    "chunktile_" + tileX + "_" + tileZ + "_mip" + mipLevel,
                    this.textures[mipLevel]);
            this.textureDirty[mipLevel] = false;
            this.xaeroSamplingApplied[mipLevel] = false;
        }

        if (this.textureDirty[mipLevel]) {
            this.textures[mipLevel].setPixels(copyImage(levelImage));
            this.textures[mipLevel].upload();
            this.textureDirty[mipLevel] = false;
            this.xaeroSamplingApplied[mipLevel] = false;
        }

        applyXaeroSamplingMode(mc, mipLevel);
        return this.textureIds[mipLevel];
    }

    private NativeImage getMipImage(int mipLevel) {
        if (mipLevel == 0) {
            this.mipImages[0] = this.image;
            return this.image;
        }

        NativeImage mipImage = this.mipImages[mipLevel];
        if (mipImage != null) return mipImage;

        int factor = 1 << mipLevel;
        int size = TILE_PIXEL_SIZE / factor;
        mipImage = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                mipImage.setPixelRGBA(x, y, averagePixel(x * factor, y * factor, factor));
            }
        }
        mipImage.untrack();
        this.mipImages[mipLevel] = mipImage;
        this.textureDirty[mipLevel] = true;
        this.xaeroSamplingApplied[mipLevel] = false;
        return mipImage;
    }

    private int averagePixel(int startX, int startY, int size) {
        int alpha = 0;
        int blue = 0;
        int green = 0;
        int red = 0;
        int count = 0;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int pixel = this.image.getPixelRGBA(startX + x, startY + y);
                int pixelAlpha = (pixel >> 24) & 0xFF;
                if (pixelAlpha == 0) continue;

                alpha += pixelAlpha;
                blue += (pixel >> 16) & 0xFF;
                green += (pixel >> 8) & 0xFF;
                red += pixel & 0xFF;
                count++;
            }
        }

        if (count == 0) return 0x00000000;
        return ((alpha / count) << 24)
                | ((blue / count) << 16)
                | ((green / count) << 8)
                | (red / count);
    }

    private static NativeImage copyImage(NativeImage source) {
        NativeImage copy = new NativeImage(NativeImage.Format.RGBA, source.getWidth(), source.getHeight(), false);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                copy.setPixelRGBA(x, y, source.getPixelRGBA(x, y));
            }
        }
        copy.untrack();
        return copy;
    }

    private void applyXaeroSamplingMode(Minecraft mc, int mipLevel) {
        if (this.textureIds[mipLevel] == null || this.xaeroSamplingApplied[mipLevel]) return;
        mc.getTextureManager().bindForSetup(this.textureIds[mipLevel]);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        this.xaeroSamplingApplied[mipLevel] = true;
    }

    public void saveToFile(File tileFile) {
        if (this.image == null || !this.needsUpdate) return;
        try {
            tileFile.getParentFile().mkdirs();
            this.image.writeToFile(tileFile);
            this.needsUpdate = false;
        } catch (IOException ignored) {}
    }

    public void render(GuiGraphics guiGraphics, float x, float y, float width, float height, float brightness) {
        ResourceLocation renderTextureId = ensureTextureReady(chooseMipLevel(width / TILE_PIXEL_SIZE));
        if (renderTextureId != null && width > 0.0f && height > 0.0f) {
            VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.text(renderTextureId));
            Matrix4f matrix = guiGraphics.pose().last().pose();
            float x1 = x + width;
            float y1 = y + height;
            int light = 0xF000F0;
            int color = Math.max(0, Math.min(255, Math.round(brightness * 255.0f)));

            consumer.vertex(matrix, x, y1, 0.0f).color(color, color, color, 255).uv(0.0f, 1.0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, x1, y1, 0.0f).color(color, color, color, 255).uv(1.0f, 1.0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, x1, y, 0.0f).color(color, color, color, 255).uv(1.0f, 0.0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, x, y, 0.0f).color(color, color, color, 255).uv(0.0f, 0.0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 0, 1).endVertex();
        }
    }

    public void close() {
        closeTextures();
        try { if (this.image != null) this.image.close(); } catch (Exception ignored) {}
        this.image = null;
    }

    public int getTileX() { return tileX; }
    public int getTileZ() { return tileZ; }
    public NativeImage getImage() { return image; }
    public ResourceLocation getTextureId() {
        return ensureTextureReady(0);
    }
    public int getPixelARGB(int localX, int localZ) {
        if (this.image == null) {
            return 0x00000000;
        }

        int clampedX = Math.max(0, Math.min(TILE_PIXEL_SIZE - 1, localX));
        int clampedZ = Math.max(0, Math.min(TILE_PIXEL_SIZE - 1, localZ));
        int abgr = this.image.getPixelRGBA(clampedX, clampedZ);
        int alpha = (abgr >> 24) & 0xFF;
        int blue = (abgr >> 16) & 0xFF;
        int green = (abgr >> 8) & 0xFF;
        int red = abgr & 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
    public void markAccessed() { }
    public void markNeedsUpdate() { this.needsUpdate = true; }

    private void invalidateTextures(boolean includeBase) {
        for (int i = includeBase ? 0 : 1; i <= MAX_MIP_LEVEL; i++) {
            if (i > 0) {
                try { if (this.mipImages[i] != null) this.mipImages[i].close(); } catch (Exception ignored) {}
                this.mipImages[i] = null;
            }
            this.textureDirty[i] = true;
            this.xaeroSamplingApplied[i] = false;
        }
        this.mipImages[0] = this.image;
        this.textureDirty[0] = true;
        this.xaeroSamplingApplied[0] = false;
    }

    private void closeTextures() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i <= MAX_MIP_LEVEL; i++) {
            if (this.textureIds[i] != null) {
                mc.getTextureManager().release(this.textureIds[i]);
            }
            try { if (this.textures[i] != null) this.textures[i].close(); } catch (Exception ignored) {}
            if (i > 0) {
                try { if (this.mipImages[i] != null) this.mipImages[i].close(); } catch (Exception ignored) {}
            }
            this.textures[i] = null;
            this.textureIds[i] = null;
            this.mipImages[i] = null;
            this.textureDirty[i] = false;
            this.xaeroSamplingApplied[i] = false;
        }
    }

    private static int chooseMipLevel(float renderScale) {
        if (renderScale < 0.0625f) return 3;
        if (renderScale < 0.125f) return 2;
        if (renderScale < 0.25f) return 1;
        return 0;
    }

    public static int chunkToTileCoord(int chunkCoord) {
        return Math.floorDiv(chunkCoord, TILE_SIZE);
    }

    public static int tileToChunkCoord(int tileCoord) {
        return tileCoord * TILE_SIZE;
    }
}
