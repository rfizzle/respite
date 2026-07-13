package com.rfizzle.respite.client.chronometer;

import com.rfizzle.respite.chronometer.PocketChronometer;
import com.rfizzle.respite.registry.RespiteRegistry;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Renders the pocket chronometer's tooltip. The hour, moon phase, and new-moon
 * countdown are vanilla-synced client state read live off the client level, so
 * the clock stays smooth at zero server cost; the holder's days awake rides the
 * stack in {@link RespiteRegistry#AWAKE_TICKS}, written server-side.
 *
 * <p>Deliberately an {@link ItemTooltipCallback} rather than an
 * {@code Item#appendHoverText} override (the {@code mc-tooltips} default): the
 * tooltip context carries no day time, so a live clock needs the client level,
 * which only client code may touch. The reading is inserted just below the item
 * name so it still sits above any recipe-viewer footer a callback would otherwise
 * fall beneath.
 */
public final class PocketChronometerTooltip {

    private PocketChronometerTooltip() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!stack.is(RespiteRegistry.POCKET_CHRONOMETER)) {
                return;
            }
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            List<Component> reading = PocketChronometer.tooltip(
                    level.getDayTime(),
                    level.dimensionType().hasFixedTime(),
                    level.getMoonPhase(),
                    stack.getOrDefault(RespiteRegistry.AWAKE_TICKS, 0));
            lines.addAll(1, reading);
        });
    }
}
