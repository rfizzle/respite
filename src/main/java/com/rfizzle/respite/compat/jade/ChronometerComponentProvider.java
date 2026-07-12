package com.rfizzle.respite.compat.jade;

import com.rfizzle.respite.Respite;
import com.rfizzle.respite.block.ChronometerBlock;
import com.rfizzle.respite.chronometer.ChronometerLines;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The Jade Chronometer line — clock time and signal, with the night moon
 * addition and any set alarm hour. Tooltip-only: day time, dimension type, and
 * the {@code power}/{@code alarm_hour} properties are all vanilla-synced client
 * state, so no server data request is needed and the line is byte-identical to
 * the WTHIT one because both call {@link ChronometerLines}.
 */
public enum ChronometerComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        tooltip.add(ChronometerLines.build("tooltip.respite.chronometer", accessor.getLevel(),
                accessor.getBlockState().getValue(ChronometerBlock.ALARM_HOUR)));
    }

    @Override
    public ResourceLocation getUid() {
        return Respite.id("chronometer");
    }
}
