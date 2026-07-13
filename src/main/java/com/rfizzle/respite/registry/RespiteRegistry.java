package com.rfizzle.respite.registry;

import com.mojang.serialization.Codec;
import com.rfizzle.respite.Respite;
import com.rfizzle.respite.bedroll.BedrollBlock;
import com.rfizzle.respite.bedroll.BedrollItem;
import com.rfizzle.respite.block.ChronometerBlock;
import com.rfizzle.respite.brew.CaffeinatedBrewItem;
import com.rfizzle.respite.chronometer.PocketChronometerItem;
import com.rfizzle.respite.condition.FeatureEnabledCondition;
import com.rfizzle.respite.effect.WearinessEffect;
import com.rfizzle.respite.effect.WellRestedEffect;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The one home for every {@code Registry.register} call. Registration runs
 * once from {@link Respite#onInitialize()}, before the registries freeze;
 * the insertion-ordered block map is the roster datagen, compat, and the
 * resource-contract tests walk.
 */
public final class RespiteRegistry {

    /** Every registered block, in registration order. */
    public static final Map<ResourceLocation, Block> BLOCKS = new LinkedHashMap<>();

    /** Standalone (non-BlockItem) items, in registration order — the roster
     * datagen, compat, and the resource-contract tests can walk. */
    public static final List<Item> STANDALONE_ITEMS = new ArrayList<>();

    // Quick to break with any tool (or none), always drops itself, pistons move
    // it like stone; copper case on smooth stone → copper sounds, copper map color.
    public static final ChronometerBlock CHRONOMETER = new ChronometerBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(0.5f)
                    .sound(SoundType.COPPER));

    // The lapse-edge cue pair (design/SPEC.md §Sound Design): "time itself is
    // moving" has no vanilla voice, so these are the one custom synthesis.
    public static final SoundEvent TIME_LAPSE_START =
            SoundEvent.createVariableRangeEvent(Respite.id("ui.time_lapse.start"));
    public static final SoundEvent TIME_LAPSE_END =
            SoundEvent.createVariableRangeEvent(Respite.id("ui.time_lapse.end"));

    // The two weariness stages (design/SPEC.md §4). Muted Moonlight-indigo tint —
    // ambient with no particles, so the colour only ever shows in the icon frame.
    // The sweep applies them and the regen mixin reads them off the player to
    // resolve the active stage.
    public static final Holder<MobEffect> WEARY = registerEffect("weary", new WearinessEffect(0x5A5A82));
    public static final Holder<MobEffect> EXHAUSTED = registerEffect("exhausted", new WearinessEffect(0x3A3A5A));

    // The positive pole of the weariness ladder (design/SPEC.md §4): a beneficial,
    // behaviour-free marker granted on a dawn wake. Warm Candleglow tint — the
    // "wake refreshed" morning. The regen mixin reads it off the player to resolve
    // the bonus; the grant applies it, no sweep re-asserts it.
    public static final Holder<MobEffect> WELL_RESTED = registerEffect("well_rested", new WellRestedEffect(0xF2C14E));

    // The Caffeinated Brew pair (design/SPEC.md §6). Both stack to 16. The
    // Unsteeped Brew is inert — a plain crafting intermediate. The Caffeinated
    // Brew carries a zero-nutrition, always-edible food component so it drinks
    // like a potion while restoring no hunger or saturation ("not food"); its
    // effects are applied in CaffeinatedBrewItem#finishUsingItem, never through
    // this component's own eat path.
    public static final Item UNSTEEPED_BREW =
            new Item(new Item.Properties().stacksTo(16));
    public static final CaffeinatedBrewItem CAFFEINATED_BREW = new CaffeinatedBrewItem(
            new Item.Properties()
                    .stacksTo(16)
                    .food(new FoodProperties.Builder().alwaysEdible().build()));

    // The pocket chronometer (design/SPEC.md §5) — the portable half of the
    // Chronometer's job: a carried timepiece whose tooltip reads the hour, the moon,
    // the nights until the new moon, and the holder's days awake. It places nothing
    // and emits no signal, so it is a plain standalone item.
    public static final PocketChronometerItem POCKET_CHRONOMETER =
            new PocketChronometerItem(new Item.Properties());

    // The days-awake carrier the pocket chronometer writes server-side and its
    // tooltip reads client-side: TIME_SINCE_REST is server-only, so it rides the
    // stack (network-synced) rather than being read from the client's stat counter.
    public static final DataComponentType<Integer> AWAKE_TICKS = registerComponent("awake_ticks",
            DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // The bedroll (design/SPEC.md §7) — a one-tile camp bed. Wool body → wool
    // sound and a warm map colour; ignited by lava, crushed like a bed by pistons.
    // A genuine BedBlock so vanilla's sleeper machinery treats it as one; Respite
    // routes its sleep through Bedroll to skip the spawn set and halve the heal.
    public static final BedrollBlock BEDROLL = new BedrollBlock(
            DyeColor.BROWN,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.2f)
                    .sound(SoundType.WOOL)
                    .ignitedByLava()
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY));

    private static boolean registered;

    private RespiteRegistry() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        registerBlock("chronometer", CHRONOMETER, new Item.Properties());

        // The bedroll's item is a custom BlockItem (auto-use sleep), so it is
        // registered directly rather than through the default-BlockItem helper.
        ResourceLocation bedrollId = Respite.id("bedroll");
        Registry.register(BuiltInRegistries.BLOCK, bedrollId, BEDROLL);
        BLOCKS.put(bedrollId, BEDROLL);
        Registry.register(BuiltInRegistries.ITEM, bedrollId,
                new BedrollItem(BEDROLL, new Item.Properties().stacksTo(16)));

        registerItem("pocket_chronometer", POCKET_CHRONOMETER);

        registerItem("unsteeped_brew", UNSTEEPED_BREW);
        registerItem("caffeinated_brew", CAFFEINATED_BREW);

        registerSound(TIME_LAPSE_START);
        registerSound(TIME_LAPSE_END);

        // A redstone component belongs where builders look for redstone
        // components — the vanilla tab, not a one-block mod tab.
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
                .register(entries -> entries.accept(CHRONOMETER));

        // Both brews are drinks in all but name, so they sit with the other
        // bottles in the vanilla Food & Drinks tab. Accepted explicitly so the
        // tab stays intentional even as the standalone-item roster grows.
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS)
                .register(entries -> {
                    entries.accept(UNSTEEPED_BREW);
                    entries.accept(CAFFEINATED_BREW);
                });

        // The bedroll and the pocket chronometer are camp/travel utilities — they
        // sit with the other tools and utility items (where vanilla keeps the clock),
        // not in the vanilla bed tab or the Chronometer block's redstone tab.
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> {
                    entries.accept(BEDROLL);
                    entries.accept(POCKET_CHRONOMETER);
                });

        // Datapack-side feature gates (recipes and their unlock advancements).
        ResourceConditions.register(FeatureEnabledCondition.TYPE);
    }

    private static void registerSound(SoundEvent event) {
        Registry.register(BuiltInRegistries.SOUND_EVENT, event.getLocation(), event);
    }

    private static <T extends Item> T registerItem(String name, T item) {
        Registry.register(BuiltInRegistries.ITEM, Respite.id(name), item);
        STANDALONE_ITEMS.add(item);
        return item;
    }

    private static <T> DataComponentType<T> registerComponent(String name, DataComponentType<T> type) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Respite.id(name), type);
    }

    private static Holder<MobEffect> registerEffect(String name, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Respite.id(name), effect);
    }

    private static <T extends Block> T registerBlock(String name, T block, Item.Properties itemProperties) {
        ResourceLocation id = Respite.id(name);
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        BLOCKS.put(id, block);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, itemProperties));
        return block;
    }
}
