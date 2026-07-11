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

- **The Chronometer** — a copper-and-redstone timepiece block. It emits redstone
  power 1–15 that climbs with the hour (each level lasts 80 seconds; comparators
  read the same value), its dial face sweeps the sun and moon across the day,
  and a right-click reads out the exact time — plus the moon phase and new-moon
  countdown at night. In dimensions without a day-night cycle it honestly emits
  nothing. `enableChronometer = false` removes the recipe; placed blocks keep
  working.

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
