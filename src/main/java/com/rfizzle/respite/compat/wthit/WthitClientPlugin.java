package com.rfizzle.respite.compat.wthit;

import com.rfizzle.respite.block.ChronometerBlock;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IWailaClientPlugin;

public final class WthitClientPlugin implements IWailaClientPlugin {

    @Override
    public void register(IClientRegistrar registrar) {
        registrar.body(ChronometerWthitProvider.INSTANCE, ChronometerBlock.class);
    }
}
