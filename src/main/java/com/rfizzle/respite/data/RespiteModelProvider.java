package com.rfizzle.respite.data;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.block.ChronometerBlock;
import com.rfizzle.respite.registry.RespiteRegistry;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.minecraft.data.models.BlockModelGenerators;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.data.models.blockstates.PropertyDispatch;
import net.minecraft.data.models.blockstates.Variant;
import net.minecraft.data.models.blockstates.VariantProperties;
import net.minecraft.data.models.model.ModelTemplates;
import net.minecraft.data.models.model.TextureMapping;
import net.minecraft.resources.ResourceLocation;

/**
 * Respite's blockstates and models ({@code design/SPEC.md} §5, §7).
 *
 * <p>The Chronometer's ten block models are all {@code cube_column} variants
 * differing only in their side texture, so they are generated from one template.
 * The Bedroll's block model is not: it is a four-pixel-tall custom
 * {@code elements} box, which no vanilla {@code ModelTemplate} can express, so
 * {@code assets/respite/models/block/bedroll.json} stays hand-authored under
 * {@code src/main/resources} and is only <em>referenced</em> from the blockstate
 * generated here.
 */
public class RespiteModelProvider extends FabricModelProvider {

    /** The shared top/end face every Chronometer model wears. */
    private static final ResourceLocation CHRONOMETER_TOP = Respite.id("block/chronometer_top");

    /** The eight moving dial faces, plus the still face at index {@code -1}. */
    private static final int DIAL_FACES = 8;

    public RespiteModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockModelGenerators generators) {
        RespiteRegistry.register();

        ResourceLocation still = chronometerFace(generators, "chronometer_still", "chronometer_dial_still");
        ResourceLocation[] dials = new ResourceLocation[DIAL_FACES];
        for (int face = 0; face < DIAL_FACES; face++) {
            dials[face] = chronometerFace(generators, "chronometer_dial_" + face, "chronometer_dial_" + face);
        }

        // Two redstone levels per dial face: power 0 is the fixed-time still face,
        // and 1..15 walk the eight faces two at a time — the same (power - 1) / 2
        // mapping ChronometerResourceContractTest asserts against the shipped file.
        generators.blockStateOutput.accept(MultiVariantGenerator.multiVariant(RespiteRegistry.CHRONOMETER)
                .with(PropertyDispatch.property(ChronometerBlock.POWER)
                        .generate(power -> Variant.variant()
                                .with(VariantProperties.MODEL, power == 0 ? still : dials[(power - 1) / 2]))));

        // The BlockItem shows a mid-sweep dial rather than the still face, so the
        // item in hand reads as a running clock.
        generators.delegateItemModel(RespiteRegistry.CHRONOMETER, dials[1]);

        // The bedroll is a genuine BedBlock, so it carries PART and OCCUPIED as well
        // as FACING — but it is one tile with one model, so only FACING dispatches.
        generators.blockStateOutput.accept(MultiVariantGenerator.multiVariant(RespiteRegistry.BEDROLL,
                        Variant.variant().with(VariantProperties.MODEL, Respite.id("block/bedroll")))
                .with(BlockModelGenerators.createHorizontalFacingDispatch()));
    }

    @Override
    public void generateItemModels(ItemModelGenerators generators) {
        RespiteRegistry.register();

        generators.generateFlatItem(RespiteRegistry.BEDROLL.asItem(), ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(RespiteRegistry.POCKET_CHRONOMETER, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(RespiteRegistry.UNSTEEPED_BREW, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(RespiteRegistry.CAFFEINATED_BREW, ModelTemplates.FLAT_ITEM);
    }

    /** One {@code cube_column} Chronometer face: the shared top, one dial side. */
    private static ResourceLocation chronometerFace(BlockModelGenerators generators,
                                                    String model,
                                                    String sideTexture) {
        return ModelTemplates.CUBE_COLUMN.create(
                Respite.id("block/" + model),
                TextureMapping.column(Respite.id("block/" + sideTexture), CHRONOMETER_TOP),
                generators.modelOutput);
    }
}
