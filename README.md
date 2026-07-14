<p align="center">
  <img src="art/logo.png" alt="Respite" width="800">
</p>

<p align="center"><strong>Make the night count.</strong></p>

<p align="center">
  <a href="https://www.minecraft.net/"><img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white"></a>
  <a href="https://fabricmc.net/"><img alt="Fabric" src="https://img.shields.io/badge/Mod_Loader-Fabric-DBB69B"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/github/license/rfizzle/respite"></a>
  <a href="https://github.com/rfizzle/respite/releases"><img alt="Latest release" src="https://img.shields.io/github/v/release/rfizzle/respite?include_prereleases"></a>
  <a href="https://github.com/rfizzle/respite/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/rfizzle/respite/actions/workflows/ci.yml/badge.svg"></a>
  <a href="https://modrinth.com/mod/respite-vitality-overhaul"><img alt="Modrinth downloads" src="https://img.shields.io/modrinth/dt/Mz4an5lJ?logo=modrinth&label=Modrinth&color=00AF5C"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/respite-vitality-overhaul"><img alt="CurseForge downloads" src="https://img.shields.io/curseforge/dt/1608507?logo=curseforge&label=CurseForge&color=F16436"></a>
</p>

A vitality overhaul for Minecraft 1.21.1 (Fabric). Respite makes sleep, rest,
and the passage of night part of the game: vanilla's bed is a skip button, but
Respite makes the night happen — sleeping accelerates time visibly while the
world keeps running, a hearty supper heals you overnight, phantoms trade their
insomnia grudge for real territory, sleeplessness wears you down, and a
copper-and-redstone timepiece reads the hour off a redstone wire. Every feature
toggles on its own, and with a feature off the world you know is behaviorally
untouched.

## Download

| [Modrinth](https://modrinth.com/mod/respite-vitality-overhaul) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/respite-vitality-overhaul) | [GitHub Releases](https://github.com/rfizzle/respite/releases) | [Website](https://respite.rfizzle.com) | [Report an issue](https://github.com/rfizzle/respite/issues) |
| --- | --- | --- | --- | --- |

---

## Features

- **Continuous Time-Lapse** — sleeping accelerates time instead of skipping it.
  Each sleeper adds an equal share of the speed — the more of you in bed, the
  faster the night runs — and the world genuinely runs through the compressed night: furnaces
  smelt, crops grow, weather rains itself out, and dawn arrives watched rather
  than skipped. Awake players keep real time in their own body, a nearby fight
  holds time at normal pace until it's settled, and a struggling server
  degrades gracefully under a per-tick millisecond budget. Action-bar lines
  and a start/settle sound pair mark the lapse — both toggleable. On a shared
  server a quiet chat whisper names who climbs into or out of bed with the
  running tally (`Alex is in bed (2/4)`), so the night stays a negotiation;
  `announceSleepVote = false` silences it. An AFK player idle a few minutes
  (tunable) drops out of the share entirely, so the night runs at the speed of
  the people actually present. `enableTimeLapse = false` restores the entire
  vanilla sleep system, `playersSleepingPercentage` included.
- **Restful Saturation** — going to bed on a full hunger bar arms the night:
  while you sleep, every 600 world ticks converts 1 point of saturation into
  half a heart — up to 10 hearts across a full night. Sleep runs deeper the
  darker the moon: the heal per point ramps from plain at the full moon up to
  double at the new moon, when it runs deepest. Vanilla food regeneration stands
  down in bed (the conversion replaces it) and the hunger bar never drops
  overnight; healing stops at the saturation floor, full health, or leaving
  the bed, and waking three hearts richer earns a quiet action-bar line. The
  time-lapse compresses the wait, never the totals.
  `enableRestfulSaturation = false` leaves sleep behaviorally vanilla.
- **Phantoms of the Heights** — insomnia no longer summons phantoms; they become a
  territorial hazard instead. They hunt survival and adventure players who are up
  high at night — feet above Y=100 with open sky — or those same players caught
  under the open night sky on a new moon, one night in eight. Sleeping players are never anchors,
  and everything about the phantom itself stays vanilla: cadence, group size, spawn
  position, daylight burning, cat avoidance, drops, and XP are untouched, so only
  when and where they appear changes. The `doInsomnia` gamerule remains the master
  phantom switch — off keeps them dead. `enablePhantomRework = false` restores
  vanilla insomnia spawning untouched.
- **Weariness** — sleeplessness wears you down in two stages. Three days without
  rest makes you Weary (natural healing 25% slower); six makes you Exhausted (50%
  slower), and your eyelids droop — a cosmetic half-second screen dim every
  couple of minutes, never full black and never within ten seconds of a fight.
  Exactly one stage shows at a time as an ordinary status-effect icon; only
  vanilla's food-driven regen is slowed (potions, beacons, instant health, and
  Restful Saturation are untouched), and no monsters are ever sent after the
  sleepless. Sleeping, dying, or a Caffeinated Brew clears both stages. The
  eyelid blink hides with F1 and toggles off with `showExhaustionBlink`;
  `enableWeariness = false` removes the effects and the penalty entirely. The
  ladder's positive pole: waking from a full night's sleep — bed or bedroll —
  grants **Well-Rested**, a beneficial status effect that heals you 50% faster
  for two minutes (it reads as one morning with Restful Saturation, not a double
  reward — the night heals you in bed, the grace only speeds recovery if you
  still wake hurt). Tuned with `enableWellRested`, `wellRestedSeconds` (120), and
  `wellRestedRegenBonus` (0.5); off restores untouched vanilla waking.
