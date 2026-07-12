package com.rfizzle.respite.phantom;

/**
 * Pure eligibility core for the phantom rework ({@code design/SPEC.md} §3). No
 * Minecraft types: the live spawner reads level and player state, this decides
 * whether a given player is an eligible spawn anchor. Fabric-free so JUnit
 * reaches it without a game.
 */
public final class PhantomMath {

    private PhantomMath() {
    }

    /**
     * Whether a player is an eligible phantom anchor this tick. True when all of:
     * it is night; the player is in survival or adventure (not creative or
     * spectator), awake, and has open sky above; and <em>either</em> their feet
     * sit strictly above {@code altitudeMin} <em>or</em> it is a new moon with
     * the new-moon rule enabled.
     *
     * @param night                 the Overworld is past dusk
     * @param survivalOrAdventure   the player's game mode is survival or adventure
     * @param sleeping              the player is asleep in a bed
     * @param skyAccess             the player's column can see the sky
     * @param feetY                 the player's feet block Y
     * @param altitudeMin           {@code phantomAltitudeMin} — feet must be strictly above this
     * @param newMoon               the current moon phase is the new moon
     * @param phantomNewMoonEnabled {@code phantomNewMoon} — the new-moon anchor rule is on
     */
    public static boolean isAnchorEligible(boolean night, boolean survivalOrAdventure, boolean sleeping,
            boolean skyAccess, int feetY, int altitudeMin, boolean newMoon, boolean phantomNewMoonEnabled) {
        if (!night || !survivalOrAdventure || sleeping || !skyAccess) {
            return false;
        }
        boolean aboveAltitude = feetY > altitudeMin;
        boolean newMoonAnchor = newMoon && phantomNewMoonEnabled;
        return aboveAltitude || newMoonAnchor;
    }
}
