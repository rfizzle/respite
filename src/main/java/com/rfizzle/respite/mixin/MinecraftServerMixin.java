package com.rfizzle.respite.mixin;

import com.rfizzle.respite.phantom.RespitePhantomSpawner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Registers {@link RespitePhantomSpawner} into the Overworld's custom-spawner
 * list ({@code design/SPEC.md} §3) by augmenting the list handed to the
 * {@code ServerLevel} constructor in {@code createLevels}. The augmentation is
 * gated on the list already carrying a vanilla {@link PhantomSpawner} — which
 * vanilla only gives the Overworld — so Respite's spawner inherits vanilla's
 * Overworld-only placement and per-level accounting slot structurally, never
 * touching the Nether or End. It is always registered; the spawner self-checks
 * {@code enablePhantomRework} at tick time, so the feature toggle takes effect
 * without re-registration.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @ModifyArg(method = "createLevels",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;<init>("
                            + "Lnet/minecraft/server/MinecraftServer;"
                            + "Ljava/util/concurrent/Executor;"
                            + "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"
                            + "Lnet/minecraft/world/level/storage/ServerLevelData;"
                            + "Lnet/minecraft/resources/ResourceKey;"
                            + "Lnet/minecraft/world/level/dimension/LevelStem;"
                            + "Lnet/minecraft/server/level/progress/ChunkProgressListener;"
                            + "ZJLjava/util/List;Z"
                            + "Lnet/minecraft/world/RandomSequences;)V"),
            index = 9)
    private List<CustomSpawner> respite$registerPhantomSpawner(List<CustomSpawner> customSpawners) {
        boolean overworldList = customSpawners.stream().anyMatch(spawner -> spawner instanceof PhantomSpawner);
        if (!overworldList) {
            return customSpawners;
        }
        List<CustomSpawner> augmented = new ArrayList<>(customSpawners);
        augmented.add(new RespitePhantomSpawner());
        return augmented;
    }
}
