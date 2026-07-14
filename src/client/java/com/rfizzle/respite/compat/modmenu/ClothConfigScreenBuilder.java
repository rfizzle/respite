package com.rfizzle.respite.compat.modmenu;

import com.rfizzle.respite.client.ClientRespiteConfig;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.config.RespiteConfig.DoubleBound;
import com.rfizzle.respite.config.RespiteConfig.IntBound;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth config screen — every option from the spec's Configuration table,
 * grouped by feature. Referenced only when Cloth Config is loaded
 * (see {@link ModMenuIntegration}), so its Cloth imports never resolve without it.
 *
 * <p>When the client is connected to a remote server, the server-authoritative
 * gameplay fields are shown <em>read-only</em>, seeded from the server's synced
 * config ({@link ClientRespiteConfig}) — the client can't override a rule the
 * server owns; an admin edits the server config and runs {@code /respite reload}.
 * The client-only presentation toggles stay editable in every case, and in
 * singleplayer (or from the title screen) every field is editable against the
 * local file, which is authoritative there.
 */
final class ClothConfigScreenBuilder {

    static Screen build(Screen parent) {
        RespiteConfig config = RespiteConfig.get();
        RespiteConfig defaults = new RespiteConfig();
        // The screen edits a working copy; publish() clamps, persists, and atomically
        // swaps it in, so a concurrent reader never observes a half-applied edit.
        RespiteConfig working = config.copy();

        // On a remote server the gameplay keys are the server's to set; show its
        // synced values read-only. Singleplayer/title screen edits the local file.
        RespiteConfig server = ClientRespiteConfig.serverConfig();
        boolean serverControlled = server != null && connectedToRemoteServer();
        // Source for gameplay-field display: the server's values when it owns them.
        RespiteConfig gameplay = serverControlled ? server : config;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.respite.title"))
                .setSavingRunnable(() -> RespiteConfig.publish(working));

        ConfigEntryBuilder entry = builder.entryBuilder();

        // --- Time-lapse (§1) ---
        ConfigCategory timeLapse = builder.getOrCreateCategory(Component.translatable("config.respite.category.time_lapse"));
        serverNote(timeLapse, entry, serverControlled);
        bool(timeLapse, entry, "config.respite.enableTimeLapse", gameplay.enableTimeLapse, defaults.enableTimeLapse,
                serverControlled, v -> working.enableTimeLapse = v);
        intField(timeLapse, entry, "config.respite.maxTimeLapseRate", gameplay.maxTimeLapseRate, defaults.maxTimeLapseRate,
                RespiteConfig.Bounds.MAX_TIME_LAPSE_RATE, serverControlled, v -> working.maxTimeLapseRate = v);
        intField(timeLapse, entry, "config.respite.timeLapseTickBudgetMs", gameplay.timeLapseTickBudgetMs, defaults.timeLapseTickBudgetMs,
                RespiteConfig.Bounds.TIME_LAPSE_TICK_BUDGET_MS, serverControlled, v -> working.timeLapseTickBudgetMs = v);
        bool(timeLapse, entry, "config.respite.combatHoldsTime", gameplay.combatHoldsTime, defaults.combatHoldsTime,
                serverControlled, v -> working.combatHoldsTime = v);
        bool(timeLapse, entry, "config.respite.announceTimeLapse", gameplay.announceTimeLapse, defaults.announceTimeLapse,
                serverControlled, v -> working.announceTimeLapse = v);
        bool(timeLapse, entry, "config.respite.announceSleepVote", gameplay.announceSleepVote, defaults.announceSleepVote,
                serverControlled, v -> working.announceSleepVote = v);
        bool(timeLapse, entry, "config.respite.excludeIdleFromShare", gameplay.excludeIdleFromShare, defaults.excludeIdleFromShare,
                serverControlled, v -> working.excludeIdleFromShare = v);
        intField(timeLapse, entry, "config.respite.idleThresholdMinutes", gameplay.idleThresholdMinutes, defaults.idleThresholdMinutes,
                RespiteConfig.Bounds.IDLE_THRESHOLD_MINUTES, serverControlled, v -> working.idleThresholdMinutes = v);

        // --- Restful saturation (§2) ---
        ConfigCategory restful = builder.getOrCreateCategory(Component.translatable("config.respite.category.restful"));
        serverNote(restful, entry, serverControlled);
        bool(restful, entry, "config.respite.enableRestfulSaturation", gameplay.enableRestfulSaturation, defaults.enableRestfulSaturation,
                serverControlled, v -> working.enableRestfulSaturation = v);
        bool(restful, entry, "config.respite.restfulRequiresFullHunger", gameplay.restfulRequiresFullHunger, defaults.restfulRequiresFullHunger,
                serverControlled, v -> working.restfulRequiresFullHunger = v);
        intField(restful, entry, "config.respite.restfulHealIntervalTicks", gameplay.restfulHealIntervalTicks, defaults.restfulHealIntervalTicks,
                RespiteConfig.Bounds.RESTFUL_HEAL_INTERVAL_TICKS, serverControlled, v -> working.restfulHealIntervalTicks = v);
        doubleField(restful, entry, "config.respite.newMoonHealMultiplier", gameplay.newMoonHealMultiplier, defaults.newMoonHealMultiplier,
                RespiteConfig.Bounds.NEW_MOON_HEAL_MULTIPLIER, serverControlled, v -> working.newMoonHealMultiplier = v);

        // --- Phantoms (§3) ---
        ConfigCategory phantoms = builder.getOrCreateCategory(Component.translatable("config.respite.category.phantoms"));
        serverNote(phantoms, entry, serverControlled);
        bool(phantoms, entry, "config.respite.enablePhantomRework", gameplay.enablePhantomRework, defaults.enablePhantomRework,
                serverControlled, v -> working.enablePhantomRework = v);
        intField(phantoms, entry, "config.respite.phantomAltitudeMin", gameplay.phantomAltitudeMin, defaults.phantomAltitudeMin,
                RespiteConfig.Bounds.PHANTOM_ALTITUDE_MIN, serverControlled, v -> working.phantomAltitudeMin = v);
        bool(phantoms, entry, "config.respite.phantomNewMoon", gameplay.phantomNewMoon, defaults.phantomNewMoon,
                serverControlled, v -> working.phantomNewMoon = v);

        // --- Weariness (§4) ---
        ConfigCategory weariness = builder.getOrCreateCategory(Component.translatable("config.respite.category.weariness"));
        serverNote(weariness, entry, serverControlled);
        bool(weariness, entry, "config.respite.enableWeariness", gameplay.enableWeariness, defaults.enableWeariness,
                serverControlled, v -> working.enableWeariness = v);
        intField(weariness, entry, "config.respite.wearinessThresholdDays", gameplay.wearinessThresholdDays, defaults.wearinessThresholdDays,
                RespiteConfig.Bounds.WEARINESS_THRESHOLD_DAYS, serverControlled, v -> working.wearinessThresholdDays = v);
        doubleField(weariness, entry, "config.respite.wearinessRegenPenalty", gameplay.wearinessRegenPenalty, defaults.wearinessRegenPenalty,
                RespiteConfig.Bounds.WEARINESS_REGEN_PENALTY, serverControlled, v -> working.wearinessRegenPenalty = v);
        intField(weariness, entry, "config.respite.exhaustedThresholdDays", gameplay.exhaustedThresholdDays, defaults.exhaustedThresholdDays,
                RespiteConfig.Bounds.EXHAUSTED_THRESHOLD_DAYS, serverControlled, v -> working.exhaustedThresholdDays = v);
        doubleField(weariness, entry, "config.respite.exhaustedRegenPenalty", gameplay.exhaustedRegenPenalty, defaults.exhaustedRegenPenalty,
                RespiteConfig.Bounds.EXHAUSTED_REGEN_PENALTY, serverControlled, v -> working.exhaustedRegenPenalty = v);
        bool(weariness, entry, "config.respite.enableWellRested", gameplay.enableWellRested, defaults.enableWellRested,
                serverControlled, v -> working.enableWellRested = v);
        intField(weariness, entry, "config.respite.wellRestedSeconds", gameplay.wellRestedSeconds, defaults.wellRestedSeconds,
                RespiteConfig.Bounds.WELL_RESTED_SECONDS, serverControlled, v -> working.wellRestedSeconds = v);
        doubleField(weariness, entry, "config.respite.wellRestedRegenBonus", gameplay.wellRestedRegenBonus, defaults.wellRestedRegenBonus,
                RespiteConfig.Bounds.WELL_RESTED_REGEN_BONUS, serverControlled, v -> working.wellRestedRegenBonus = v);

        // --- Chronometer (§5) ---
        ConfigCategory chronometer = builder.getOrCreateCategory(Component.translatable("config.respite.category.chronometer"));
        serverNote(chronometer, entry, serverControlled);
        bool(chronometer, entry, "config.respite.enableChronometer", gameplay.enableChronometer, defaults.enableChronometer,
                serverControlled, v -> working.enableChronometer = v);

        // --- Caffeinated brew (§6) ---
        ConfigCategory brew = builder.getOrCreateCategory(Component.translatable("config.respite.category.brew"));
        serverNote(brew, entry, serverControlled);
        bool(brew, entry, "config.respite.enableCaffeinatedBrew", gameplay.enableCaffeinatedBrew, defaults.enableCaffeinatedBrew,
                serverControlled, v -> working.enableCaffeinatedBrew = v);
        intField(brew, entry, "config.respite.brewHasteSeconds", gameplay.brewHasteSeconds, defaults.brewHasteSeconds,
                RespiteConfig.Bounds.BREW_HASTE_SECONDS, serverControlled, v -> working.brewHasteSeconds = v);

        // --- Bedroll (§7) ---
        ConfigCategory bedroll = builder.getOrCreateCategory(Component.translatable("config.respite.category.bedroll"));
        serverNote(bedroll, entry, serverControlled);
        bool(bedroll, entry, "config.respite.enableBedroll", gameplay.enableBedroll, defaults.enableBedroll,
                serverControlled, v -> working.enableBedroll = v);
        doubleField(bedroll, entry, "config.respite.bedrollRestfulMultiplier", gameplay.bedrollRestfulMultiplier, defaults.bedrollRestfulMultiplier,
                RespiteConfig.Bounds.BEDROLL_RESTFUL_MULTIPLIER, serverControlled, v -> working.bedrollRestfulMultiplier = v);

        // --- Client --- (presentation toggles: always the client's own, always editable)
        ConfigCategory client = builder.getOrCreateCategory(Component.translatable("config.respite.category.client"));
        bool(client, entry, "config.respite.showTimeLapseMessages", config.showTimeLapseMessages, defaults.showTimeLapseMessages,
                false, v -> working.showTimeLapseMessages = v);
        bool(client, entry, "config.respite.showExhaustionBlink", config.showExhaustionBlink, defaults.showExhaustionBlink,
                false, v -> working.showExhaustionBlink = v);

        return builder.build();
    }

