package com.rfizzle.respite.gametest;

import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.mixin.PhantomSpawnerAccessor;
import com.rfizzle.respite.mixin.RespitePhantomSpawnerAccessor;
import com.rfizzle.respite.mixin.ServerLevelCustomSpawnersAccessor;
import com.rfizzle.respite.phantom.RespitePhantomSpawner;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.PhantomSpawner;

/**
 * In-world coverage for {@code design/SPEC.md} §3 — the deterministic gates and
 * the spawner wiring. The actual spawn roll (difficulty-weighted, position-
 * validated) is the spec's documented manual-observation pass; these tests prove
 * the gates that decide whether it is even reached, that vanilla's insomnia
 * spawner is suppressed, and that Respite's spawner is registered in the
 * Overworld's list. The eligibility matrix itself is {@code PhantomMathTest}.
 */
public class PhantomGameTest implements FabricGameTest {

    private static void setInsomnia(ServerLevel level, boolean value) {
        level.getGameRules().getRule(GameRules.RULE_DOINSOMNIA).set(value, level.getServer());
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "phantomReworkOff")
    public void reworkOffKeepsRespiteSpawnerInert(GameTestHelper helper) {
        RespiteConfig config = RespiteConfig.get();
        boolean saved = config.enablePhantomRework;
        try {
            config.enablePhantomRework = false;
            RespitePhantomSpawner spawner = new RespitePhantomSpawner();
            int spawned = spawner.tick(helper.getLevel(), true, false);
            helper.assertTrue(spawned == 0,
                    "with the rework off, Respite's spawner must do nothing, got " + spawned);
            int nextTick = ((RespitePhantomSpawnerAccessor) (Object) spawner).respite$getNextTick();
            helper.assertTrue(nextTick == 0,
                    "the rework-off bail-out must fire before --nextTick, was " + nextTick);
            helper.succeed();
        } finally {
            config.enablePhantomRework = saved;
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "phantomNoEnemies")
    public void spawnEnemiesFalseYieldsNoSpawns(GameTestHelper helper) {
        RespiteConfig config = RespiteConfig.get();
        boolean saved = config.enablePhantomRework;
        try {
            config.enablePhantomRework = true;
            RespitePhantomSpawner spawner = new RespitePhantomSpawner();
            int spawned = spawner.tick(helper.getLevel(), false, false);
            helper.assertTrue(spawned == 0, "spawnEnemies=false must yield no phantoms, got " + spawned);
            int nextTick = ((RespitePhantomSpawnerAccessor) (Object) spawner).respite$getNextTick();
            helper.assertTrue(nextTick == 0,
                    "the spawnEnemies=false bail-out must fire before --nextTick, was " + nextTick);
            helper.succeed();
        } finally {
            config.enablePhantomRework = saved;
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "phantomInsomniaOff")
    public void doInsomniaOffSuppressesRespiteSpawner(GameTestHelper helper) {
        RespiteConfig config = RespiteConfig.get();
        ServerLevel level = helper.getLevel();
        boolean savedRework = config.enablePhantomRework;
        boolean savedInsomnia = level.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA);
        try {
            MockPlayers.retireLeaked(helper);
            config.enablePhantomRework = true;
            setInsomnia(level, false);
            RespitePhantomSpawner spawner = new RespitePhantomSpawner();
            int spawned = spawner.tick(level, true, false);
            helper.assertTrue(spawned == 0, "doInsomnia off must suppress Respite's spawner too, got " + spawned);
            int nextTick = ((RespitePhantomSpawnerAccessor) (Object) spawner).respite$getNextTick();
            helper.assertTrue(nextTick == 0,
                    "the doInsomnia-off bail-out must fire before --nextTick, was " + nextTick);
            helper.succeed();
        } finally {
            config.enablePhantomRework = savedRework;
            setInsomnia(level, savedInsomnia);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "phantomVanillaSuppressed")
    public void vanillaPhantomSpawnerIsSuppressedWhileReworkOn(GameTestHelper helper) {
        RespiteConfig config = RespiteConfig.get();
        ServerLevel level = helper.getLevel();
        boolean savedRework = config.enablePhantomRework;
        boolean savedInsomnia = level.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA);
        try {
            MockPlayers.retireLeaked(helper);
            setInsomnia(level, true);
            PhantomSpawner vanilla = new PhantomSpawner();

            // Rework on: the mixin returns at HEAD before vanilla touches its countdown.
            config.enablePhantomRework = true;
            int suppressed = vanilla.tick(level, true, false);
            helper.assertTrue(suppressed == 0, "suppressed vanilla tick must return 0, got " + suppressed);
            int nextTick = ((PhantomSpawnerAccessor) (Object) vanilla).respite$getNextTick();
            helper.assertTrue(nextTick == 0,
                    "the suppression inject must fire before vanilla advances nextTick, was " + nextTick);

            // Rework off: vanilla runs and advances its countdown — proving the guard, not a coincidental zero.
            config.enablePhantomRework = false;
            vanilla.tick(level, true, false);
            int advanced = ((PhantomSpawnerAccessor) (Object) vanilla).respite$getNextTick();
            helper.assertTrue(advanced != 0,
                    "with the rework off, vanilla's spawner must run and advance nextTick, was " + advanced);
            helper.succeed();
        } finally {
            config.enablePhantomRework = savedRework;
            setInsomnia(level, savedInsomnia);
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "phantomWiring")
    public void respiteSpawnerIsRegisteredInTheOverworldList(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<CustomSpawner> spawners =
                ((ServerLevelCustomSpawnersAccessor) (Object) level).respite$getCustomSpawners();
        boolean hasVanilla = spawners.stream().anyMatch(spawner -> spawner instanceof PhantomSpawner);
        boolean hasRespite = spawners.stream().anyMatch(spawner -> spawner instanceof RespitePhantomSpawner);
        if (hasVanilla) {
            helper.assertTrue(hasRespite,
                    "an Overworld list carrying a vanilla PhantomSpawner must also carry Respite's");
        } else {
            // A test dimension without vanilla's phantom spawner: the guard must add nothing either.
            helper.assertTrue(!hasRespite,
                    "with no vanilla PhantomSpawner present, Respite's must not be added");
        }
        helper.succeed();
    }
}
