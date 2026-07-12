package com.rfizzle.respite.gametest;

import com.rfizzle.respite.brew.BrewMath;
import com.rfizzle.respite.config.RespiteConfig;
import com.rfizzle.respite.gametest.util.MockPlayers;
import com.rfizzle.respite.registry.RespiteRegistry;
import com.rfizzle.respite.weariness.WearinessHandler;
import com.rfizzle.respite.weariness.WearinessMath;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * In-world coverage for {@code design/SPEC.md} §6: the shapeless recipe
 * assembles the Unsteeped Brew from a real water bottle, a lit campfire steeps
 * it into the Caffeinated Brew in exactly 600 ticks, and drinking the brew
 * clears Weariness, resets the rest clock, grants Haste I, and returns a bottle.
 * Also pins the vanilla effect-merge contract (a stronger beacon Haste survives,
 * an equal one takes the longer duration) and the never-brick guarantee (the
 * brew still drinks with the feature disabled).
 *
 * <p>Like {@link WearinessGameTest}, mock players are not driven by the server
 * tick loop, so drink behavior is exercised by calling
 * {@link com.rfizzle.respite.brew.CaffeinatedBrewItem#finishUsingItem} directly.
 */
public class CaffeinatedBrewGameTest implements FabricGameTest {

    private static final BlockPos CAMPFIRE = new BlockPos(1, 2, 1);

    private static ItemStack waterBottle() {
        ItemStack water = new ItemStack(Items.POTION);
        water.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
        return water;
    }

    private static void setTimeSinceRest(ServerPlayer player, long ticks) {
        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), (int) ticks);
    }

    private static long ticksSinceRest(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM, Stats.TIME_SINCE_REST);
    }

    /** Runs the body; on any throwable, cleans up first so later batches stay clean. */
    private static void guarded(Runnable cleanup, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }
    }

    // --- Recipes -----------------------------------------------------------

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brewCrafting", timeoutTicks = 100)
    public void shapelessRecipeAssemblesTheUnsteepedBrew(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(2, 2, List.of(
                waterBottle(),
                new ItemStack(Items.COCOA_BEANS),
                new ItemStack(Items.COCOA_BEANS),
                new ItemStack(Items.OAK_LEAVES)));

        var recipe = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level);
        helper.assertTrue(recipe.isPresent(),
                "water bottle + 2 cocoa + leaves must match a crafting recipe");

        ItemStack result = recipe.orElseThrow().value().assemble(input, level.registryAccess());
        helper.assertTrue(result.is(RespiteRegistry.UNSTEEPED_BREW),
                "the shapeless recipe must yield the Unsteeped Brew, got " + result);
        helper.assertTrue(result.getCount() == 1, "it yields exactly one, got " + result.getCount());
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brewSteeping", timeoutTicks = 100)
    public void litCampfireSteepsUnsteepedIntoCaffeinatedInSixHundredTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(CAMPFIRE, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        CampfireBlockEntity campfire = (CampfireBlockEntity) helper.getBlockEntity(CAMPFIRE);
        helper.assertTrue(campfire != null, "the campfire block entity must exist");

        ItemStack unsteeped = new ItemStack(RespiteRegistry.UNSTEEPED_BREW);
        SingleRecipeInput cookInput = new SingleRecipeInput(unsteeped);
        Optional<RecipeHolder<CampfireCookingRecipe>> recipe =
                level.getServer().getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, cookInput, level);
        helper.assertTrue(recipe.isPresent(), "the Unsteeped Brew must have a campfire recipe");
        int cookTime = recipe.orElseThrow().value().getCookingTime();
        helper.assertTrue(cookTime == 600, "steeping must take 600 ticks, was " + cookTime);

        BlockPos abs = campfire.getBlockPos();
        BlockState state = campfire.getBlockState();
        helper.assertTrue(campfire.placeFood(null, unsteeped, cookTime), "the food must seat on the campfire");

        // One shy of the cook time: nothing has dropped yet.
        for (int i = 0; i < cookTime - 1; i++) {
            CampfireBlockEntity.cookTick(level, abs, state, campfire);
        }
        helper.assertItemEntityCountIs(RespiteRegistry.CAFFEINATED_BREW, CAMPFIRE, 2.0, 0);

        // The 600th tick completes the steep and drops the Caffeinated Brew.
        CampfireBlockEntity.cookTick(level, abs, state, campfire);
        helper.assertItemEntityCountIs(RespiteRegistry.CAFFEINATED_BREW, CAMPFIRE, 2.0, 1);
        helper.succeed();
    }

    // --- Drinking ----------------------------------------------------------

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brewDrinkExhausted", timeoutTicks = 100)
    public void drinkingClearsExhaustedResetsRestGrantsHasteAndReturnsABottle(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            setTimeSinceRest(player, WearinessMath.TICKS_PER_DAY * config.exhaustedThresholdDays);
            WearinessHandler.sweepPlayer(player, config);
            helper.assertTrue(player.hasEffect(RespiteRegistry.EXHAUSTED), "player must start Exhausted");

            // The connected mock spawns with infinite materials; drop it to
            // survival so the empty-bottle return path is the one under test.
            player.getAbilities().instabuild = false;
            ItemStack result = RespiteRegistry.CAFFEINATED_BREW.finishUsingItem(
                    new ItemStack(RespiteRegistry.CAFFEINATED_BREW), helper.getLevel(), player);

            helper.assertFalse(player.hasEffect(RespiteRegistry.EXHAUSTED), "drinking must clear Exhausted");
            helper.assertFalse(player.hasEffect(RespiteRegistry.WEARY), "and leave no Weary behind");
            helper.assertTrue(ticksSinceRest(player) == 0, "the rest clock must reset to 0");

            MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
            helper.assertTrue(haste != null, "the brew must grant Haste");
            helper.assertTrue(haste.getAmplifier() == 0, "Haste I, not Haste II");
            int expected = BrewMath.hasteDurationTicks(config.brewHasteSeconds);
            helper.assertTrue(haste.getDuration() == expected,
                    "Haste must last " + expected + " ticks, was " + haste.getDuration());
            helper.assertTrue(result.is(Items.GLASS_BOTTLE),
                    "a survival drinker gets the empty bottle back, got " + result);

            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brewFullInventory", timeoutTicks = 100)
    public void aFullInventoryDropsTheReturnedBottleRatherThanLosingIt(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            player.getAbilities().instabuild = false;
            // Fill every main slot so the emptied bottle cannot be re-inserted.
            for (int i = 0; i < player.getInventory().items.size(); i++) {
                player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
            }
            // A stack of two: drinking one leaves a brew in hand, so the bottle
            // must find another home — the drop fallback, not a silent discard.
            ItemStack twoBrews = new ItemStack(RespiteRegistry.CAFFEINATED_BREW, 2);
            RespiteRegistry.CAFFEINATED_BREW.finishUsingItem(twoBrews, helper.getLevel(), player);

            boolean dropped = player.level()
                    .getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2.0)).stream()
                    .anyMatch(e -> e.getItem().is(Items.GLASS_BOTTLE));
            helper.assertTrue(dropped, "a full inventory must drop the empty bottle, never lose it");
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brewDrinkWeary", timeoutTicks = 100)
    public void drinkingClearsWeary(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            setTimeSinceRest(player, WearinessMath.TICKS_PER_DAY * config.wearinessThresholdDays);
            WearinessHandler.sweepPlayer(player, config);
            helper.assertTrue(player.hasEffect(RespiteRegistry.WEARY), "player must start Weary");

            RespiteRegistry.CAFFEINATED_BREW.finishUsingItem(
                    new ItemStack(RespiteRegistry.CAFFEINATED_BREW), helper.getLevel(), player);

            helper.assertFalse(player.hasEffect(RespiteRegistry.WEARY), "drinking must clear Weary");
            helper.assertTrue(ticksSinceRest(player) == 0, "the rest clock must reset to 0");
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brewPreventive", timeoutTicks = 100)
    public void drinkingWhileRestedStillResetsTheClock(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            // Below the Weary line — no stage, but the timer is running.
            setTimeSinceRest(player, WearinessMath.TICKS_PER_DAY * (config.wearinessThresholdDays - 1));
            WearinessHandler.sweepPlayer(player, config);
            helper.assertFalse(player.hasEffect(RespiteRegistry.WEARY), "player must not be Weary yet");

            RespiteRegistry.CAFFEINATED_BREW.finishUsingItem(
                    new ItemStack(RespiteRegistry.CAFFEINATED_BREW), helper.getLevel(), player);

            helper.assertTrue(ticksSinceRest(player) == 0, "preventive use still resets the clock");
            helper.assertTrue(player.hasEffect(MobEffects.DIG_SPEED), "and still grants Haste");
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brewHasteStronger", timeoutTicks = 100)
    public void strongerBeaconHasteSurvivesTheBrew(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            // A stronger beacon Haste II must win the vanilla merge — the brew's
            // Haste I never overrides it or escalates it.
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 6000, 1));
            RespiteRegistry.CAFFEINATED_BREW.finishUsingItem(
                    new ItemStack(RespiteRegistry.CAFFEINATED_BREW), helper.getLevel(), player);
            MobEffectInstance haste = player.getEffect(MobEffects.DIG_SPEED);
            helper.assertTrue(haste != null && haste.getAmplifier() == 1,
                    "a stronger beacon Haste II must survive the brew");
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brewHasteEqual", timeoutTicks = 100)
    public void equalHasteTakesTheLongerDurationNeverEscalates(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> MockPlayers.retire(player);
        guarded(cleanup, () -> {
            // An equal-strength Haste I with a short duration is refreshed to the
            // brew's longer duration — never escalated to II.
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 0));
            int brewTicks = BrewMath.hasteDurationTicks(RespiteConfig.get().brewHasteSeconds);
            RespiteRegistry.CAFFEINATED_BREW.finishUsingItem(
                    new ItemStack(RespiteRegistry.CAFFEINATED_BREW), helper.getLevel(), player);
            MobEffectInstance merged = player.getEffect(MobEffects.DIG_SPEED);
            helper.assertTrue(merged != null && merged.getAmplifier() == 0,
                    "an equal Haste I stays Haste I, never escalates to II");
            helper.assertTrue(merged.getDuration() == brewTicks,
                    "the longer brew duration wins, expected " + brewTicks + ", was " + merged.getDuration());
            cleanup.run();
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "brewDisabled", timeoutTicks = 100)
    public void brewStillDrinksWithTheFeatureDisabled(GameTestHelper helper) {
        MockPlayers.retireLeaked(helper);
        RespiteConfig config = RespiteConfig.get();
        boolean saved = config.enableCaffeinatedBrew;
        ServerPlayer player = MockPlayers.serverPlayerInLevel(helper);
        Runnable cleanup = () -> {
            config.enableCaffeinatedBrew = saved;
            MockPlayers.retire(player);
        };
        guarded(cleanup, () -> {
            // The toggle removes the recipes, but a bottle already in hand must
            // never brick — its drink behavior is not gated on the feature flag.
            config.enableCaffeinatedBrew = false;
            setTimeSinceRest(player, WearinessMath.TICKS_PER_DAY * config.exhaustedThresholdDays);
            WearinessHandler.sweepPlayer(player, config);
            helper.assertTrue(player.hasEffect(RespiteRegistry.EXHAUSTED), "player must start Exhausted");

            RespiteRegistry.CAFFEINATED_BREW.finishUsingItem(
                    new ItemStack(RespiteRegistry.CAFFEINATED_BREW), helper.getLevel(), player);

            helper.assertFalse(player.hasEffect(RespiteRegistry.EXHAUSTED),
                    "a held brew still clears Weariness with the feature off");
            helper.assertTrue(ticksSinceRest(player) == 0, "and still resets the clock");
            helper.assertTrue(player.hasEffect(MobEffects.DIG_SPEED), "and still grants Haste");
            cleanup.run();
            helper.succeed();
        });
    }
}
