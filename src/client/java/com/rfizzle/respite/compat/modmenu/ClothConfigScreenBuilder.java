package com.rfizzle.respite.compat.modmenu;

import com.rfizzle.respite.config.RespiteConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth config screen — every option from the spec's Configuration table,
 * grouped by feature. Referenced only when Cloth Config is loaded
 * (see {@link ModMenuIntegration}), so its Cloth imports never resolve without it.
 */
final class ClothConfigScreenBuilder {

    static Screen build(Screen parent) {
        RespiteConfig config = RespiteConfig.get();
        RespiteConfig defaults = new RespiteConfig();
        // The screen edits a working copy; publish() clamps, persists, and atomically
        // swaps it in, so a concurrent reader never observes a half-applied edit.
        RespiteConfig working = config.copy();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.respite.title"))
                .setSavingRunnable(() -> RespiteConfig.publish(working));

        ConfigEntryBuilder entry = builder.entryBuilder();

        // --- Time-lapse (§1) ---
        ConfigCategory timeLapse = builder.getOrCreateCategory(Component.translatable("config.respite.category.time_lapse"));
        timeLapse.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.enableTimeLapse"), config.enableTimeLapse)
                .setDefaultValue(defaults.enableTimeLapse)
                .setTooltip(Component.translatable("config.respite.enableTimeLapse.tooltip"))
                .setSaveConsumer(v -> working.enableTimeLapse = v)
                .build());
        timeLapse.addEntry(entry.startIntField(Component.translatable("config.respite.maxTimeLapseRate"), config.maxTimeLapseRate)
                .setDefaultValue(defaults.maxTimeLapseRate)
                .setMin(RespiteConfig.Bounds.MAX_TIME_LAPSE_RATE.min()).setMax(RespiteConfig.Bounds.MAX_TIME_LAPSE_RATE.max())
                .setTooltip(Component.translatable("config.respite.maxTimeLapseRate.tooltip"))
                .setSaveConsumer(v -> working.maxTimeLapseRate = v)
                .build());
        timeLapse.addEntry(entry.startIntField(Component.translatable("config.respite.timeLapseTickBudgetMs"), config.timeLapseTickBudgetMs)
                .setDefaultValue(defaults.timeLapseTickBudgetMs)
                .setMin(RespiteConfig.Bounds.TIME_LAPSE_TICK_BUDGET_MS.min()).setMax(RespiteConfig.Bounds.TIME_LAPSE_TICK_BUDGET_MS.max())
                .setTooltip(Component.translatable("config.respite.timeLapseTickBudgetMs.tooltip"))
                .setSaveConsumer(v -> working.timeLapseTickBudgetMs = v)
                .build());
        timeLapse.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.combatHoldsTime"), config.combatHoldsTime)
                .setDefaultValue(defaults.combatHoldsTime)
                .setTooltip(Component.translatable("config.respite.combatHoldsTime.tooltip"))
                .setSaveConsumer(v -> working.combatHoldsTime = v)
                .build());
        timeLapse.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.announceTimeLapse"), config.announceTimeLapse)
                .setDefaultValue(defaults.announceTimeLapse)
                .setTooltip(Component.translatable("config.respite.announceTimeLapse.tooltip"))
                .setSaveConsumer(v -> working.announceTimeLapse = v)
                .build());
        timeLapse.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.announceSleepVote"), config.announceSleepVote)
                .setDefaultValue(defaults.announceSleepVote)
                .setTooltip(Component.translatable("config.respite.announceSleepVote.tooltip"))
                .setSaveConsumer(v -> working.announceSleepVote = v)
                .build());
        timeLapse.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.excludeIdleFromShare"), config.excludeIdleFromShare)
                .setDefaultValue(defaults.excludeIdleFromShare)
                .setTooltip(Component.translatable("config.respite.excludeIdleFromShare.tooltip"))
                .setSaveConsumer(v -> working.excludeIdleFromShare = v)
                .build());
        timeLapse.addEntry(entry.startIntField(Component.translatable("config.respite.idleThresholdMinutes"), config.idleThresholdMinutes)
                .setDefaultValue(defaults.idleThresholdMinutes)
                .setMin(RespiteConfig.Bounds.IDLE_THRESHOLD_MINUTES.min()).setMax(RespiteConfig.Bounds.IDLE_THRESHOLD_MINUTES.max())
                .setTooltip(Component.translatable("config.respite.idleThresholdMinutes.tooltip"))
                .setSaveConsumer(v -> working.idleThresholdMinutes = v)
                .build());

        // --- Restful saturation (§2) ---
        ConfigCategory restful = builder.getOrCreateCategory(Component.translatable("config.respite.category.restful"));
        restful.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.enableRestfulSaturation"), config.enableRestfulSaturation)
                .setDefaultValue(defaults.enableRestfulSaturation)
                .setTooltip(Component.translatable("config.respite.enableRestfulSaturation.tooltip"))
                .setSaveConsumer(v -> working.enableRestfulSaturation = v)
                .build());
        restful.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.restfulRequiresFullHunger"), config.restfulRequiresFullHunger)
                .setDefaultValue(defaults.restfulRequiresFullHunger)
                .setTooltip(Component.translatable("config.respite.restfulRequiresFullHunger.tooltip"))
                .setSaveConsumer(v -> working.restfulRequiresFullHunger = v)
                .build());
        restful.addEntry(entry.startIntField(Component.translatable("config.respite.restfulHealIntervalTicks"), config.restfulHealIntervalTicks)
                .setDefaultValue(defaults.restfulHealIntervalTicks)
                .setMin(RespiteConfig.Bounds.RESTFUL_HEAL_INTERVAL_TICKS.min()).setMax(RespiteConfig.Bounds.RESTFUL_HEAL_INTERVAL_TICKS.max())
                .setTooltip(Component.translatable("config.respite.restfulHealIntervalTicks.tooltip"))
                .setSaveConsumer(v -> working.restfulHealIntervalTicks = v)
                .build());
        restful.addEntry(entry.startDoubleField(Component.translatable("config.respite.newMoonHealMultiplier"), config.newMoonHealMultiplier)
                .setDefaultValue(defaults.newMoonHealMultiplier)
                .setMin(RespiteConfig.Bounds.NEW_MOON_HEAL_MULTIPLIER.min()).setMax(RespiteConfig.Bounds.NEW_MOON_HEAL_MULTIPLIER.max())
                .setTooltip(Component.translatable("config.respite.newMoonHealMultiplier.tooltip"))
                .setSaveConsumer(v -> working.newMoonHealMultiplier = v)
                .build());

        // --- Phantoms (§3) ---
        ConfigCategory phantoms = builder.getOrCreateCategory(Component.translatable("config.respite.category.phantoms"));
        phantoms.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.enablePhantomRework"), config.enablePhantomRework)
                .setDefaultValue(defaults.enablePhantomRework)
                .setTooltip(Component.translatable("config.respite.enablePhantomRework.tooltip"))
                .setSaveConsumer(v -> working.enablePhantomRework = v)
                .build());
        phantoms.addEntry(entry.startIntField(Component.translatable("config.respite.phantomAltitudeMin"), config.phantomAltitudeMin)
                .setDefaultValue(defaults.phantomAltitudeMin)
                .setMin(RespiteConfig.Bounds.PHANTOM_ALTITUDE_MIN.min()).setMax(RespiteConfig.Bounds.PHANTOM_ALTITUDE_MIN.max())
                .setTooltip(Component.translatable("config.respite.phantomAltitudeMin.tooltip"))
                .setSaveConsumer(v -> working.phantomAltitudeMin = v)
                .build());
        phantoms.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.phantomNewMoon"), config.phantomNewMoon)
                .setDefaultValue(defaults.phantomNewMoon)
                .setTooltip(Component.translatable("config.respite.phantomNewMoon.tooltip"))
                .setSaveConsumer(v -> working.phantomNewMoon = v)
                .build());

        // --- Weariness (§4) ---
        ConfigCategory weariness = builder.getOrCreateCategory(Component.translatable("config.respite.category.weariness"));
        weariness.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.enableWeariness"), config.enableWeariness)
                .setDefaultValue(defaults.enableWeariness)
                .setTooltip(Component.translatable("config.respite.enableWeariness.tooltip"))
                .setSaveConsumer(v -> working.enableWeariness = v)
                .build());
        weariness.addEntry(entry.startIntField(Component.translatable("config.respite.wearinessThresholdDays"), config.wearinessThresholdDays)
                .setDefaultValue(defaults.wearinessThresholdDays)
                .setMin(RespiteConfig.Bounds.WEARINESS_THRESHOLD_DAYS.min()).setMax(RespiteConfig.Bounds.WEARINESS_THRESHOLD_DAYS.max())
                .setTooltip(Component.translatable("config.respite.wearinessThresholdDays.tooltip"))
                .setSaveConsumer(v -> working.wearinessThresholdDays = v)
                .build());
        weariness.addEntry(entry.startDoubleField(Component.translatable("config.respite.wearinessRegenPenalty"), config.wearinessRegenPenalty)
                .setDefaultValue(defaults.wearinessRegenPenalty)
                .setMin(RespiteConfig.Bounds.WEARINESS_REGEN_PENALTY.min()).setMax(RespiteConfig.Bounds.WEARINESS_REGEN_PENALTY.max())
                .setTooltip(Component.translatable("config.respite.wearinessRegenPenalty.tooltip"))
                .setSaveConsumer(v -> working.wearinessRegenPenalty = v)
                .build());
        weariness.addEntry(entry.startIntField(Component.translatable("config.respite.exhaustedThresholdDays"), config.exhaustedThresholdDays)
                .setDefaultValue(defaults.exhaustedThresholdDays)
                .setMin(RespiteConfig.Bounds.EXHAUSTED_THRESHOLD_DAYS.min()).setMax(RespiteConfig.Bounds.EXHAUSTED_THRESHOLD_DAYS.max())
                .setTooltip(Component.translatable("config.respite.exhaustedThresholdDays.tooltip"))
                .setSaveConsumer(v -> working.exhaustedThresholdDays = v)
                .build());
        weariness.addEntry(entry.startDoubleField(Component.translatable("config.respite.exhaustedRegenPenalty"), config.exhaustedRegenPenalty)
                .setDefaultValue(defaults.exhaustedRegenPenalty)
                .setMin(RespiteConfig.Bounds.EXHAUSTED_REGEN_PENALTY.min()).setMax(RespiteConfig.Bounds.EXHAUSTED_REGEN_PENALTY.max())
                .setTooltip(Component.translatable("config.respite.exhaustedRegenPenalty.tooltip"))
                .setSaveConsumer(v -> working.exhaustedRegenPenalty = v)
                .build());
        weariness.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.enableWellRested"), config.enableWellRested)
                .setDefaultValue(defaults.enableWellRested)
                .setTooltip(Component.translatable("config.respite.enableWellRested.tooltip"))
                .setSaveConsumer(v -> working.enableWellRested = v)
                .build());
        weariness.addEntry(entry.startIntField(Component.translatable("config.respite.wellRestedSeconds"), config.wellRestedSeconds)
                .setDefaultValue(defaults.wellRestedSeconds)
                .setMin(RespiteConfig.Bounds.WELL_RESTED_SECONDS.min()).setMax(RespiteConfig.Bounds.WELL_RESTED_SECONDS.max())
                .setTooltip(Component.translatable("config.respite.wellRestedSeconds.tooltip"))
                .setSaveConsumer(v -> working.wellRestedSeconds = v)
                .build());
        weariness.addEntry(entry.startDoubleField(Component.translatable("config.respite.wellRestedRegenBonus"), config.wellRestedRegenBonus)
                .setDefaultValue(defaults.wellRestedRegenBonus)
                .setMin(RespiteConfig.Bounds.WELL_RESTED_REGEN_BONUS.min()).setMax(RespiteConfig.Bounds.WELL_RESTED_REGEN_BONUS.max())
                .setTooltip(Component.translatable("config.respite.wellRestedRegenBonus.tooltip"))
                .setSaveConsumer(v -> working.wellRestedRegenBonus = v)
                .build());

        // --- Chronometer (§5) ---
        ConfigCategory chronometer = builder.getOrCreateCategory(Component.translatable("config.respite.category.chronometer"));
        chronometer.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.enableChronometer"), config.enableChronometer)
                .setDefaultValue(defaults.enableChronometer)
                .setTooltip(Component.translatable("config.respite.enableChronometer.tooltip"))
                .setSaveConsumer(v -> working.enableChronometer = v)
                .build());

        // --- Caffeinated brew (§6) ---
        ConfigCategory brew = builder.getOrCreateCategory(Component.translatable("config.respite.category.brew"));
        brew.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.enableCaffeinatedBrew"), config.enableCaffeinatedBrew)
                .setDefaultValue(defaults.enableCaffeinatedBrew)
                .setTooltip(Component.translatable("config.respite.enableCaffeinatedBrew.tooltip"))
                .setSaveConsumer(v -> working.enableCaffeinatedBrew = v)
                .build());
        brew.addEntry(entry.startIntField(Component.translatable("config.respite.brewHasteSeconds"), config.brewHasteSeconds)
                .setDefaultValue(defaults.brewHasteSeconds)
                .setMin(RespiteConfig.Bounds.BREW_HASTE_SECONDS.min()).setMax(RespiteConfig.Bounds.BREW_HASTE_SECONDS.max())
                .setTooltip(Component.translatable("config.respite.brewHasteSeconds.tooltip"))
                .setSaveConsumer(v -> working.brewHasteSeconds = v)
                .build());

        // --- Bedroll (§7) ---
        ConfigCategory bedroll = builder.getOrCreateCategory(Component.translatable("config.respite.category.bedroll"));
        bedroll.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.enableBedroll"), config.enableBedroll)
                .setDefaultValue(defaults.enableBedroll)
                .setTooltip(Component.translatable("config.respite.enableBedroll.tooltip"))
                .setSaveConsumer(v -> working.enableBedroll = v)
                .build());
        bedroll.addEntry(entry.startDoubleField(Component.translatable("config.respite.bedrollRestfulMultiplier"), config.bedrollRestfulMultiplier)
                .setDefaultValue(defaults.bedrollRestfulMultiplier)
                .setMin(RespiteConfig.Bounds.BEDROLL_RESTFUL_MULTIPLIER.min()).setMax(RespiteConfig.Bounds.BEDROLL_RESTFUL_MULTIPLIER.max())
                .setTooltip(Component.translatable("config.respite.bedrollRestfulMultiplier.tooltip"))
                .setSaveConsumer(v -> working.bedrollRestfulMultiplier = v)
                .build());

        // --- Client ---
        ConfigCategory client = builder.getOrCreateCategory(Component.translatable("config.respite.category.client"));
        client.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.showTimeLapseMessages"), config.showTimeLapseMessages)
                .setDefaultValue(defaults.showTimeLapseMessages)
                .setTooltip(Component.translatable("config.respite.showTimeLapseMessages.tooltip"))
                .setSaveConsumer(v -> working.showTimeLapseMessages = v)
                .build());
        client.addEntry(entry.startBooleanToggle(Component.translatable("config.respite.showExhaustionBlink"), config.showExhaustionBlink)
                .setDefaultValue(defaults.showExhaustionBlink)
                .setTooltip(Component.translatable("config.respite.showExhaustionBlink.tooltip"))
                .setSaveConsumer(v -> working.showExhaustionBlink = v)
                .build());

        return builder.build();
    }

    private ClothConfigScreenBuilder() {
    }
}
