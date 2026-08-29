package com.rfizzle.respite.data;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Respite's {@code fabric-datagen} entrypoint — the first of the four datagen
 * anchors (the loom {@code datagen} run, the {@code make run-datagen} target and
 * {@code verifyDatagenIdempotent} are the other three, and they only mean
 * anything as a set: with no entrypoint the verify task's git pathspec matches
 * an empty directory and reports clean having checked nothing).
 *
 * <p>Everything registered here writes into {@code src/main/generated}, which
 * {@code build.gradle} declares as a {@code main} resources source dir — so the
 * output ships in the jar and lands on the test classpath exactly the way
 * {@code src/main/resources} does, which is what keeps the
 * {@code *ResourceContractTest} guards reading the real artifact.
 *
 * <p>Two shipped files stay hand-authored on purpose and are <em>not</em>
 * generated here: {@code assets/respite/models/block/bedroll.json} (an
 * {@code elements}-based model — vanilla's {@link net.minecraft.data.models.model.ModelTemplate}
 * can only emit parent+textures) and the lang/sounds files.
 */
public class RespiteDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        pack.addProvider(RespiteModelProvider::new);
        pack.addProvider(RespiteBlockLootTableProvider::new);
        pack.addProvider(RespiteRecipeProvider::new);
        pack.addProvider(RespiteAdvancementProvider::new);
    }
}
