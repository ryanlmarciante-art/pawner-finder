# Spawner Finder (Fabric, Minecraft 1.21.11, client-side)

Line-of-sight spawner scanner for Donut SMP: ESP brackets, live counter, nearest-distance readout,
movable HUD and a dark settings screen. Settings are saved to `config/spawnerfinder.json`.

## Keys (rebindable in Controls > Spawner Finder)
- J: scan surroundings now (result stays 5 s when auto-scan is off)
- K: open settings
- U: scanner ON/OFF

## Build
Needs JDK 21 and Gradle 9.2+.
    gradle build
Jar: `build/libs/spawner-finder-1.0.0.jar` (not the `-sources` one).
Or push to GitHub: the included workflow builds the jar and attaches it as an artifact.

## Install
1. Install Fabric Loader 0.18.1+ for 1.21.11 and put Fabric API in `mods/`.
2. Put `spawner-finder-1.0.0.jar` in `mods/`.
