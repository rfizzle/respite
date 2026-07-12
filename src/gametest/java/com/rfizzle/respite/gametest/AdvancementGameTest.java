package com.rfizzle.respite.gametest;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.rest.RestWakeEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

/**
 * In-world coverage for the custom advancement criteria ({@code design/SPEC.md}
 * §Advancements): root, beauty_sleep, and dark_and_dreamless grant from the
 * dawn-wake dispatcher on their exact facts, night_shift grants from drinking
 * the brew while weary, and a bystander who did nothing is never granted. The
 * two vanilla-trigger advancements (mountain_watch, clockwork) are covered by
 * the JSON contract and vanilla's own trigger firing.
 *
 * <p>Players are spawned through the {@code getAdvancements().reload(...)} trick
 * so a listener exists for the freshly-registered criteria before the first
 * fire (per the {@code mc-advancements} grant-assertion pattern).
 */
public class AdvancementGameTest implements FabricGameTest {

    private static ServerPlayer spawnListeningPlayer(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        player.getAdvancements().reload(helper.getLevel().getServer().getAdvancements());
        return player;
    }

    private static void assertGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Respite.id(path));
        helper.assertTrue(holder != null, "advancement " + path + " should be loaded (datapack output present)");
        helper.assertTrue(player.getAdvancements().getOrStartProgress(holder).isDone(),
                "advancement " + path + " should be granted");
    }

    private static void assertNotGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Respite.id(path));
        helper.assertTrue(holder != null, "advancement " + path + " should be loaded (datapack output present)");
        helper.assertTrue(!player.getAdvancements().getOrStartProgress(holder).isDone(),
                "advancement " + path + " should NOT be granted");
    }

    private static void guarded(Runnable cleanup, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "advancementLoad", timeoutTicks = 100)
    public void allSixAdvancementsLoad(GameTestHelper helper) {
        // A codec-invalid predicate (the vanilla-trigger mountain_watch/clockwork
        // JSON especially) loads to a null holder rather than throwing — assert
        // every tab entry is actually present so a bad predicate fails here.
        for (String id : new String[] {"root", "beauty_sleep", "night_shift",
                "mountain_watch", "clockwork", "dark_and_dreamless"}) {
            AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(Respite.id(id));
            helper.assertTrue(holder != null, "advancement " + id + " should have loaded");
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "advancementRoot", timeoutTicks = 100)
    public void rootGrantsForSleepingThroughAnActiveLapse(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer sleeper = spawnListeningPlayer(helper);
        ServerPlayer bystander = spawnListeningPlayer(helper);
        Runnable cleanup = () -> {
            MockPlayers.retire(sleeper);
            MockPlayers.retire(bystander);
        };
        guarded(cleanup, () -> {
            // Slept a full-moon-adjacent night (phase 0), no healing, lapse active:
            // only root should land.
            RestWakeEvents.onDawnWake(sleeper, 200, 0.0f, true, 0);
            assertGranted(helper, sleeper, "root");
            assertNotGranted(helper, sleeper, "beauty_sleep");
            assertNotGranted(helper, sleeper, "dark_and_dreamless");
            // A player who never woke is untouched.
            assertNotGranted(helper, bystander, "root");
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "advancementBeauty", timeoutTicks = 100)
    public void beautySleepGrantsAtEightHeartsRestored(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer healed = spawnListeningPlayer(helper);
        ServerPlayer barely = spawnListeningPlayer(helper);
        Runnable cleanup = () -> {
            MockPlayers.retire(healed);
            MockPlayers.retire(barely);
        };
        guarded(cleanup, () -> {
            RestWakeEvents.onDawnWake(healed, 200, RestWakeEvents.BEAUTY_SLEEP_HEALTH, false, 0);
            RestWakeEvents.onDawnWake(barely, 200, RestWakeEvents.BEAUTY_SLEEP_HEALTH - 1.0f, false, 0);
            assertGranted(helper, healed, "beauty_sleep");
            assertNotGranted(helper, barely, "beauty_sleep");
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "advancementDark", timeoutTicks = 100)
    public void darkAndDreamlessGrantsOnANewMoonNight(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer newMoon = spawnListeningPlayer(helper);
        ServerPlayer otherNight = spawnListeningPlayer(helper);
        Runnable cleanup = () -> {
            MockPlayers.retire(newMoon);
            MockPlayers.retire(otherNight);
        };
        guarded(cleanup, () -> {
            RestWakeEvents.onDawnWake(newMoon, 200, 0.0f, false, 4);
            RestWakeEvents.onDawnWake(otherNight, 200, 0.0f, false, 0);
            assertGranted(helper, newMoon, "dark_and_dreamless");
            assertNotGranted(helper, otherNight, "dark_and_dreamless");
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "advancementNightShift", timeoutTicks = 100)
    public void nightShiftGrantsForDrinkingTheBrewWhileWeary(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer weary = spawnListeningPlayer(helper);
        ServerPlayer rested = spawnListeningPlayer(helper);
        Runnable cleanup = () -> {
            MockPlayers.retire(weary);
            MockPlayers.retire(rested);
        };
        guarded(cleanup, () -> {
            weary.addEffect(new MobEffectInstance(
                    RespiteRegistry.WEARY, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
            drinkBrew(weary);
            drinkBrew(rested);
            assertGranted(helper, weary, "night_shift");
            assertNotGranted(helper, rested, "night_shift");
            cleanup.run();
            helper.succeed();
        });
    }

    private static void drinkBrew(ServerPlayer player) {
        ItemStack stack = new ItemStack(RespiteRegistry.CAFFEINATED_BREW);
        RespiteRegistry.CAFFEINATED_BREW.finishUsingItem(stack, player.serverLevel(), player);
    }
}
