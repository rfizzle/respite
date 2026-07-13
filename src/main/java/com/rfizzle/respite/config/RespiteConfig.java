package com.rfizzle.respite.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rfizzle.respite.Respite;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The single mod config, {@code config/respite.json} — every key from
 * {@code design/SPEC.md} §Configuration. Server keys are authoritative gameplay
 * rules; the client keys are presentation toggles honored only on the client.
 */
public class RespiteConfig {
    private static volatile RespiteConfig INSTANCE;

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().setLenient().create();

    // Schema version of the on-disk file. Bumped by ConfigMigrator when the shape changes; a
    // freshly constructed config is already current. Not player-tunable — leave it out of clamp().
    public int configVersion = ConfigMigrator.CURRENT_VERSION;

    // --- Server Config ---

    // Time-lapse (§1)
    public boolean enableTimeLapse = true;
    public int maxTimeLapseRate = 60;
    public int timeLapseTickBudgetMs = 40;
    public boolean combatHoldsTime = true;
    public boolean announceTimeLapse = true;
    public boolean announceSleepVote = true;
    // Idle exclusion (§1): an AFK player counts for nothing on either side of the k/n share.
    public boolean excludeIdleFromShare = true;
    public int idleThresholdMinutes = 5;

    // Restful saturation (§2)
    public boolean enableRestfulSaturation = true;
    public boolean restfulRequiresFullHunger = true;
    public int restfulHealIntervalTicks = 600;
    public double newMoonHealMultiplier = 2.0;

    // Phantoms (§3)
    public boolean enablePhantomRework = true;
    public int phantomAltitudeMin = 100;
    public boolean phantomNewMoon = true;

    // Weariness (§4)
    public boolean enableWeariness = true;
    public int wearinessThresholdDays = 3;
    public double wearinessRegenPenalty = 0.25;
    public int exhaustedThresholdDays = 6;
    public double exhaustedRegenPenalty = 0.50;
    // Well-Rested — the positive pole of the weariness ladder (§4)
    public boolean enableWellRested = true;
    public int wellRestedSeconds = 120;
    public double wellRestedRegenBonus = 0.5;

    // Chronometer (§5)
    public boolean enableChronometer = true;

    // Caffeinated brew (§6)
    public boolean enableCaffeinatedBrew = true;
    public int brewHasteSeconds = 90;

    // Bedroll (§7)
    public boolean enableBedroll = true;
    public double bedrollRestfulMultiplier = 0.5;

    // --- Client Config ---

    public boolean showTimeLapseMessages = true;
    public boolean showExhaustionBlink = true;

    public void clamp() {
        maxTimeLapseRate = clampInt("maxTimeLapseRate", maxTimeLapseRate, 2, 100);
        timeLapseTickBudgetMs = clampInt("timeLapseTickBudgetMs", timeLapseTickBudgetMs, 5, 45);
        idleThresholdMinutes = clampInt("idleThresholdMinutes", idleThresholdMinutes, 1, 60);
        restfulHealIntervalTicks = clampInt("restfulHealIntervalTicks", restfulHealIntervalTicks, 100, 2400);
        newMoonHealMultiplier = clampDouble("newMoonHealMultiplier", newMoonHealMultiplier, 1.0, 4.0);
        phantomAltitudeMin = clampInt("phantomAltitudeMin", phantomAltitudeMin, -64, 320);
        wearinessThresholdDays = clampInt("wearinessThresholdDays", wearinessThresholdDays, 1, 30);
        wearinessRegenPenalty = clampDouble("wearinessRegenPenalty", wearinessRegenPenalty, 0.0, 0.95);
        exhaustedThresholdDays = clampInt("exhaustedThresholdDays", exhaustedThresholdDays, 2, 60);
        exhaustedRegenPenalty = clampDouble("exhaustedRegenPenalty", exhaustedRegenPenalty, 0.0, 0.95);
        wellRestedSeconds = clampInt("wellRestedSeconds", wellRestedSeconds, 0, 600);
        wellRestedRegenBonus = clampDouble("wellRestedRegenBonus", wellRestedRegenBonus, 0.0, 2.0);
        brewHasteSeconds = clampInt("brewHasteSeconds", brewHasteSeconds, 0, 600);
        bedrollRestfulMultiplier = clampDouble("bedrollRestfulMultiplier", bedrollRestfulMultiplier, 0.0, 1.0);
        // Exhausted must sit at least one full day past Weary (SPEC §4); runs after the
        // range clamps so the raised value stays inside both fields' stated ranges.
        if (exhaustedThresholdDays < wearinessThresholdDays + 1) {
            Respite.LOGGER.warn("Config 'exhaustedThresholdDays' value {} is below wearinessThresholdDays + 1 ({}); raised to {}",
                    exhaustedThresholdDays, wearinessThresholdDays + 1, wearinessThresholdDays + 1);
            exhaustedThresholdDays = wearinessThresholdDays + 1;
        }
    }

