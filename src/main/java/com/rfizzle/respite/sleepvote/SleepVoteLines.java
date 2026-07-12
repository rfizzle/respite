package com.rfizzle.respite.sleepvote;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * The sleep-whisper chat lines ({@code design/SPEC.md} §1, Sleep whisper). One
 * {@link Component} shell for both the enter and leave line, so the two stay
 * identically shaped by construction. A {@code message.respite.*} surface — a
 * plain chat line pushed by the mod, muted to gray and carrying no ✦ marker
 * (that glyph is reserved for the action-bar {@code notification.respite.*}
 * surface, per concord DESIGN-SYSTEM §10).
 */
public final class SleepVoteLines {

    /** "%s is in bed (%s/%s)" — a player just climbed in. */
    public static final String ENTER_KEY = "message.respite.sleep_vote_enter";
    /** "%s left the bed (%s/%s)" — a player got back out while the night still counts. */
    public static final String LEAVE_KEY = "message.respite.sleep_vote_leave";

    private SleepVoteLines() {
    }

    /**
     * The whisper for one bed transition: the actor's display name, then the
     * post-transition share of players in bed.
     *
     * @param enter    true for the climbed-in line, false for the got-out line
     * @param name     the actor's display name (may carry team color / prefixes)
     * @param sleeping players in bed after this transition
     * @param total    non-spectator players present
     */
    public static Component build(boolean enter, Component name, int sleeping, int total) {
        return Component.translatable(enter ? ENTER_KEY : LEAVE_KEY, name, sleeping, total)
                .withStyle(ChatFormatting.GRAY);
    }
}
