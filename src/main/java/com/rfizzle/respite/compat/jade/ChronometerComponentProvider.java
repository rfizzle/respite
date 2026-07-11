package com.rfizzle.respite.compat.jade;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.chronometer.ChronometerLines;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The Jade Chronometer line — clock time and signal, with the night moon
 * addition. Tooltip-only: day time, dimension type, and the power property are
 * all vanilla-synced client state, so no server data request is needed and the
 * line is byte-identical to the WTHIT one because both call
 * {@link ChronometerLines}.
 */
public enum ChronometerComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.add(ChronometerLines.build("tooltip.respite.chronometer", accessor.getLevel()));
    }

    @Override
    public ResourceLocation getUid() {
        return Respite.id("chronometer");
    }
}
