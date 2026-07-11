# Respite — Feature Spec

Minecraft 1.21.1 Fabric mod. Vitality overhaul — sleep, rest, and the passage of night.

**Architectural philosophy:** One clock, honestly accelerated. Respite never forks, fakes, or jumps time. The time-lapse works by running the real Overworld tick loop additional times per server tick under a hard millisecond budget, so every system — furnaces, crops, brewing stands, mobs, weather, redstone, scheduled ticks — experiences a genuine night, just compressed. `dayTime` is never set directly (vanilla's sleep skip *is* a time jump, and Respite replaces it); every other feature reads time the vanilla way (`getDayTime`, moon phase, `TIME_SINCE_REST`). All gameplay decisions run server-side. The client's one custom-drawn surface is the Exhausted blink (§4) — a transient, purely cosmetic screen fade keyed off a synced status effect; everything else needs no custom rendering — an accelerated sky is just vanilla sky rendering fed faster time updates. When every feature toggle is off, a Respite install is behaviorally byte-identical to vanilla.

**Asset philosophy:** Custom pixel art through Concord's glyph pipeline (concord `design/DESIGN-SYSTEM.md` §8) for everything Respite adds a face to: the Chronometer block textures, the two brew items, the Weary and Exhausted effect icons, and a 16×16 lantern glyph for Jade/recipe-viewer contexts. No vanilla texture is replaced. Sounds stay vanilla where the cue is organic (drinking, block placement, bed rustle); the one custom synthesized pair (via the `/sfx` pipeline, §9) is the time-lapse onset/settle cue, where no vanilla sound expresses "time itself is moving". Look and brand live in `design/DESIGN.md`; file locations in `design/ASSETS.md`.

---

## 1. Continuous Time-Lapse

Sleeping accelerates time instead of skipping it. Replaces vanilla's instant night skip.

### Problem

Vanilla sleep is a discontinuity: the world state at dawn is the world state at dusk plus nothing. Furnaces don't smelt, crops don't grow, and multiplayer sleep is all-or-nothing — one insomniac holds the whole server's night hostage (or `playersSleepingPercentage` silently overrules the minority). The night is dead time the game deletes rather than plays.

### Behavior

Evaluated once per real server tick, server-side:

1. **Eligibility** — the time-lapse can run in the Overworld when `doDaylightCycle` is true and vanilla would allow sleep to matter: it is night, or a thunderstorm is active during the day.
2. **Rate** — let `n` = non-spectator players currently in the Overworld and `k` = those with `isSleeping() == true` (in bed, at any stage of falling asleep). The target rate is:

   `rate = max(1, round(maxTimeLapseRate × k / n))`

   With the default `maxTimeLapseRate = 60` and `n = 4`: 1 sleeper → 15×, 2 → 30×, 3 → 45×, 4 → 60×. A solo player gets the full 60×. `k = 0` (or `n = 0`) → rate 1, time-lapse inactive.

   **Peril brake** — while `combatHoldsTime` is true and any awake player counted in `n` is *in peril*, the rate clamps to 1: extra ticks pause, sleepers stay in bed, and the lapse resumes when the peril ends. A player is in peril when a hostile mob's current attack target is that player, or the player dealt or took damage within the last 100 **real** server ticks (5 s — real ticks, not world ticks, so the window is never compressed by the lapse itself). While the brake holds an otherwise-active lapse, Overworld players see `✦ Time holds — battle nearby` (`notification.respite.time_hold`) in place of the rate line, under the same `announceTimeLapse` / `showTimeLapseMessages` toggles.
3. **Vanilla skip suppressed** — while `enableTimeLapse` is true, vanilla's "enough players sleeping → set time to morning, wake everyone, clear weather" path never fires. The `playersSleepingPercentage` gamerule consequently has no effect (see Gamerules below). Players simply stay asleep in bed.
4. **Extra ticks** — each real server tick, Respite runs up to `rate − 1` additional Overworld ticks (world time, block entities, entities, block/fluid scheduled ticks, random ticks, weather, raids, spawning — the full per-dimension tick), subject to the performance governor below. Day time advances `rate` ticks per real tick in total.
5. **Natural wake** — players wake through vanilla's own logic when the night (or thunderstorm) ends. Weather is *not* force-cleared; an accelerated storm rains itself out on its own schedule. Morning arrives; no state was skipped.
6. **Rate changes** — recomputed every real tick. A player leaving their bed, joining, leaving the server, or switching dimension takes effect on the next tick. `RespiteTimeLapseCallback` fires whenever the effective rate changes (§ Public API).

A full night (12,000 ticks ≈ 10 real minutes) passes in ≈10 seconds at 60×. A furnace smelts 60 items over that night — same as vanilla would across a real 10-minute night — because the ticks are real.

### Performance governor

Extra ticks are metered, never assumed free:

- Per real server tick, Respite measures wall-clock time spent on extra ticks and stops issuing them once `timeLapseTickBudgetMs` (default 40 ms) is spent, even if fewer than `rate − 1` ran. The **effective rate** that tick is `1 + extras actually run`.
- If the base tick alone is already over budget (a struggling server), zero extras run that tick and the night passes at whatever pace the server can afford — degradation is gradual, never a stall or a death spiral.
- The effective rate (not the target) is what `/respite status`, the action-bar announcement, the API, and the callback report.

### Multiplayer & fairness

- The 1/n share is the fairness contract: nobody's sleep is wasted and nobody's insomnia vetoes the night. Two of four asleep = a half-speed-of-max night for everyone, including the two still awake.
- **Awake players keep real time in their own body** — during extra ticks, player-attached ticking is skipped for non-sleeping players: hunger/exhaustion accrual, status-effect duration decrement, air supply, fire ticks, and natural-regen cadence advance only on real ticks. Their entity still moves, collides, and interacts with the accelerated world; only their attached timers are exempt. Sleeping players receive full extra ticks (Restful Saturation §2 counts on it; they are safe in bed). Stated trade-off: an awake player's potion outlasts the compressed night in game-time terms; accepted — the alternative (a 90 s effect evaporating in 3 real seconds because teammates slept) weaponizes sleep against the awake.
- **The peril brake is the combat-fairness contract** — accelerated mob AI against real-time human reaction is unwinnable, so the night never rushes past a live fight. The brake is deliberately veto-like but paid-for: holding mob aggro to stall the night means actually being hunted. Servers that disagree set `combatHoldsTime = false`.
- Players in the Nether or End neither count toward `n` nor receive extra ticks (see Dimensions below), so an Overworld crew can't accelerate a dimension a teammate is fighting in.
- Spectators are excluded from `n` (matching vanilla sleep accounting).
- When the effective rate changes, players in the Overworld get an action bar line — `✦ Time ×30 — 2 of 4 asleep` (`notification.respite.time_lapse`), and `✦ Time settles` when it ends (`notification.respite.time_lapse_end`). Server toggle `announceTimeLapse`; client toggle `showTimeLapseMessages`.

### Dimensions

Extra ticks run for the **Overworld only**. The Nether and End tick at the normal rate throughout, so mobs there never rush an absent-minded teammate at 60×. Accepted trade-off, stated plainly: cross-dimension contraptions (a Nether-side farm) do not accelerate during the time-lapse, and machinery keyed to game-time deltas observed from another dimension will see the Overworld's clock run ahead. Global day time is Overworld-owned in vanilla, so the day/night position stays coherent everywhere.

### Gamerules & vanilla interactions

- `doDaylightCycle = false` — the time-lapse never activates (there is no time to advance). Sleeping retains its vanilla non-skip effects: spawn point, phantom-stat reset.
- `playersSleepingPercentage` — no effect while `enableTimeLapse` is true; the proportional rate replaces the threshold model. Documented in the config tooltip. Setting `enableTimeLapse = false` restores the full vanilla sleep system, gamerule included.
- **Bed rules untouched** — monsters nearby, distance, obstruction, "you can only sleep at night": all vanilla checks apply unchanged. Respite changes what sleep *does*, never when it's allowed.
- `/time set` during an active lapse — takes effect normally; acceleration continues from the new time (or ends, if the new time is day).
- **Mob spawning during extra ticks** runs the vanilla spawn cycle per tick, so per-game-hour spawn density over the night is unchanged — the night is compressed, not intensified.
- **Sleeping players remain vulnerable** per vanilla: damage wakes them. A mob that wanders in mid-night reaches them sooner in real time (the night is faster); in game time nothing changed. Waking from damage puts the player in peril (damage taken), so the peril brake engages and the night holds while they defend themselves.

### Failure paths

- Recursion guard: extra ticks must never re-enter the accelerator; the rate evaluation runs only on real ticks.
- If another mod manipulates the server tick loop (Carpet's `/tick`, tick-warp mods), behavior is undefined; see Compatibility.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableTimeLapse` | bool | `true` | — |
| `maxTimeLapseRate` | int | `60` | 2–100 |
| `timeLapseTickBudgetMs` | int | `40` | 5–45 |
| `combatHoldsTime` | bool | `true` | — |
| `announceTimeLapse` | bool | `true` | — |

### Implementation Notes

- A `TimeLapseEngine` invoked from the server tick loop (mixin at the tail of `MinecraftServer#tickChildren` or a `ServerTickEvents.END_SERVER_TICK` listener — prefer the Fabric event unless ordering forces the mixin): computes the rate, then loops the Overworld `ServerLevel.tick(...)` + time advance under the budget, with a re-entrancy flag.
- Vanilla skip suppression: mixin into the `SleepStatus`/`ServerLevel` sleep-resolution path (the block that calls `setDayTime` and `wakeUpAllPlayers`), no-op'd while `enableTimeLapse` is true. Players' individual sleep timers still run so vanilla wake-at-dawn works.
- Player network/keep-alive ticking stays on the real cadence — only the dimension tick is repeated, not connection handling.
- Effective-rate state lives on the engine; `RespiteTimeLapseCallback` (Fabric `Event`, array-backed) fires on change, server-side.
- Peril tracking stays allocation-free on the hot path: a `Mob#setTarget` mixin maintains a per-player count of hostile mobs currently targeting them, and dealt/took damage timestamps (real-tick clock) live on the same per-player transient state — the rate evaluation reads both, no entity scan.
- The awake-player exemption keys off an "extra tick in progress" flag on the engine: during extra ticks the player tick runs, but `FoodData` ticking, effect-duration decrement, and air/fire/regen bookkeeping are skipped for non-sleeping players.

---

## 2. Restful Saturation

Going to bed on a full stomach converts saturation into overnight healing.

### Problem

Vanilla makes saturation invisible and pre-sleep eating meaningless: sleep is instant, so no regeneration happens "overnight", and a hearty meal before bed is pure flavor. The vision promises a mechanical reason to cook supper.

### Behavior

1. **Arming** — evaluated at the moment a player starts sleeping: armed if their food level is 20 (a full hunger bar). With `restfulRequiresFullHunger = false`, the gate relaxes to food ≥ 18 (vanilla's natural-regen threshold).
2. **Conversion** — while an armed player sleeps, every `restfulHealIntervalTicks` **world ticks** (default 600) of sleep: if saturation ≥ 1.0 and health < max, consume 1.0 saturation and heal 1.0 health (half a heart). The interval counts world ticks, so the time-lapse compresses the real-time wait but never changes the totals: a full 12,000-tick night is 20 conversion steps — up to 10 hearts for 20 saturation.
3. **Deep Sleep** — when the night is a new moon (moon phase index 4), each conversion heals `1.0 × newMoonHealMultiplier` health (default 2.0 — a full heart per point of saturation) for the same 1.0 saturation. The multiplier is read per conversion step from the level's current moon phase; a full-health heal from 10 saturation is the headline case.
4. **Vanilla regen suspended while sleeping** — Respite's conversion replaces food-based natural regeneration for the sleeping player (no double-dipping, and the overnight heal is predictable). Regen resumes normally on wake.
5. **No hunger drain in bed** — food exhaustion does not accrue while sleeping; the food level itself never drops overnight. Only saturation is spent, by the conversion.
6. **Stop conditions** — conversion halts when saturation < 1.0, health is full, or the player leaves the bed. Healing already applied is kept (no clawback on interrupted sleep).
7. **Wake feedback** — if a night's sleep restored ≥ 6 health (3 hearts), the player gets `✦ You wake refreshed` (`notification.respite.rested`) on the action bar at wake; if any Deep Sleep conversion ran that night, the line is `✦ You wake deeply rested` (`notification.respite.deep_rested`) instead, same threshold.

### Edge cases

- **Multiplayer** — per-player and independent: each sleeper's arming, saturation, and healing are their own, regardless of who else sleeps or what the time-lapse rate is.
- **Damage while sleeping** — vanilla: damage wakes the player; conversion stops with the wake. Poison/wither ticking mid-sleep interleaves with conversion normally.
- **Peaceful difficulty** — peaceful's own ambient regeneration is untouched; the conversion still runs (harmlessly redundant).
- **Weariness interaction** — the conversion is exempt from the Weariness ladder's regen penalties (§4); it is the cure path, not "natural regeneration".
- **Blood Moon disjointness** — Deep Sleep keys to the new moon (phase 4); Tribulation's Blood Moon keys to the full moon (phase 0). The two lunar events can never coincide (see Compatibility).
- **Persistence** — the armed flag and interval counter are transient (not written to the player's saved data). A server stopping mid-sleep loses at most one partial interval; accepted.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableRestfulSaturation` | bool | `true` | — |
| `restfulRequiresFullHunger` | bool | `true` | — |
| `restfulHealIntervalTicks` | int | `600` | 100–2400 |
| `newMoonHealMultiplier` | double | `2.0` | 1.0–4.0 |

### Implementation Notes

- A per-player transient state object (armed flag, tick accumulator, health-restored tally) held in a server-side map keyed by UUID, cleared on wake/disconnect.
- Hook the sleeping branch of the player tick; suspend vanilla regen with a guard in the `FoodData` tick for sleeping players (mixin or the entity-tick event, whichever stays smallest).
- Saturation/health mutations go through the vanilla `FoodData`/`setHealth` setters so other mods' hooks observe them.

---

## 3. Phantoms of the Heights

Insomnia is removed; phantoms become a territorial hazard of altitude and the new moon.

### Problem

Vanilla ties phantoms to a hygiene stat: skip sleep three days and the sky punishes you. It's the most widely gamerule'd-off feature in the game, and it makes phantom membrane an anti-reward for tedium. Phantoms deserve a territory, not a grudge.

### Behavior

1. **Insomnia off** — vanilla's phantom spawner never runs while `enablePhantomRework` is true. `TIME_SINCE_REST` no longer causes any spawn (it drives Weariness instead, §4).
2. **Respite's spawner** — server-side, Overworld only, at vanilla's cadence (a check per player every 1–2 minutes, weighted by local difficulty). A player is an eligible anchor when **all** of:
   - it is night;
   - the player is in survival or adventure mode, not sleeping, and has sky access above them;
   - **either** the player's feet are above `phantomAltitudeMin` (default Y=100), **or** it is a new moon (moon phase index 4 — one night in eight) and `phantomNewMoon` is true.
3. **Spawn mechanics vanilla** — group size (1 to 1+local-difficulty, max 4), spawn position (20–34 blocks above the anchor, sky-visible), and everything about the phantom itself (daylight burning, cat avoidance, swoop AI, drops, XP) are unchanged vanilla behavior. Respite changes *when and where* phantoms appear, never *what they are*.
4. **Membrane economy** — drop rates untouched; acquisition shifts from insomnia-farming to hunting the heights or braving a new moon.

### Edge cases

- **`doInsomnia` gamerule** — respected as the master phantom switch: `doInsomnia = false` disables Respite's spawner too. A server that killed phantoms keeps them dead.
- **`enablePhantomRework = false`** — full vanilla parity: vanilla's insomnia spawner runs untouched, Respite's never does.
- **Sleeping players are never anchors** — sleep is safety; this also prevents phantom spawns from griefing an active time-lapse.
- **Time-lapse interaction** — the spawner rides world ticks, so an accelerated night compresses real-time exposure without changing per-night spawn counts.
- **Superflat / modified worldgen** — the altitude rule is absolute Y, no terrain awareness; servers tune `phantomAltitudeMin` to their world shape.
- **Nether/End** — no Respite phantom spawns (vanilla parity: the spawner is Overworld-only).
- **Mob cap** — Respite's spawner uses the same category accounting as vanilla's phantom spawner (phantoms spawn outside the standard monster-cap cycle, via the custom-spawner slot).

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enablePhantomRework` | bool | `true` | — |
| `phantomAltitudeMin` | int | `100` | −64–320 |
| `phantomNewMoon` | bool | `true` | — |

### Implementation Notes

- Vanilla's `PhantomSpawner` sits in `ServerLevel`'s custom-spawner list; suppress it with a targeted mixin (skip its `tick` when the rework is on) and register `RespitePhantomSpawner` (a `CustomSpawner`) alongside — vanilla's class is the structural template.
- Moon phase via `ServerLevel#getMoonPhase()` (index 4 = new moon).
- No new entity, no phantom subclass — spawns are plain `EntityType.PHANTOM` creations, so sibling mods (Tribulation scaling) treat them exactly like vanilla phantoms.

---

## 4. Weariness

Sleeplessness wears you down in two stages: three days without rest slows natural healing by 25%; six days slows it by 50% and your eyelids start to droop.

### Problem

With insomnia gone (§3), staying awake would have no cost at all, and sleep needs a gentle pull. The vision promises a debuff that respects the player: slower healing, never spawned monsters. A single mild stage would hide inside regen's noise; the second stage is where the cost crosses into a felt decision while staying short of punishment.

### Behavior

1. **Thresholds** — a player's `TIME_SINCE_REST` stat drives a two-stage ladder:
   - ≥ `wearinessThresholdDays × 24000` ticks (default 3 days = 72,000 ticks ≈ 1 real hour): the **Weary** status effect (`respite:weary`).
   - ≥ `exhaustedThresholdDays × 24000` ticks (default 6 days): the **Exhausted** status effect (`respite:exhausted`) replaces Weary. The stages are mutually exclusive — exactly one is active at a time. The effective Exhausted threshold is clamped to at least `wearinessThresholdDays + 1` days.
2. **The effects** — both neutral category, ambient, each with its own icon (assets per `DESIGN.md`); applied with indefinite duration and re-asserted by a check that runs every 100 ticks that applies whichever stage matches the stat, so neither can be permanently removed while the stat is over threshold (milk clears either for at most 5 seconds — documented, not fought).
3. **Penalty** — food-based natural regeneration heals less: each vanilla regen heal event is scaled to `amount × (1 − penalty)`, with `wearinessRegenPenalty` (default 0.25) while Weary and `exhaustedRegenPenalty` (default 0.50) while Exhausted. Unaffected: instant health, Regeneration the potion effect, beacon regen, peaceful-difficulty ambient regen, and Restful Saturation's conversion (§2).
4. **The blink (client-side, cosmetic)** — while Exhausted, the player's eyelids droop at intervals: every 90 real seconds with ±30 s uniform jitter, an eyelid-shaped fade eases in from screen top and bottom over ~0.3 s and releases over ~0.3 s, peaking at 55% occlusion — never full black, vision is never lost. **Combat-suppressed:** no blink begins within 200 client ticks (10 s) of the player taking or dealing damage; a due blink is deferred until the window clears, not skipped. No gameplay effect, no sound; hidden with F1; client toggle `showExhaustionBlink`.
5. **Clearing** — both stages lift when `TIME_SINCE_REST` drops below their thresholds, which happens when the stat resets: starting to sleep in a bed (vanilla stat semantics — a catnap counts, exactly as it did for vanilla insomnia), dying, or drinking a Caffeinated Brew (§6).

### Edge cases

- **Catnap clearing is deliberate** — Respite keeps vanilla's rest-stat semantics rather than inventing a parallel "slept a full night" tracker; the real incentive to sleep through the night is the time-lapse and Restful Saturation, not the debuff's letter.
- **Death clears it** — vanilla resets `TIME_SINCE_REST` on death; accepted (dying is, mechanically, a rest).
- **Stage transition** — crossing the Exhausted threshold swaps the effect within one 100-tick sweep; there is no frame where both icons show.
- **Creative/spectator** — the stat accrues and the icon may show, but no natural regen exists to penalize; no special-casing. The blink follows the effect, so it can show in creative; accepted (the toggle exists).
- **Multiplayer** — per-player stat, per-player effect, per-client blink; no shared state.
- **Mobs** — player-only; the effects are never applied to non-players, and commands applying them to mobs do nothing beyond the icon.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableWeariness` | bool | `true` | — |
| `wearinessThresholdDays` | int | `3` | 1–30 |
| `wearinessRegenPenalty` | double | `0.25` | 0.0–0.95 |
| `exhaustedThresholdDays` | int | `6` | 2–60 |
| `exhaustedRegenPenalty` | double | `0.50` | 0.0–0.95 |

Client: `showExhaustionBlink` (see Configuration).

### Implementation Notes

- `WearyEffect` and `ExhaustedEffect` extend `MobEffect` (neutral), registered `respite:weary` / `respite:exhausted`; a 100-tick server task sweeps online players' `Stats.TIME_SINCE_REST` and applies the matching stage, removing the other.
- The regen scale hooks the natural-regeneration branch of `FoodData#tick` (the `heal` call gated on food ≥ 18), not `LivingEntity#heal` generally — the penalty must never touch potion or beacon healing; the factor resolves from the active stage.
- The blink is client-only: a screen-space gradient fill (no texture) drawn from a `HudRenderCallback`, keyed off the synced `respite:exhausted` effect instance; the jitter timer and combat-suppression window (local hurt/attack observations) live on the client, and nothing is networked beyond the vanilla effect sync.

---

## 5. The Chronometer

A copper-and-redstone timepiece block emitting a signal that tracks the time of day.

### Problem

Vanilla's only time-automation primitive is the daylight detector, which reads *sky light* — weather-corrupted, latitude-coarse, and nearly information-free at night. Building "lock the doors at dusk" requires contraption folklore. The clock item knows the time; no block will say it.

### Behavior

1. **Block** — `respite:chronometer`, a full solid block. Any tool (or none) breaks it quickly; it always drops itself. Not flammable, not movable-with-inventory concerns (it holds no items); pistons move it like stone.
2. **Recipe** — shaped, 3×3, yields 1:

   ```
   copper ingot | copper ingot | copper ingot
   redstone dust|    clock     | redstone dust
   smooth stone | smooth stone | smooth stone
   ```
3. **Signal** — the block is a redstone power source (redstone-block semantics: powers adjacent wires and components at its level; does not strongly power neighbors through blocks). Its strength is a pure function of Overworld day time:

   `power = floor((dayTime mod 24000) / 1600) + 1` → 1–15

   Each level spans 1,600 ticks (80 real seconds). Anchors: level 1 begins at tick 0 (dawn), level 8 covers 11,200–12,799 (sunset at 12,000 falls inside it), level 12 covers midnight (18,000), level 15 covers 22,400–23,999 (the last stretch before dawn). A comparator reading the block sees the same 1–15 value.
4. **Updates** — the block re-checks its level on a self-rescheduled 20-tick block tick and emits neighbor updates only when the level changes (at most once per 1,600 world ticks in real time; during a 60× time-lapse a change can land every ~1.3 real seconds — still trivially cheap). Placement sets the correct level immediately.
5. **Inspect** — right-click (survival, no item consumed, no GUI): action bar `✦ 7:12 pm — signal 9` (`notification.respite.chronometer`) with the 12-hour clock derived as `hours = ((dayTime / 1000) + 6) mod 24`, minutes = `(dayTime mod 1000) × 60 / 1000`. At night (day-time position 12,000–23,999) the line gains the moon: `✦ 7:12 pm — signal 9 — waning crescent, new moon in 2 nights` (`notification.respite.chronometer_night`), with the phase name from `moon.respite.<phase>` and the count computed as `(4 − moonPhase) mod 8`; when the count is 0 the line is `✦ 7:12 pm — signal 9 — new moon tonight` (`notification.respite.chronometer_new_moon`). The Jade/WTHIT line carries the same night addition.
6. **Dial face** — the block's face texture sweeps through 8 visual phases, two signal levels per face (the blockstate JSON maps the `power` property onto the eight dial models; `power=0` is the still face). Cosmetic only; the signal keeps full 15-level precision.

### Edge cases

- **Fixed-time dimensions** (Nether, End — any dimension with `fixed_time`): power 0, dial shows a distinct "still" face. Vanilla-deferential: the clock spins uselessly there, the Chronometer honestly says nothing.
- **`doDaylightCycle = false`** — the signal freezes at the current (correct) level; time isn't advancing, so neither is the dial.
- **`/time set`** — the next block tick (≤ 20 game ticks) snaps the level to the new time.
- **Redstone semantics** — no strong powering means a lever-style "power through a wall" build needs a repeater, same as a redstone block; stated so builders aren't surprised.
- **Config-disabled** — `enableChronometer = false` disables the crafting recipe (resource condition); already-placed blocks keep functioning, because un-registering a placed block corrupts worlds. Documented in the config tooltip.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableChronometer` | bool | `true` | — |

### Implementation Notes

- `ChronometerBlock extends Block`: the current level lives in a `power` blockstate property (0–15, the daylight-detector pattern); a scheduled tick re-arms itself every 20 ticks and swaps the state only when the level changed, which is what makes neighbor updates fire exactly on change. `isSignalSource → true` with `getSignal` reading the property covers wires; `hasAnalogOutputSignal`/`getAnalogOutputSignal` are the separate pair a comparator reads — both return the same value. The dial's 8 faces are the blockstate JSON's mapping of the property.
- The level function lives in one static, pure method (`ChronometerTime.signalFor(long dayTime, boolean fixedTime)`) shared by the block, the inspect line, `/respite status`, and the API — one formula, one home.
- Recipe JSON gated by a Fabric resource condition bound to the config (evaluated at datapack load; `/respite reload` + `/reload` re-evaluates).

---

## 6. The Caffeinated Brew

A campfire-steeped cocoa drink that clears Weariness and grants a burst of Haste.

### Problem

Weariness (§4) needs counterplay that isn't "ignore it": a deliberate, craftable way to pull an all-nighter safely. It must not touch the brewing stand — potions are a different craft (and a future sibling's silo).

### Behavior

1. **Unsteeped Brew** (`respite:unsteeped_brew`) — shapeless crafting, yields 1: 1 water bottle + 2 cocoa beans + 1 block from `#minecraft:leaves`. Stack size 16. Not drinkable.
2. **Steeping** — a `campfire_cooking` recipe, 600 ticks (30 s): Unsteeped Brew → **Caffeinated Brew** (`respite:caffeinated_brew`). Campfire only — no furnace, smoker, or soul-campfire speed special-casing (soul campfires cook at the same recipe time). The wait is the point.
3. **Drinking** — 32-tick use animation (potion-style). On finish, server-side:
   - remove the Weary or Exhausted effect and reset `TIME_SINCE_REST` to 0;
   - grant Haste I for `brewHasteSeconds` (default 90 s / 1,800 ticks);
   - return an empty glass bottle (survival; creative consumes nothing, vanilla convention).
   - No hunger or saturation is restored — the brew is deliberately not food (food values stay vanilla's).
4. **Re-drinking** — refreshes the Haste duration; never escalates to Haste II. Drinking while not Weary is valid preventive use (the timer still resets). Stack size 16.

### Edge cases

- **Brewing stand** — the brew never appears in, and is never brewable at, a brewing stand; no potion registry entry, no `PotionItem` subclassing. This is the Apothecary fence, kept in code shape as well as content.
- **Existing Haste** (beacon) — vanilla effect-merge rules apply: the stronger amplifier wins; equal amplifier takes the longer duration.
- **Config-disabled** — `enableCaffeinatedBrew = false` disables both recipes (resource conditions); existing items in inventories remain drinkable (never brick a player's items).
- **Cocoa economy untouched** — cocoa farming, growth, and drops are vanilla; Respite only adds a consumer.

### Config

| Key | Type | Default | Range |
|---|---|---|---|
| `enableCaffeinatedBrew` | bool | `true` | — |
| `brewHasteSeconds` | int | `90` | 0–600 |

### Implementation Notes

- Two plain `Item`s; the Caffeinated Brew uses a consumable/food component with zero nutrition and `canAlwaysEat`, applying its effects in `finishUsingItem` (server side), returning `Items.GLASS_BOTTLE`.
- Recipes are vanilla JSON types (`crafting_shapeless`, `campfire_cooking`) with Fabric resource conditions — they display natively in EMI/REI/JEI with no plugin code.

---

## Configuration

Single JSON config `config/respite.json`, created with defaults on first launch, plus a ModMenu/Cloth Config screen when those mods are present. `configVersion` is **1**. Unknown or missing fields are filled with defaults; every numeric field is clamped to its stated range after load; a corrupted file falls back to full defaults and is left on disk untouched. Every `config.respite.*` label ships with a `.tooltip` key.

### Server config

| Key | Type | Default | Range / values | Feature |
|---|---|---|---|---|
| `enableTimeLapse` | bool | `true` | — | §1 |
| `maxTimeLapseRate` | int | `60` | 2–100 | §1 |
| `timeLapseTickBudgetMs` | int | `40` | 5–45 | §1 |
| `combatHoldsTime` | bool | `true` | — | §1 |
| `announceTimeLapse` | bool | `true` | — | §1 |
| `enableRestfulSaturation` | bool | `true` | — | §2 |
| `restfulRequiresFullHunger` | bool | `true` | — | §2 |
| `restfulHealIntervalTicks` | int | `600` | 100–2400 | §2 |
| `newMoonHealMultiplier` | double | `2.0` | 1.0–4.0 | §2 |
| `enablePhantomRework` | bool | `true` | — | §3 |
| `phantomAltitudeMin` | int | `100` | −64–320 | §3 |
| `phantomNewMoon` | bool | `true` | — | §3 |
| `enableWeariness` | bool | `true` | — | §4 |
| `wearinessThresholdDays` | int | `3` | 1–30 | §4 |
| `wearinessRegenPenalty` | double | `0.25` | 0.0–0.95 | §4 |
| `exhaustedThresholdDays` | int | `6` | 2–60 | §4 |
| `exhaustedRegenPenalty` | double | `0.50` | 0.0–0.95 | §4 |
| `enableChronometer` | bool | `true` | — | §5 |
| `enableCaffeinatedBrew` | bool | `true` | — | §6 |
| `brewHasteSeconds` | int | `90` | 0–600 | §6 |

### Client config

| Key | Type | Default | Description |
|---|---|---|---|
| `showTimeLapseMessages` | bool | `true` | Show the time-lapse action-bar lines on this client |
| `showExhaustionBlink` | bool | `true` | Show the Exhausted eyelid blink on this client (§4) |

---

## Commands

Root `/respite`, brigadier tree:

| Command | Permission | Output |
|---|---|---|
| `/respite status` | everyone (level 0) | Time-lapse state and effective rate (`Time ×30 — 2 of 4 asleep` / `Time ×1`, or the peril hold), the caller's time awake in days (one decimal), rest stage (rested / Weary / Exhausted), nights until the next new moon, and — if the caller is looking at a Chronometer — its current signal |
| `/respite reload` | op (level 2) | Reloads `config/respite.json` and re-evaluates recipe conditions; reports what changed |
| `/respite rest clear [player]` | op (level 2) | Resets the target's `TIME_SINCE_REST` to 0 (clears Weariness) |
| `/respite rest set <days> [player]` | op (level 2) | Sets the target's `TIME_SINCE_REST` to `days × 24000` (testing lever for §3/§4) |

Feedback through `command.respite.*` translation keys; op-level diagnostic dumps may be literal text (the sanctioned exception, DESIGN-SYSTEM §10).

---

## Public API

Package **`com.rfizzle.respite.api`** — the only stable surface, per concord [`API-STANDARD.md`](../../concord/API-STANDARD.md): read-only static accessors, array-backed Fabric events, server-authoritative, `@Stable`-marked (local annotation, no shared jar). Respite has no HUD slot and therefore ships no HUD accessors.

```java
public final class RespiteAPI {
    /** Effective time-lapse rate for the level; 1 when inactive. */
    public static int getTimeLapseRate(ServerLevel level);

    /** True while the time-lapse is running extra ticks for the level. */
    public static boolean isTimeLapseActive(ServerLevel level);

    /** The player's TIME_SINCE_REST in ticks. */
    public static long getTicksSinceRest(ServerPlayer player);

    /** True if the player currently has the Weary effect. */
    public static boolean isWeary(ServerPlayer player);

    /** True if the player currently has the Exhausted effect. */
    public static boolean isExhausted(ServerPlayer player);

    /** The Chronometer signal (1–15) for the level's day time; 0 in fixed-time dimensions. */
    public static int getChronometerSignal(Level level);
}
```

Events (server-side, array-backed Fabric `Event`s):

- **`RespiteTimeLapseCallback`** — `onRateChanged(ServerLevel level, int oldRate, int newRate, int sleeping, int total)`; fires on every effective-rate change, including start (old 1) and end (new 1).
- **`RespiteRestCallback`** — `onPlayerRested(ServerPlayer player, long ticksSlept, float healthRestored)`; fires when a player wakes at dawn having slept (not on interrupted sleep).

Consumption is the suite pattern: `modCompileOnly` against the published jar, every call site guarded by `FabricLoader.getInstance().isModLoaded("respite")`.

---

## Compatibility

### Required

- Minecraft 1.21.1, Java 21
- Fabric Loader ≥ 0.16.10
- Fabric API

### Optional integrations

- **ModMenu + Cloth Config** — config screen.
- **Jade / WTHIT** — one Chronometer line: current clock time and signal level (`tooltip.respite.chronometer`). Guarded plugin, absent mods cost nothing.
- **EMI / REI / JEI** — no plugin code: both recipes are vanilla recipe types and display natively.

### Concord siblings

Respite is a **provider**, and its integrations cost it no code:

- **Tribulation** — zero-integration integration: Respite phantoms are plain vanilla phantoms (§3), so Tribulation's mob scaling applies to them automatically. Nothing to ship on either side. Lunar events are disjoint by construction: Respite's new-moon rules (§2 Deep Sleep, §3 phantoms) key to moon phase 4, Tribulation's Blood Moon to phase 0 — the two can never coincide, and future lunar features on either side should keep the phases apart deliberately. A Blood Moon's sleep-block simply yields `k = 0`, so the time-lapse never runs during one (bed rules untouched, §1).
- **Mercantile** — provider only: the item IDs `respite:caffeinated_brew` / `respite:unsteeped_brew` are stable for Mercantile's conditional exclusive-trade packs (consumer-side, per the suite matrix pattern).
- **Prosperity** — provider only: the Chronometer and brew are stable injection targets for Prosperity's `loot_injections` datapacks (consumer-side).
- **Meridian** — no coupling; none invented.

No `isModLoaded` guard exists in Respite at v0.1 because no sibling code path exists; any future consumer-side compat lands in `compat/<modid>/` per the standard.

### Known conflicts & cautions

- **Sleep-replacement mods** (anything that skips, votes, or accelerates the night — Somnia-likes, sleep-vote mods): do not combine; both fight over the same vanilla seam. Behavior is undefined; documented in the README/FAQ rather than runtime-detected at v0.1.
- **Tick-loop manipulators** (Carpet `/tick`, tickrate changers): undefined interaction with the extra-tick engine; caution documented.
- **Sodium / Iris / EBE** — unaffected: Respite does no custom world rendering; the accelerated sky is vanilla rendering fed faster time.
- **Daylight detectors** — keep working, and accelerate honestly during the time-lapse like everything else.

---

## Sound Design

Triggers and subtitles only — character and files live with `DESIGN.md` / `ASSETS.md`.

| Event | Sound | Subtitle |
|---|---|---|
| Time-lapse starts (effective rate leaves 1) | `respite:ui.time_lapse.start` — custom, `/sfx`, stereo UI cue, ≤1.5 s, played non-positionally to Overworld players | `subtitles.respite.time_lapse_start` — "Time quickens" |
| Time-lapse ends (effective rate returns to 1) | `respite:ui.time_lapse.end` — custom, `/sfx`, mirror of the start cue | `subtitles.respite.time_lapse_end` — "Time settles" |
| Chronometer placed / broken | vanilla copper block sounds | vanilla |
| Chronometer inspected | vanilla `ui.button.click` at low volume, client-side | — (UI feedback, no world sound) |
| Brew drunk | vanilla `entity.generic.drink` | vanilla |
| Weary / Exhausted applied or cleared | silent (vanilla effects appear silently; Respite matches) | — |
| Time-lapse held by the peril brake | silent — the action-bar line carries it | — |
| Exhausted blink | silent | — |

Rate *changes* while the lapse stays active (3 of 4 sleepers → 2 of 4) do not re-fire the start cue — only the action-bar line updates.

---

## HUD

No HUD element, no HUD accessors — the "no slot, by design" decision and its reasoning live in `design/DESIGN.md` §2. Respite's ambient surfaces are: the vanilla status-effect icons for Weary and Exhausted, transient action-bar lines (time-lapse rate, peril hold, wake-refreshed, Chronometer inspect — all listed above), `/respite status`, and the Jade/WTHIT Chronometer line. Nothing persistent is ever drawn on the screen. The Exhausted blink (§4) is the one transient client-drawn surface — a sub-second cosmetic fade, not a HUD element: it carries no information beyond what its effect icon already shows, takes no slot, and ships no accessors.

---

## Localization

All user-facing strings are translation keys in `assets/respite/lang/en_us.json`, namespaced by surface per concord DESIGN-SYSTEM §10. The complete key inventory at v0.1:

| Key | Surface |
|---|---|
| `block.respite.chronometer` | Block name |
| `item.respite.unsteeped_brew`, `item.respite.caffeinated_brew` | Item names |
| `effect.respite.weary`, `effect.respite.exhausted` | Effect names |
| `notification.respite.time_lapse` | `✦ Time ×%s — %s of %s asleep` |
| `notification.respite.time_lapse_end` | `✦ Time settles` |
| `notification.respite.time_hold` | `✦ Time holds — battle nearby` |
| `notification.respite.rested` | `✦ You wake refreshed` |
| `notification.respite.deep_rested` | `✦ You wake deeply rested` |
| `notification.respite.chronometer` | `✦ %s — signal %s` |
| `notification.respite.chronometer_night` | `✦ %s — signal %s — %s, new moon in %s nights` |
| `notification.respite.chronometer_new_moon` | `✦ %s — signal %s — new moon tonight` |
| `moon.respite.<phase>` | The eight moon-phase names (indices 0–7: full, waning gibbous, third quarter, waning crescent, new, waxing crescent, first quarter, waxing gibbous) |
| `command.respite.*` | All command feedback (status lines, reload result, rest set/clear confirmations) |
| `config.respite.<key>` + `.tooltip` | Every config option, label + tooltip pairs |
| `tooltip.respite.chronometer` (+ `_night`, `_new_moon`) | Jade/WTHIT line — same variants as the inspect notification, without the ✦ |
| `subtitles.respite.time_lapse_start`, `subtitles.respite.time_lapse_end` | Sound subtitles |
| `advancements.respite.<id>.title` / `.description` | Advancement pairs (below) |
| `key.categories.respite` | Reserved; no keybinds ship at v0.1 |

The ✦ marker lives inside the localized values, never bolted on in code. Enum-like state (Weary yes/no in `/respite status`) routes through translation keys, never `Enum#name()`.

---

## Advancements

A small `respite` tab, granted server-side:

| Id | Title | Trigger |
|---|---|---|
| `respite:root` | "Respite" | Sleep through a night with the time-lapse active (first `RespiteRestCallback` with rate > 1 during the night) |
| `respite:beauty_sleep` | "Beauty Sleep" | Wake with ≥ 8 hearts restored by Restful Saturation in one night |
| `respite:night_shift` | "Night Shift" | Drink a Caffeinated Brew while Weary |
| `respite:mountain_watch` | "Mountain Watch" | Kill a phantom while you are above Y=100 |
| `respite:clockwork` | "Clockwork" | Place a Chronometer |
| `respite:dark_and_dreamless` | "Dark and Dreamless" | Wake at dawn from a night slept through a new moon (`RespiteRestCallback` with the night's moon phase 4) |

Titles/descriptions via `advancements.respite.*` keys; custom criteria triggers where vanilla predicates can't express the condition (root, beauty_sleep), vanilla triggers with predicates elsewhere.

---

## Testing Strategy

Tiering per the `mc-mod-testing` skill.

### Unit tests (JUnit, pure)

- Rate formula: `k/n` sweep (including k=0, n=0, n=1, rounding at every k for n=4), clamp to `maxTimeLapseRate`; peril-brake clamp and the 100-real-tick peril window decay.
- Chronometer signal function: boundary ticks 0, 1,599, 1,600, 11,200, 12,000, 17,999, 18,000, 22,400, 23,999; fixed-time → 0; the clock-time formatting math; the `(4 − phase) mod 8` new-moon countdown for all eight phases and the night-window gate for the moon line.
- Restful-saturation accounting: 20-interval night totals, stop conditions (saturation floor, full health), penalty-exemption arithmetic, Deep Sleep multiplier arithmetic (default and range extremes).
- Weariness ladder math: both thresholds, the Exhausted `wearinessThresholdDays + 1` clamp, per-stage penalty resolution, and config clamping (all ranges in the Configuration table).
- Blink scheduling math: jitter bounds (60–120 s), combat-suppression deferral (due blink fires after the window clears, never inside it).

### fabric-loader-junit

- Registration contracts: block, two items, two effects, sound events, recipe JSON validity.
- Resource contracts: every key in the Localization table present in `en_us.json`; every config label has its `.tooltip`; subtitles wired in `sounds.json`.

### Gametests (Fabric Gametest API)

- Chronometer: `/time set` sweep across all 15 levels asserts emitted power and comparator reading; fixed-time dimension asserts 0; neighbor update fires exactly on level change.
- Brew: shapeless recipe assembles; campfire converts Unsteeped → Caffeinated in 600 ticks; drinking clears a synthetically applied Weary or Exhausted, resets the stat, applies Haste 1,800 ticks, returns a bottle.
- Weariness: set `TIME_SINCE_REST` past each threshold → the matching stage applied within 100 ticks and the other absent; natural-regen heal scaled ×0.75 Weary and ×0.50 Exhausted (measured via health delta under controlled food state); stat reset → effects removed.
- Restful Saturation: simulated sleeping player with full hunger heals 1.0 HP per 600 world ticks and spends saturation 1:1; vanilla regen suspended while asleep; on a phase-4 night (`/time set` to a new-moon night) each conversion heals 2.0 HP for the same 1.0 saturation.
- Time-lapse: with a simulated sleeping player, `dayTime` advances > 1 per real tick; governor honors a deliberately tiny budget (effective rate < target); vanilla skip suppressed (time never jumps discontinuously); rate recomputes when the sleeper is removed; a hostile mob targeting a second, awake player clamps the effective rate to 1 and releases it 100 real ticks after the peril ends; during extra ticks an awake player's food/effect timers do not advance while a sleeper's do. Where the gametest player simulation can't genuinely sleep, drive the engine's rate input directly and assert the tick mechanics — the sleep-detection seam is then covered by a focused unit test.

### Manual

- Multiplayer fairness matrix (1–4 players, mixed sleepers, mid-night joins/leaves, Nether bystander, an awake player pulling mob aggro mid-lapse).
- Blink feel pass: cadence, occlusion depth, and combat suppression in real play; F1 and the client toggle.
- Phantom spawning observation: mountain night, sea-level night (none), new-moon sea level (spawns), `doInsomnia` off (none).
- Performance: time-lapse MSPT profile on a large world; governor behavior under load.
