<p align="center">
  <img src="art/logo.png" alt="Respite" width="800">
</p>

<p align="center"><strong>Make the night count.</strong></p>

<p align="center">Respite — vitality overhaul</p>

<p align="center">
  <a href="https://www.minecraft.net/"><img alt="Minecraft 1.21.1" src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white"></a>
  <a href="https://fabricmc.net/"><img alt="Fabric" src="https://img.shields.io/badge/Mod_Loader-Fabric-DBB69B"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/License-MIT-blue"></a>
</p>

Respite is in development. The player-experience promise lives in
[`design/VISION.md`](design/VISION.md), the behavioral contract in
[`design/SPEC.md`](design/SPEC.md); this page describes features as they ship.
The website is [respite.rfizzle.com](https://respite.rfizzle.com).

## Shipped so far

- **Continuous Time-Lapse** — sleeping accelerates time instead of skipping it.
  Each sleeper adds an equal share of the speed (up to 60× with everyone in
  bed), and the world genuinely runs through the compressed night: furnaces
  smelt, crops grow, weather rains itself out, and dawn arrives watched rather
  than skipped. Awake players keep real time in their own body, a nearby fight
  holds time at normal pace until it's settled, and a struggling server
  degrades gracefully under a per-tick millisecond budget. Action-bar lines
  and a start/settle sound pair mark the lapse — both toggleable.
  `enableTimeLapse = false` restores the entire vanilla sleep system,
  `playersSleepingPercentage` included.
- **Restful Saturation** — going to bed on a full hunger bar arms the night:
  while you sleep, every 600 world ticks converts 1 point of saturation into
  half a heart — up to 10 hearts across a full night, and double per point
  under a new moon, when sleep runs deepest. Vanilla food regeneration stands
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
  `enableWeariness = false` removes the effects and the penalty entirely.
- **The Chronometer** — a copper-and-redstone timepiece block. It emits redstone
  power 1–15 that climbs with the hour (each level lasts 80 seconds; comparators
  read the same value), its dial face sweeps the sun and moon across the day,
  and a right-click reads out the exact time — plus the moon phase and new-moon
  countdown at night. In dimensions without a day-night cycle it honestly emits
  nothing. `enableChronometer = false` removes the recipe; placed blocks keep
  working.
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
  under a bedroll heals like an ordinary night in a real bed). Every vanilla bed
  rule applies and it never explodes in the Nether; `enableBedroll = false`
  removes the recipe while placed bedrolls keep working.

Alongside the features, Respite ships a small `respite` advancement tab (sleep
through a time-lapse night, heal eight hearts overnight, pull a night shift on
the brew, down a phantom at altitude, place a Chronometer, sleep through a new moon) and the
`/respite` command tree: `status` for any player, and op-only `reload` and
`rest` testing levers. See the [command reference](https://respite.rfizzle.com/commands).

---

## For developers

Respite publishes a stable, read-only integration surface under
`com.rfizzle.respite.api`, per the [Concord API Standard](https://github.com/rfizzle/concord/blob/master/API-STANDARD.md).
Reads are server-authoritative. Respite has no HUD slot by design, so the
surface ships no HUD accessors.

`RespiteAPI` accessors:

- `getTimeLapseRate(ServerLevel)` / `isTimeLapseActive(ServerLevel)` — the effective time-lapse acceleration, Overworld-only.
- `getTicksSinceRest(ServerPlayer)`, `isWeary(ServerPlayer)`, `isExhausted(ServerPlayer)` — the Weariness state.
- `getChronometerSignal(Level)` — the Chronometer signal 1–15 for the level's day time; 0 in fixed-time dimensions.

Array-backed Fabric events (server-side):

- `RespiteTimeLapseCallback` — fires on every effective-rate change, start and end included.
- `RespiteRestCallback` — fires when a player wakes at dawn having slept.

Consume as a soft dependency — compile against the release jar and guard every
call site with an `isModLoaded` check:

```gradle
repositories {
    ivy {
        url = 'https://github.com'
        patternLayout { artifact '/[organisation]/[module]/releases/download/v[revision]/[module]-[revision].jar' }
        metadataSources { artifact() }
        content { includeGroup 'rfizzle' }
    }
}
dependencies {
    modCompileOnly "rfizzle:respite:${project.respite_version}"
}
```

```java
if (FabricLoader.getInstance().isModLoaded("respite")) {
    int rate = RespiteAPI.getTimeLapseRate(serverLevel);
}
```

---

## Development

```sh
./gradlew build          # produces build/libs/respite-<version>.jar
./gradlew test           # runs unit tests
./gradlew runGametest    # runs Fabric gametest suite
./gradlew runClient      # launch dev client
./gradlew runServer      # launch dev server
```

See [`AGENTS.md`](AGENTS.md) for source layout, conventions, and the suite-wide
standards this repo conforms to.

---

## Part of Concord

Part of [Concord](https://github.com/rfizzle/concord) — a modular collection of system overhauls.
Install any, combine all.

---

## License

Licensed under the [MIT License](LICENSE). © 2026 rfizzle. Respite is not
affiliated with Mojang Studios or Microsoft.
