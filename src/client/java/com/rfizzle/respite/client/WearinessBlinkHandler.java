package com.rfizzle.respite.client;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.weariness.BlinkMath;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

/**
 * The Exhausted eyelid blink ({@code design/SPEC.md} §4.4): while the local
 * player carries {@code respite:exhausted}, the screen darkens from top and
 * bottom for about half a second every 90s (±30s jitter), peaking at 55%
 * occlusion — never full black. Purely cosmetic: no gameplay effect, no sound,
 * hidden with F1, and switchable off with the client {@code showExhaustionBlink}
 * toggle.
 *
 * <p>Client-thread only, and entirely local — the trigger is the already-synced
 * {@code respite:exhausted} effect on {@link LocalPlayer}, so nothing is
 * networked for this. {@link BlinkMath} owns the timing; this class owns the
 * live state and the draw. Combat suppression watches two local signals: the
 * player's health dropping (took damage) and an attack that plausibly lands
 * damage on a living target (dealt damage); a blink due inside the 10s window
 * is deferred, not skipped.
 */
public final class WearinessBlinkHandler {

    private static final Random JITTER = new Random();

    private static long clientTick;
    /** Scheduled tick of the next blink, or {@code -1} when unscheduled. */
    private static long nextBlinkTick = -1;
    /** Tick the current blink started, or {@code -1} when no blink is running. */
    private static long blinkStartTick = -1;
    /** Tick of the last observed combat event, or {@code -1} for none this session. */
    private static long lastCombatTick = -1;
    /** Previous tick's health, for edge-detecting damage taken. */
    private static float lastHealth = Float.NaN;

    private WearinessBlinkHandler() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(WearinessBlinkHandler::onClientTick);
        HudRenderCallback.EVENT.register(WearinessBlinkHandler::onHudRender);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            // The callback fires on the attack input against any entity, before
            // damage resolves — so arm the window only for a hit that plausibly
            // lands damage on a living target (§4.4).
            if (entity instanceof LivingEntity target
                    && BlinkMath.attackDealsDamage(
                            target.isAlive(), target.isAttackable(), target.isInvulnerable())) {
                lastCombatTick = clientTick;
            }
            return InteractionResult.PASS;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        clientTick = 0;
        nextBlinkTick = -1;
        blinkStartTick = -1;
        lastCombatTick = -1;
        lastHealth = Float.NaN;
    }

    private static void onClientTick(Minecraft client) {
        clientTick++;
        LocalPlayer player = client.player;
        if (player == null) {
            unschedule();
            return;
        }
        // Damage taken: any drop in synced health arms the combat window.
        float health = player.getHealth();
        if (!Float.isNaN(lastHealth) && health < lastHealth) {
            lastCombatTick = clientTick;
        }
        lastHealth = health;

        if (!player.hasEffect(RespiteRegistry.EXHAUSTED) || !RespiteConfig.get().showExhaustionBlink) {
            unschedule();
            return;
        }
        if (nextBlinkTick < 0) {
            nextBlinkTick = clientTick + BlinkMath.nextInterval(JITTER.nextDouble());
        }
        if (blinkStartTick >= 0 && BlinkMath.isBlinkDone(clientTick - blinkStartTick)) {
            blinkStartTick = -1;
        }
        if (BlinkMath.shouldStartBlink(clientTick, nextBlinkTick, lastCombatTick, blinkStartTick >= 0)) {
            blinkStartTick = clientTick;
            nextBlinkTick = clientTick + BlinkMath.nextInterval(JITTER.nextDouble());
        }
    }

    private static void unschedule() {
        nextBlinkTick = -1;
        blinkStartTick = -1;
    }

    private static void onHudRender(GuiGraphics graphics, DeltaTracker delta) {
        if (blinkStartTick < 0) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) {
            return; // F1
        }
        if (!RespiteConfig.get().showExhaustionBlink) {
            return;
        }
        float occlusion = BlinkMath.occlusionAt(clientTick - blinkStartTick);
        if (occlusion <= 0.0f) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        // Occlusion is the fraction of screen height the lids cover between them,
        // split evenly top and bottom, so the darkest moment leaves a clear band
        // in the middle — vision is never lost.
        int lid = Math.round(occlusion * height * 0.5f);
        if (lid <= 0) {
            return;
        }
        int edge = 0xF2000000; // near-opaque black at the screen edge
        int inner = 0x00000000; // fading to clear toward the centre
        graphics.fillGradient(0, 0, width, lid, edge, inner);
        graphics.fillGradient(0, height - lid, width, height, inner, edge);
        graphics.flush(); // commit before a batching optimiser can drop the fill
    }
}
