package com.rfizzle.respite.gametest;

import com.rfizzle.respite.Respite;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Trivial skeleton gametest — exercises the Fabric Gametest wiring end to end. */
public class RespiteSmokeGameTest implements FabricGameTest {

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void modLoads(GameTestHelper helper) {
        if (!FabricLoader.getInstance().isModLoaded(Respite.MOD_ID)) {
            helper.fail("respite is not loaded");
        }
        helper.succeed();
    }
}
