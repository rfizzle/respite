package com.rfizzle.respite.mixin;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to {@link ServerLevel}'s private custom-spawner list, so the
 * phantom wiring gametest can assert that {@code RespitePhantomSpawner} landed in
 * the Overworld's list.
 */
@Mixin(ServerLevel.class)
public interface ServerLevelCustomSpawnersAccessor {

    @Accessor("customSpawners")
    List<CustomSpawner> respite$getCustomSpawners();
}
