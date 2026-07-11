package com.rfizzle.respite.registry;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.block.ChronometerBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

    private static boolean registered;

    private RespiteRegistry() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        registerBlock("chronometer", CHRONOMETER, new Item.Properties());

        // A redstone component belongs where builders look for redstone
        // components — the vanilla tab, not a one-block mod tab.
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
                .register(entries -> entries.accept(CHRONOMETER));
    }

    private static <T extends Block> T registerBlock(String name, T block, Item.Properties itemProperties) {
        ResourceLocation id = Respite.id(name);
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        BLOCKS.put(id, block);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, itemProperties));
        return block;
    }
}
