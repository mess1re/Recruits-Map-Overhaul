package me.mss1r.recruitsmapoverhaul.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MapBlockColorResolver {
    private static final MapTintSampler TINT_SAMPLER = new MapTintSampler();
    private static final Map<BlockState, TextureColor> TEXTURE_COLOR_CACHE = new HashMap<>();

    private MapBlockColorResolver() {
    }

    static int resolveBaseRgb(ClientLevel level, BlockPos pos, BlockState state) {
        MapColor mapColor = state.getMapColor(level, pos);
        int mapRgb = mapColor != null ? mapColor.col : 0;
        if (mapRgb == 0) {
            return -1;
        }

        TextureColor textureColor = getTextureColor(state);
        int base = textureColor.rgb() != 0 ? textureColor.rgb() : mapRgb;
        int tint = resolveBiomeTint(level, pos, state, textureColor.tintIndex());
        if (tint == -1 || (tint & 0x00FFFFFF) == 0) {
            return base;
        }

        return multiplyBiomeTint(base, tint);
    }

    static int resolveWaterRgb(ClientLevel level, BlockPos pos, BlockState state) {
        int waterRgb = resolveBaseRgb(level, pos, state);
        MapSample floor = MapStateSampler.findUnderWaterSample(level, pos);
        if (floor == null) {
            return waterRgb;
        }

        int floorRgb = resolveBaseRgb(level, floor.pos(), floor.state());
        if ((floorRgb & 0x00FFFFFF) == 0) {
            return waterRgb;
        }

        int depth = Math.max(1, pos.getY() - floor.height());
        float waterAlpha = clamp(0.46f + Math.min(depth, 6) * 0.075f, 0.54f, 0.86f);
        return blendRgb(floorRgb, waterRgb, waterAlpha);
    }

    static int applyBrightnessToNativeColor(int rgb, float brightness) {
        return applyBrightnessToNativeColor(rgb, ColorMultiplier.uniform(brightness));
    }

    static int applyBrightnessToNativeColor(int rgb, ColorMultiplier brightness) {
        int red = clampColor(Math.round(((rgb >> 16) & 0xFF) * brightness.red()));
        int green = clampColor(Math.round(((rgb >> 8) & 0xFF) * brightness.green()));
        int blue = clampColor(Math.round((rgb & 0xFF) * brightness.blue()));

        // NativeImage RGBA pixels are stored in ABGR byte order.
        return 0xFF000000 | (blue << 16) | (green << 8) | red;
    }

    private static int resolveBiomeTint(ClientLevel level, BlockPos pos, BlockState state, int tintIndex) {
        if (tintIndex < 0) {
            return -1;
        }

        BlockColors blockColors = Minecraft.getInstance().getBlockColors();
        return blockColors.getColor(state, TINT_SAMPLER.use(level), pos, tintIndex);
    }

    private static TextureColor getTextureColor(BlockState state) {
        TextureColor cached = TEXTURE_COLOR_CACHE.get(state);
        if (cached != null) {
            return cached;
        }

        TextureColor result;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            BakedModel model = minecraft.getBlockRenderer().getBlockModelShaper().getBlockModel(state);
            List<BakedQuad> upQuads = model.getQuads(state, Direction.UP, RandomSource.create(42L), ModelData.EMPTY, null);
            result = !upQuads.isEmpty()
                    ? averageSprite(upQuads.get(0).getSprite(), upQuads.get(0).getTintIndex())
                    : averageSprite(model.getParticleIcon(ModelData.EMPTY), 0);
        } catch (RuntimeException ignored) {
            result = TextureColor.EMPTY;
        }

        TEXTURE_COLOR_CACHE.put(state, result);
        return result;
    }

    private static TextureColor averageSprite(TextureAtlasSprite sprite, int tintIndex) {
        if (sprite == null || sprite.contents() == null) {
            return TextureColor.EMPTY;
        }

        int width = sprite.contents().width();
        int height = sprite.contents().height();
        int size = Math.min(width, height);
        if (size <= 0) {
            return TextureColor.EMPTY;
        }

        int step = Math.max(1, Math.min(4, size / 8));
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        long count = 0L;

        for (int y = 0; y < size; y += step) {
            for (int x = 0; x < size; x += step) {
                int abgr = sprite.getPixelRGBA(0, x, y);
                int alpha = (abgr >> 24) & 0xFF;
                if (alpha == 0 || (abgr & 0x00FFFFFF) == 0) {
                    continue;
                }

                blue += (abgr >> 16) & 0xFF;
                green += (abgr >> 8) & 0xFF;
                red += abgr & 0xFF;
                count++;
            }
        }

        if (count == 0L) {
            return TextureColor.EMPTY;
        }

        int rgb = ((int) (red / count) << 16) | ((int) (green / count) << 8) | (int) (blue / count);
        return new TextureColor(rgb, tintIndex);
    }

    private static int multiplyBiomeTint(int base, int tint) {
        int red = (((base >> 16) & 0xFF) * ((tint >> 16) & 0xFF)) / 255;
        int green = (((base >> 8) & 0xFF) * ((tint >> 8) & 0xFF)) / 255;
        int blue = ((base & 0xFF) * (tint & 0xFF)) / 255;
        return (red << 16) | (green << 8) | blue;
    }

    static int blendRgb(int bottom, int top, float topAlpha) {
        float bottomAlpha = 1.0f - topAlpha;
        int red = clampColor(Math.round(((bottom >> 16) & 0xFF) * bottomAlpha + ((top >> 16) & 0xFF) * topAlpha));
        int green = clampColor(Math.round(((bottom >> 8) & 0xFF) * bottomAlpha + ((top >> 8) & 0xFF) * topAlpha));
        int blue = clampColor(Math.round((bottom & 0xFF) * bottomAlpha + (top & 0xFF) * topAlpha));
        return (red << 16) | (green << 8) | blue;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
