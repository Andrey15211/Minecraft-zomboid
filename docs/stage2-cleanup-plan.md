# Stage 2 cleanup plan: removing the Elden Ring host side

This file records what is safe to delete now that Project Zomboid is the host game.
Do the deletion in one commit so the tree before/after is bisectable.

## Delete entirely

| Path | Why safe |
| --- | --- |
| `bridge-base/elden-ring/er-bridge/` | The whole C++ host (dinput8 proxy, hooks, D3D12 compositor). Replaced by the Java `pz-bridge` host. No Java code imports it; the only shared artifact is the protocol layout, mirrored as constants in `Protocol.java`. |
| `Install.ps1`, `Restore.ps1` | Copy `dinput8.dll`/`erbridge_core.dll` into the Elden Ring folder and back up Elden Ring saves. Meaningless for PZ: the PZ side installs with zero game-folder changes (`Launch-PZ.bat` adds one `-cp` entry). |
| `start.bat`, `Play.bat` | Thin wrappers for `Launch.ps1` in the Elden Ring flow. |
| `tools/build_native.py` | Builds the native DLLs (LLVM-MinGW toolchain) that no longer exist in this tree. |
| `tools/test_crash_recorder.py`, `tools/test_frame_switch.py`, `tools/test_moving_platform.py`, `tools/test_native_terrain_collision.py`, `tools/test_platform_boarding.py`, `tools/test_player_facing.py` | Regression harnesses that load the native DLLs; their subject is gone. |
| `tools/find_fps_limit.py`, `tools/check_upload_copy.py`, `tools/benchmark_*.py`, `tools/test_gpu_session.py`, `tools/test_overlay_loading.py` | Native-side measurement/upload checks; also reference `er-bridge` paths. |

## Rewrite (not delete)

| Path | Change |
| --- | --- |
| `Launch.ps1` | Replace the Elden Ring wait-logic (magic in `runtime\bridge.shm` from `dinput8.dll`) with: start `bridge-base\zomboid\pz-bridge\Launch-PZ.bat`, wait for `H_HOST_HEARTBEAT` to advance, then `gradle runClient` for mc-bridge with `ERMC_DIR` set. Keep the duplicate-PID guard. |
| `README.md`, `docs/` | Rewrite for Minecraft-Zomboid once the bridge does something visible. Defer until stage 3; the repo is private and unambiguous meanwhile. |

## Keep

- `bridge-base/elden-ring/mc-bridge/` — the guest; builds standalone (`gradle remapJar`, verified 2026-10-26 with foojay resolver in `settings.gradle`).
- `tools/status.py` — host-agnostic, reads `runtime/bridge.shm` directly (verified: no Elden Ring references; `player_valid`/`heartbeat` map to what PzShm publishes). Works against a running PZ today.
- `tools/screenshot.py` — captures the visible host window; only the window title match may need PZ wording.
- Terrain regression scripts (`test_stable_terrain.py` and the other `test_terrain_*.py`) — **partially native-coupled**: `test_stable_terrain.py` reads `er-bridge/src/game.cpp` as a source of expected constants, so after deleting `er-bridge/` those reads must be re-sourced or dropped. Check each script individually in the deletion commit; they generate harnesses under `build/`.

## mc-bridge naming

The `dev.ermc.bridge` package, mod id `erbridge` and jar name `er-bridge` are Elden Ring
heritage. Renaming touches ~90 files and both fabric.mod.json/mixins configs; defer until
the PZ host reaches feature parity, then do it as one mechanical commit (package +
resource rename plus `fabric.mod.json` id).
