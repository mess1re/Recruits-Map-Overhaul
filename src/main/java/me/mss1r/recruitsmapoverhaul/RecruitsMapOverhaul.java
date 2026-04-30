package me.mss1r.recruitsmapoverhaul;

import com.mojang.logging.LogUtils;
import me.mss1r.recruitsmapoverhaul.client.MapOverhaulClientEvents;
import me.mss1r.recruitsmapoverhaul.config.RecruitsMapOverhaulClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RecruitsMapOverhaul.MOD_ID)
public final class RecruitsMapOverhaul {
    public static final String MOD_ID = "recruitsmapoverhaul";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RecruitsMapOverhaul(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, RecruitsMapOverhaulClientConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                MinecraftForge.EVENT_BUS.register(new MapOverhaulClientEvents()));
    }
}
