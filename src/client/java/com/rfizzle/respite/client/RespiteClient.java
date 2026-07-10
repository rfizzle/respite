package com.rfizzle.respite.client;

import net.fabricmc.api.ClientModInitializer;

public class RespiteClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Respite draws no HUD element by design (design/DESIGN.md §2); client wiring
        // arrives with the first client-facing feature.
    }
}