- **The Chronometer** — a copper-and-redstone timepiece block. Its wire signal
  climbs 1–15 with the hour (each level lasts 80 seconds), a comparator instead
  reads moon fullness (0 on the new moon, 15 on the full), and its dial face
  sweeps the sun and moon across the day. Right-click reads out the exact time —
  plus the moon phase and new-moon countdown at night — and sneak-right-click
  sets an alarm hour, at which the block rings a bell once. In dimensions without
  a day-night cycle it honestly emits nothing. Ring a clock with copper for the
  **pocket chronometer**, a carried timepiece whose tooltip reads the hour — plus
  the moon phase and new-moon countdown at night — alongside your own days awake:
  the portable, read-only half of the same instrument. `enableChronometer = false`
  removes both recipes; placed blocks and held items keep working.
- **The Caffeinated Brew** — deliberate counterplay to Weariness, never the
  brewing stand. Craft an Unsteeped Brew from a water bottle, two cocoa beans,
  and any leaves, then steep it over a campfire for 30 seconds into the
  Caffeinated Brew. Drinking it clears Weary or Exhausted, resets your
  days-awake count, and grants 90 seconds of Haste I (configurable), returning
  the empty bottle — no hunger or saturation restored, since it is a drink and
  not food. It postpones rest without replacing it. Both recipes are ordinary
  crafting and campfire recipes that show natively in recipe viewers;
  `enableCaffeinatedBrew = false` removes them while already-brewed bottles keep
  working.
- **The Bedroll** — a camp bed for the road, crafted from a row of string over a
  row of any wool. Right-click the ground to unroll it and sleep in one action;
  it rolls back into your inventory when you wake. It sleeps like a bed — the
  time-lapse runs and your days-awake count resets — but never sets your spawn,
  and overnight Restful Saturation heals at half a real bed's rate (a new moon
  under a bedroll heals like the full moon in a real bed). Every vanilla bed
  rule applies and it never explodes in the Nether; `enableBedroll = false`
  removes the recipe while placed bedrolls keep working.

Alongside the features, Respite ships a small `respite` advancement tab (sleep
through a time-lapse night, heal eight hearts overnight, pull a night shift on
the brew, down a phantom at altitude, place a Chronometer, sleep through a new
moon).

