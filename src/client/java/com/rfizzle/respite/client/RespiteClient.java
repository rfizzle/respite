package com.rfizzle.respite.client;

import com.rfizzle.respite.client.chronometer.PocketChronometerTooltip;
import net.fabricmc.api.ClientModInitializer;

public class RespiteClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Respite draws no HUD element by design (design/DESIGN.md §2); its client
        // surfaces are transient — action-bar lines, the lapse-edge cues, and the
        // Exhausted eyelid blink (§4.4), a cosmetic screen fade, not a HUD slot.
        // Receive the server's authoritative config on join / reload, so a
        // connected client honors the server's rules over its own local file.
        ClientRespiteConfig.register();
        TimeLapseClientHandler.register();
        // Glide the sky at the live time-lapse rate instead of snapping each tick.
        TimeLapseSkySmoother.register();
        WearinessBlinkHandler.register();
        BedrollSleepClientHandler.register();
        // The pocket chronometer's tooltip needs the live client level for the hour
        // and moon, which only client code may read (design/SPEC.md §5).
        PocketChronometerTooltip.register();
    }
}
