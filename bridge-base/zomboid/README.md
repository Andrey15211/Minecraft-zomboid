# pz-bridge: the Project Zomboid host side

Java host bridge for Minecraft-Zomboid. Runs inside the PZ JVM by replacing the
main class: `Launch-PZ.bat` starts the game with `-cp <build>;...` and the main
class `dev.ermc.pzbridge.MainWrapper`, which opens the shared memory file and
then delegates to the original `zombie.gameStates.MainScreenState.main`.

- `src/.../MainWrapper.java` — main-class wrapper: heartbeat thread (50 ms) +
  player-state publication, fail-open (a broken bridge never blocks the game).
- `src/.../PzShm.java` — host-side writer for the bridge protocol (same offsets
  as mc-bridge's `link/Protocol.java`): `H_HOST_PID/HOST_HEARTBEAT` and an
  `ErmcGameState` seqlock snapshot of the player position.
- `test/.../ShmSmokeTest.java` — double-mapping round-trip check (no game).
- `test/.../guest/GuestReader.java` — fake guest built from the **real**
  mc-bridge `link` classes; proves a second JVM can read the PZ state.
- `Launch-PZ.bat` — launcher: original `ProjectZomboid64.bat` JVM args, our
  main class, `ERMC_DIR` pointed at the repo's `runtime/`.

## Build

```
javac --release 25 -cp <PZ>\projectzomboid.jar -d build src\dev\ermc\pzbridge\*.java
```

JDK 25+ is needed only for compilation; the game JVM is the bundled Zulu 25.

## Status (2026-10-27)

Stage 1 verified live: heartbeat and player position (tile coordinates) are
published while a save is loaded, the guest reader in a separate JVM sees them,
and `tools/status.py` works unmodified.