    /** True when connected to a separate server (not the integrated singleplayer one). */
    private static boolean connectedToRemoteServer() {
        Minecraft client = Minecraft.getInstance();
        return client.getConnection() != null && !client.hasSingleplayerServer();
    }

    /** A one-line read-only banner atop a gameplay category while the server owns its values. */
    private static void serverNote(ConfigCategory category, ConfigEntryBuilder entry, boolean serverControlled) {
        if (serverControlled) {
            category.addEntry(entry.startTextDescription(
                    Component.translatable("config.respite.server_controlled_note").withStyle(ChatFormatting.GRAY))
                    .build());
        }
    }

    /** A boolean field: an editable toggle, or a read-only line when {@code readOnly}. */
    private static void bool(ConfigCategory category, ConfigEntryBuilder entry, String key,
            boolean value, boolean defaultValue, boolean readOnly, Consumer<Boolean> save) {
        if (readOnly) {
            readOnlyLine(category, entry, key, String.valueOf(value));
            return;
        }
        category.addEntry(entry.startBooleanToggle(Component.translatable(key), value)
                .setDefaultValue(defaultValue)
                .setTooltip(Component.translatable(key + ".tooltip"))
                .setSaveConsumer(save)
                .build());
    }

    /** An int field, bounded by {@code bound}; or a read-only line when {@code readOnly}. */
    private static void intField(ConfigCategory category, ConfigEntryBuilder entry, String key,
            int value, int defaultValue, IntBound bound, boolean readOnly, Consumer<Integer> save) {
        if (readOnly) {
            readOnlyLine(category, entry, key, String.valueOf(value));
            return;
        }
        category.addEntry(entry.startIntField(Component.translatable(key), value)
                .setDefaultValue(defaultValue)
                .setMin(bound.min()).setMax(bound.max())
                .setTooltip(Component.translatable(key + ".tooltip"))
                .setSaveConsumer(save)
                .build());
    }

    /** A double field, bounded by {@code bound}; or a read-only line when {@code readOnly}. */
    private static void doubleField(ConfigCategory category, ConfigEntryBuilder entry, String key,
            double value, double defaultValue, DoubleBound bound, boolean readOnly, Consumer<Double> save) {
        if (readOnly) {
            readOnlyLine(category, entry, key, String.valueOf(value));
            return;
        }
        category.addEntry(entry.startDoubleField(Component.translatable(key), value)
                .setDefaultValue(defaultValue)
                .setMin(bound.min()).setMax(bound.max())
                .setTooltip(Component.translatable(key + ".tooltip"))
                .setSaveConsumer(save)
                .build());
    }

    /** Renders a field's label and its server value as a static, non-editable line. */
    private static void readOnlyLine(ConfigCategory category, ConfigEntryBuilder entry, String key, String value) {
        Component line = Component.translatable(key)
                .append(Component.literal(": " + value).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY);
        category.addEntry(entry.startTextDescription(line)
                .setTooltip(Component.translatable(key + ".tooltip"))
                .build());
    }

    private ClothConfigScreenBuilder() {
    }
}
