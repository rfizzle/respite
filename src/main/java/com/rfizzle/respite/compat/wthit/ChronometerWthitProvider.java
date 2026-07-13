package com.rfizzle.respite.compat.wthit;

import com.rfizzle.respite.block.ChronometerBlock;
import com.rfizzle.respite.chronometer.ChronometerLines;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;

/**
 * The WTHIT Chronometer line — pure delegation to {@link ChronometerLines},
 * so it can never drift from the Jade line. Tooltip-only: everything the line
 * needs is vanilla-synced client state.
 */
public enum ChronometerWthitProvider implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        tooltip.addLine(ChronometerLines.build("tooltip.respite.chronometer", accessor.getLevel(),
                accessor.getBlockState().getValue(ChronometerBlock.ALARM_HOUR)));
    }
}
