package com.rfizzle.respite;

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
        LOGGER.info("Respite initialized — make the night count.");
    }
}
