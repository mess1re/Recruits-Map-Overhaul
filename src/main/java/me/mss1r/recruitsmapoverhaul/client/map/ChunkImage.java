package me.mss1r.recruitsmapoverhaul.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

public class ChunkImage {
    private final NativeImage image;

    public ChunkImage(ClientLevel level, ChunkPos pos) {
        this.image = generateChunkImage(level, pos);
    }

    public static int sampleMapColor(ClientLevel level, int worldX, int worldZ) {
        MapSample sample = MapStateSampler.findTopMapSample(level, worldX, worldZ);
        if (sample == null) {
            return 0x00000000;
        }

        return resolveXaeroStyleColor(level, sample);
    }

    public NativeImage getNativeImage() {
        return this.image;
    }

    public boolean isMeaningful() {
        if (this.image == null) {
            return false;
        }

        int meaningful = 0;
        for (int i = 0; i < 256; i++) {
            int pixel = this.image.getPixelRGBA(i % 16, i / 16);
            int alpha = (pixel >> 24) & 0xFF;
            int rgb = pixel & 0x00FFFFFF;
            if (alpha > 0 && rgb != 0) {
                meaningful++;
            }
        }

        return meaningful >= 25;
    }

    public void close() {
        try {
            if (image != null) {
                image.close();
            }
        } catch (Exception ignored) {
        }
    }

    private NativeImage generateChunkImage(ClientLevel level, ChunkPos pos) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, 16, 16, true);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = pos.getMinBlockX() + x;
                int worldZ = pos.getMinBlockZ() + z;
                img.setPixelRGBA(x, z, sampleMapColor(level, worldX, worldZ));
            }
        }
        img.untrack();
        return img;
    }

    private static int resolveXaeroStyleColor(ClientLevel level, MapSample sample) {
        BlockPos pos = sample.pos();
        BlockState state = sample.state();
        boolean water = MapStateSampler.isWaterLike(state);
        int rgb = water
                ? MapBlockColorResolver.resolveWaterRgb(level, pos, state)
                : MapBlockColorResolver.resolveBaseRgb(level, pos, state);
        if ((rgb & 0x00FFFFFF) == 0) {
            return 0x00000000;
        }

        if (water) {
            return MapBlockColorResolver.applyBrightnessToNativeColor(
                    rgb,
                    MapReliefShading.computeWaterBrightness(level, pos)
            );
        }

        return MapBlockColorResolver.applyBrightnessToNativeColor(
                rgb,
                MapReliefShading.computeLandBrightness(level, sample)
        );
    }
}
