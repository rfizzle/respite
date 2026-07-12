package com.rfizzle.respite.phantom;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.restful.RestfulMath;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Respite's territorial phantom spawner ({@code design/SPEC.md} §3), registered
 * into the Overworld's custom-spawner list alongside vanilla's — whose {@link
 * net.minecraft.world.level.levelgen.PhantomSpawner} is suppressed while the
 * rework is on. Vanilla's spawner is the structural template: the 60–120s
 * cadence, the difficulty roll, the group size (1 to 1 + difficulty), the 20–34
 * block spawn offset, and the plain {@code EntityType.PHANTOM} finalize are
 * copied verbatim so sibling mods treat these as ordinary vanilla phantoms. Only
 * the anchor rule changes: insomnia's {@code TIME_SINCE_REST} gate becomes
 * {@link PhantomMath#isAnchorEligible} — the heights or the new moon, never a
 * hygiene stat.
 *
 * <p>{@link #nextTick} is per-level state, exactly like vanilla's, so the
 * time-lapse's repeated world ticks compress real-time exposure without changing
 * per-night spawn counts.
 */
public final class RespitePhantomSpawner implements CustomSpawner {

    private int nextTick;

    @Override
    public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {
        RespiteConfig config = RespiteConfig.get();
        // Feature off: inert, and vanilla's spawner runs untouched (vanilla parity).
        if (!config.enablePhantomRework) {
            return 0;
        }
        if (!spawnEnemies) {
            return 0;
        }
        // doInsomnia stays the master phantom switch — a server that killed phantoms keeps them dead.
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA)) {
            return 0;
        }
        RandomSource random = level.random;
        --this.nextTick;
        if (this.nextTick > 0) {
            return 0;
        }
        this.nextTick += (60 + random.nextInt(60)) * 20;
        boolean night = level.getSkyDarken() >= 5;
        if (!night && level.dimensionType().hasSkyLight()) {
            return 0;
        }
        boolean newMoon = level.getMoonPhase() == RestfulMath.NEW_MOON_PHASE;
        int spawned = 0;
        for (ServerPlayer player : level.players()) {
            GameType gameMode = player.gameMode.getGameModeForPlayer();
            boolean survivalOrAdventure = gameMode == GameType.SURVIVAL || gameMode == GameType.ADVENTURE;
            BlockPos anchor = player.blockPosition();
            if (!PhantomMath.isAnchorEligible(night, survivalOrAdventure, player.isSleeping(),
                    level.canSeeSky(anchor), anchor.getY(), config.phantomAltitudeMin,
                    newMoon, config.phantomNewMoon)) {
                continue;
            }
            DifficultyInstance difficulty = level.getCurrentDifficultyAt(anchor);
            if (!difficulty.isHarderThan(random.nextFloat() * 3.0F)) {
                continue;
            }
            BlockPos spawnPos = anchor
                    .above(20 + random.nextInt(15))
                    .east(-10 + random.nextInt(21))
                    .south(-10 + random.nextInt(21));
            BlockState state = level.getBlockState(spawnPos);
            FluidState fluid = level.getFluidState(spawnPos);
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, spawnPos, state, fluid, EntityType.PHANTOM)) {
                continue;
            }
            SpawnGroupData groupData = null;
            int groupSize = 1 + random.nextInt(difficulty.getDifficulty().getId() + 1);
            for (int i = 0; i < groupSize; i++) {
                Phantom phantom = EntityType.PHANTOM.create(level);
                if (phantom != null) {
                    phantom.moveTo(spawnPos, 0.0F, 0.0F);
                    groupData = phantom.finalizeSpawn(level, difficulty, MobSpawnType.NATURAL, groupData);
                    level.addFreshEntityWithPassengers(phantom);
                    spawned++;
                }
            }
        }
        return spawned;
    }
}
