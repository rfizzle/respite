package com.rfizzle.respite.compat.jade;

import com.rfizzle.respite.block.ChronometerBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin("respite")
public class RespiteJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ChronometerComponentProvider.INSTANCE, ChronometerBlock.class);
    }
}
