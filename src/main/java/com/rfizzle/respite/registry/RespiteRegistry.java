package com.rfizzle.respite.registry;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.block.ChronometerBlock;
import com.rfizzle.respite.condition.FeatureEnabledCondition;
import com.rfizzle.respite.effect.WearinessEffect;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The one home for every {@code Registry.register} call. Registration runs
 * once from {@link Respite#onInitialize()}, before the registries freeze;
 * the insertion-ordered block map is the roster datagen, compat, and the
 * resource-contract tests walk.
 */
public final class RespiteRegistry {

    /** Every registered block, in registration order. */
    public static final Map<ResourceLocation, Block> BLOCKS = new LinkedHashMap<>();

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
    // Holders are assigned in register(); the sweep applies them and the regen
    // mixin reads them off the player to resolve the active stage.
    public static Holder<MobEffect> WEARY;
    public static Holder<MobEffect> EXHAUSTED;

    private static boolean registered;

    private RespiteRegistry() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        registerBlock("chronometer", CHRONOMETER, new Item.Properties());

        registerSound(TIME_LAPSE_START);
        registerSound(TIME_LAPSE_END);

        WEARY = registerEffect("weary", new WearinessEffect(0x5A5A82));
        EXHAUSTED = registerEffect("exhausted", new WearinessEffect(0x3A3A5A));

        // A redstone component belongs where builders look for redstone
        // components — the vanilla tab, not a one-block mod tab.
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
                .register(entries -> entries.accept(CHRONOMETER));

        // Datapack-side feature gates (recipes and their unlock advancements).
        ResourceConditions.register(FeatureEnabledCondition.TYPE);
    }

    private static void registerSound(SoundEvent event) {
        Registry.register(BuiltInRegistries.SOUND_EVENT, event.getLocation(), event);
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