    /**
     * Clamp {@code value} into {@code [min, max]}, logging a warning when the
     * hand-edited value was actually out of range (warn-and-clamp — a player
     * can see exactly which field their edit overrode).
     */
    private static int clampInt(String name, int value, int min, int max) {
        int clamped = Math.clamp(value, min, max);
        if (clamped != value) {
            Respite.LOGGER.warn("Config '{}' value {} out of range [{}, {}]; clamped to {}",
                    name, value, min, max, clamped);
        }
        return clamped;
    }

    /** Double counterpart of {@link #clampInt}. */
    private static double clampDouble(String name, double value, double min, double max) {
        double clamped = Math.clamp(value, min, max);
        if (clamped != value) {
            Respite.LOGGER.warn("Config '{}' value {} out of range [{}, {}]; clamped to {}",
                    name, value, min, max, clamped);
        }
        return clamped;
    }

    /**
     * The active config. Per-tick feature code must snapshot this once at the top of
     * the outer tick ({@code RespiteConfig config = RespiteConfig.get();}) and read
     * fields off the local — never re-invoke {@code get()} inside the time-lapse's
     * repeated-tick loop, where the volatile read would recur up to 60× per real tick.
     */
    public static RespiteConfig get() {
        RespiteConfig local = INSTANCE;
        if (local == null) {
            synchronized (RespiteConfig.class) {
                local = INSTANCE;
                if (local == null) {
                    local = load();
                    INSTANCE = local;
                }
            }
        }
        return local;
    }

    /** Rebuilds the active config from disk — the seam {@code /respite reload} will call. */
    public static void reload() {
        synchronized (RespiteConfig.class) {
            INSTANCE = load();
        }
    }

    /**
     * Deep working copy for the ModMenu screen. Built from current JSON, so it is
     * already at {@link ConfigMigrator#CURRENT_VERSION} and must never be re-migrated.
     */
    public RespiteConfig copy() {
        return GSON.fromJson(GSON.toJson(this), RespiteConfig.class);
    }

    /**
     * Publishes an edited working copy as the active config — clamped, persisted, then
     * atomically swapped in, so readers only ever observe a fully-applied edit. The
     * ModMenu screen's save seam; every config publication goes through a single
     * volatile store, mirroring {@link #reload()}.
     */
    public static void publish(RespiteConfig next) {
        next.clamp();
        next.save();
        synchronized (RespiteConfig.class) {
            INSTANCE = next;
        }
    }

    public void save() {
        save(configPath());
    }

    void save(Path path) {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(tmp, GSON.toJson(this));
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Respite.LOGGER.error("Failed to save config", e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanup) {
                Respite.LOGGER.warn("Failed to delete orphan config tmp file {}", tmp, cleanup);
            }
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("respite.json");
    }

    private static RespiteConfig load() {
        return load(configPath());
    }

    static RespiteConfig load(Path path) {
        if (!Files.exists(path)) {
            RespiteConfig defaults = new RespiteConfig();
            defaults.save(path);
            return defaults;
        }
        try {
            // Migrate the raw JSON before Gson so renamed/restructured fields survive the upgrade,
            // then deserialize, clamp, and persist the upgraded schema back to disk.
            String json = Files.readString(path);
            JsonElement element = JsonParser.parseString(json);
            if (element == null || !element.isJsonObject()) {
                Respite.LOGGER.error("Config at {} is not a JSON object; using defaults (existing file left untouched)", path);
                return new RespiteConfig();
            }
            JsonObject raw = element.getAsJsonObject();
            boolean migrated = ConfigMigrator.migrate(raw);

            RespiteConfig config = GSON.fromJson(raw, RespiteConfig.class);
            if (config == null) {
                config = new RespiteConfig();
            }
            config.clamp();
            if (migrated) {
                config.save(path);
            }
            return config;
        } catch (Exception e) {
            Respite.LOGGER.error("Failed to load config, using defaults (corrupted file preserved at {})", path, e);
            return new RespiteConfig();
        }
    }
}
