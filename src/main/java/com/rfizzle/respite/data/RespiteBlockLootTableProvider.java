package com.rfizzle.respite.data;

import com.rfizzle.respite.registry.RespiteRegistry;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.Block;

import java.util.concurrent.CompletableFuture;

/**
 * Respite's block loot tables ({@code design/SPEC.md} §5, §7). Both blocks drop
 * themselves — the Chronometer keeps no inventory and the Bedroll is a one-tile
 * bed, so neither needs a conditional table.
 */
public class RespiteBlockLootTableProvider extends FabricBlockLootTableProvider {

    public RespiteBlockLootTableProvider(FabricDataOutput output,
                                         CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generate() {
        RespiteRegistry.register();

        dropSelfWithSequence(RespiteRegistry.CHRONOMETER);
        dropSelfWithSequence(RespiteRegistry.BEDROLL);
    }

    /**
     * {@link #dropSelf(Block)} with the table's random sequence restored.
     *
     * <p>Vanilla's own {@code LootTableProvider} stamps every table with
     * {@code random_sequence = <its own id>}; Fabric's block-loot provider does not
     * (it only sets the param set), so a bare {@code dropSelf} would silently drop
     * the {@code "random_sequence"} key that Respite's shipped tables carry. The
     * key selects the deterministic per-table RNG stream the
     * {@code survives_explosion} condition rolls against, so dropping it is a
     * behaviour change, not formatting — it is set back explicitly here.
     */
    private void dropSelfWithSequence(Block block) {
        add(block, createSingleItemTable(block).setRandomSequence(block.getLootTable().location()));
    }
}
