package com.rfizzle.respite;

import com.rfizzle.respite.advancement.RespiteCriteria;
import com.rfizzle.respite.bedroll.Bedroll;
import com.rfizzle.respite.command.RespiteCommand;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.config.RespiteConfigSync;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.restful.RestfulSleepHandler;
import com.rfizzle.respite.sleepvote.SleepVoteHandler;
import com.rfizzle.respite.timelapse.TimeLapseEngine;
import com.rfizzle.respite.weariness.WearinessHandler;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Respite implements ModInitializer {
    public static final String MOD_ID = "respite";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Canonical factory for this mod's resource locations — never inline the mod id. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        // Eager load: first launch writes config/respite.json with defaults.
        RespiteConfig.get();

        // Server→client config sync: registers the S2C payload type (both sides)
        // and pushes the server's config to each client on join.
        RespiteConfigSync.register();

        RespiteRegistry.register();

        // Advancement criteria must register before any advancement JSON loads
        // on datapack load — an unknown trigger id fails the whole advancement.
        RespiteCriteria.register();

        TimeLapseEngine.register();

        RestfulSleepHandler.register();

        SleepVoteHandler.register();

        Bedroll.register();

        WearinessHandler.register();

        RespiteCommand.register();

        LOGGER.info("Respite initialized — make the night count.");
    }
}
