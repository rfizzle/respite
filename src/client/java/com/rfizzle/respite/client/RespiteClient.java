package com.rfizzle.respite.client;

import net.fabricmc.api.ClientModInitializer;

public class RespiteClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Respite draws no HUD element by design (design/DESIGN.md §2); its client
        // surfaces are transient — action-bar lines, the lapse-edge cues, and the
        // Exhausted eyelid blink (§4.4), a cosmetic screen fade, not a HUD slot.
        TimeLapseClientHandler.register();
        WearinessBlinkHandler.register();
        BedrollSleepClientHandler.register();
    }
}
