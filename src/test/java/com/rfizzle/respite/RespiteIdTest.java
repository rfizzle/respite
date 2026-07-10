package com.rfizzle.respite;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Trivial skeleton test — exercises the JUnit + fabric-loader-junit wiring. */
class RespiteIdTest {

    @Test
    void idUsesTheModNamespace() {
        ResourceLocation id = Respite.id("chronometer");
        assertEquals("respite", id.getNamespace());
        assertEquals("chronometer", id.getPath());
    }

    @Test
    void modIdIsStable() {
        assertEquals("respite", Respite.MOD_ID);
    }
}