See the [full feature list](https://respite.rfizzle.com/features.html) on the
website for every behavior, tuning knob, and edge case.

---

## Installation

### Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.16.10+**
- Fabric API **0.116.1+1.21.1** or newer
- Java **21**

### Setup

Drop the jar into the `mods/` directory on both server and client. Respite must
be present on **both sides** — the client renders the Chronometer dial, the
weariness eyelid dim, and the pocket-chronometer tooltip from server-synced
state.

Optionally install [Mod Menu](https://modrinth.com/mod/modmenu) and
[Cloth Config](https://modrinth.com/mod/cloth-config) for the in-game settings
screen.

---

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/respite status` | 0 | Show your own days awake and weariness stage |
| `/respite reload` | 2 | Hot-reload the config from disk |
| `/respite rest clear [player]` | 2 | Clear weariness and reset days-awake (self or a player) |
| `/respite rest set <days> [player]` | 2 | Set the days-awake counter (self or a player) |

[Full command reference →](https://respite.rfizzle.com/commands.html)

---

## Configuration

The mod generates `config/respite.json` on first launch with sensible defaults.
Every feature is independently toggleable and every value can be tuned without a
restart using `/respite reload`.

[Full config reference →](https://respite.rfizzle.com/config.html)

---

## Optional integrations

Respite detects and integrates with these mods when present (none are bundled):

- [Mod Menu](https://modrinth.com/mod/modmenu) — config screen entry
- [Cloth Config](https://modrinth.com/mod/cloth-config) — settings GUI
- [Jade](https://modrinth.com/mod/jade) /
  [WTHIT](https://modrinth.com/mod/wthit) — Chronometer probe tooltips

The Caffeinated Brew's crafting and campfire recipes are ordinary recipes and
show natively in any recipe viewer (EMI, REI, JEI) without an adapter.

---

## Building from Source

```sh
./gradlew build          # produces build/libs/respite-<version>.jar
./gradlew test           # runs unit tests
./gradlew runGametest    # runs Fabric gametest suite
./gradlew runClient      # launch dev client
./gradlew runServer      # launch dev server
```

See [`AGENTS.md`](AGENTS.md) for the full source layout, conventions, and the
suite-wide standards this repo conforms to.

---

## For Mod Developers

Respite publishes a stable, read-only integration surface under
`com.rfizzle.respite.api`, following the
[Concord API Standard](https://github.com/rfizzle/concord/blob/master/API-STANDARD.md).
Use it as a soft dependency: compile against the mod with `modCompileOnly` and
guard every call with `FabricLoader.isModLoaded("respite")`. Everything outside
the `api` package is internal and may change in any release. Reads are
server-authoritative; Respite has no HUD slot by design, so the surface ships no
HUD accessors.

### Gradle Setup

```gradle
dependencies {
    modCompileOnly "maven.modrinth:respite-vitality-overhaul:<version>"
}
```

### The Stable Surface

`RespiteAPI` accessors:

- `getTimeLapseRate(ServerLevel)` / `isTimeLapseActive(ServerLevel)` — the effective time-lapse acceleration, Overworld-only.
- `getTicksSinceRest(ServerPlayer)`, `isWeary(ServerPlayer)`, `isExhausted(ServerPlayer)`, `isWellRested(ServerPlayer)` — the Weariness state.
- `getChronometerSignal(Level)` — the Chronometer's hour signal 1–15 (what a wire reads) for the level's day time; 0 in fixed-time dimensions. A comparator reads moon fullness instead, not this value.

Array-backed Fabric events (server-side):

- `RespiteTimeLapseCallback` — fires on every effective-rate change, start and end included.
- `RespiteRestCallback` — fires when a player wakes at dawn having slept.

### Usage Examples

**Reading the time-lapse rate (server-side):**

```java
if (FabricLoader.getInstance().isModLoaded("respite")) {
    int rate = com.rfizzle.respite.api.RespiteAPI.getTimeLapseRate(serverLevel);
}
```

**Reacting to rate changes instead of polling:**

```java
if (FabricLoader.getInstance().isModLoaded("respite")) {
    com.rfizzle.respite.api.RespiteTimeLapseCallback.EVENT.register((level, oldRate, newRate, sleeping, total) -> {
        // start (0 -> N), settle (N -> 0), and every step between
    });
}
```

**Reading a player's Weariness state:**

```java
if (FabricLoader.getInstance().isModLoaded("respite")) {
    boolean exhausted = com.rfizzle.respite.api.RespiteAPI.isExhausted(serverPlayer);
    long ticksAwake = com.rfizzle.respite.api.RespiteAPI.getTicksSinceRest(serverPlayer);
}
```

---

## Part of Concord

Part of [Concord](https://github.com/rfizzle/concord) — a modular collection of system overhauls.
Install any, combine all.

- [Meridian](https://meridian.rfizzle.com) — Chart your enchantments.
- [Mercantile](https://mercantile.rfizzle.com) — Every villager remembers.
- [Tribulation](https://tribulation.rfizzle.com) — Survive what comes next.
- [Prosperity](https://prosperity.rfizzle.com) — Every chest, yours to discover.

---

## License

Licensed under the [MIT License](LICENSE). © 2026 rfizzle. Respite is not
affiliated with Mojang Studios or Microsoft.
