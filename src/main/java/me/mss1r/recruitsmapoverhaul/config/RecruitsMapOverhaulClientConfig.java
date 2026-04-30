package me.mss1r.recruitsmapoverhaul.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RecruitsMapOverhaulClientConfig {
    public enum PlayerArrowStyle {
        OVERHAULED,
        VANILLA
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<PlayerArrowStyle> PLAYER_ARROW_STYLE;
    public static final ForgeConfigSpec.BooleanValue SHOW_COORDINATES;
    public static final ForgeConfigSpec.BooleanValue SHOW_FPS_OVERLAY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("map");
        PLAYER_ARROW_STYLE = builder
                .comment("Player arrow style on the Recruits world map. VANILLA matches the original Recruits map icon.")
                .defineEnum("playerArrowStyle", PlayerArrowStyle.OVERHAULED);
        SHOW_COORDINATES = builder
                .comment("Show X/Y/Z coordinates in the bottom map readout. Zoom is still shown when disabled.")
                .define("showCoordinates", true);
        SHOW_FPS_OVERLAY = builder
                .comment("Show the FPS counter in the top-right corner of the Recruits world map.")
                .define("showFpsOverlay", true);
        builder.pop();

        SPEC = builder.build();
    }

    private RecruitsMapOverhaulClientConfig() {
    }
}
