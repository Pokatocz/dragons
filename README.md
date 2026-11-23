# Dragons (Skywyrms)

A Minecraft mod: tame, saddle, ride and breed six elemental dragons – fire, frost, lightning, venom, void and water – and cross them into 30 hybrids.

**Download:** [CurseForge – Dragons](https://www.curseforge.com/minecraft/mc-mods/dragonss)

- **Loaders:** Fabric (with Fabric API), Forge, NeoForge
- **Minecraft:** 1.20 – 1.20.6, 1.21 – 1.21.11, 26.1 – 26.3 (68 jars, one per loader and version)
- **Mod id:** `skywyrms`

## Features

- Six dragons with their own model, animations, voice and ability (Fire Breath, Frost Breath, Lightning Strike, Venom Spit, Void Blink, Tidal Jet)
- Real flight: take off, steer where you look, bank, sprint, power-dive, land
- Breeding with golden apples, dragon eggs that incubate, hatchlings that grow up
- Genetics: two elements per dragon, 30 hybrids, inherited stats
- Wild dragons in fitting biomes – some of them hostile
- A large config (`config/skywyrms.json`): taming and food items, stat multipliers, ability switches, riding and saddles on/off, hostile dragons, despawning and more

## Building

The source is one tree for all targets; lines between `//#if MC >= 12102` … `//#endif` are picked per Minecraft version by `build/preprocess.py`.

```
python3 build/build.py --all                    # all release jars -> dist/jars/
python3 build/build.py --loader fabric --mc 1.21.1
python3 build/build.py --all --define AUTOTEST  # jars with the in-game self test -> dist/test-jars/
```

Needs Python 3, a JDK 25 (`SKYWYRMS_JDK25`) and Gradle 9.7+ (`SKYWYRMS_GRADLE`). Fabric targets are built with Loom; Forge and NeoForge targets reuse the Minecraft jar from Loom's cache, so the Fabric target of the same Minecraft version is built first. The test launcher in `run/` and the scripts in `test/` and `tools/` were written for the author's own machine.

## Layout

- `src/common` – the mod (entities, AI, abilities, rendering, config)
- `src/fabric`, `src/forge`, `src/neoforge` – loader entry points
- `src/resources` – models, textures, sounds, language, data
- `build/` – build scripts and per-target metadata
- `dist/changelog_*.md` – release notes

## License

Code: [MIT](LICENSE). The dragons' voices are Minecraft's own creature sounds, re-pitched and mixed; those remain Mojang's.
