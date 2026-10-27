# Zomboid bridge stages (host: Project Zomboid 42.21.0)

[Back to Minecraft Ring](../README.md) · [Build instructions](installation.md)

The repo's original host is Elden Ring (`bridge-base/elden-ring/`). The active
development host is Project Zomboid (`bridge-base/zomboid/pz-bridge/`), reusing
the same `bridge.shm` protocol and the Fabric mod (mc-bridge) as the guest.

## Stage status

| Stage | Scope | Status |
| --- | --- | --- |
| 1 | PZ host skeleton: `MainWrapper` classpath main class, heartbeat thread, `PzShm` protocol writer | done |
| 2 | State publishing: player pos/rotation into `ErmcGameState`, cross-JVM round-trip verified | done |
| 3 | Guest follows host: mc-bridge anchors a zone, teleports Steve on recall, then moves him onto the live PZ position every tick | done (verified live 2026-10-27) |
| 4 | Rendering: composite the Minecraft frame over the PZ frame (candidate base: Project Viewpoint 3D renderer) | not started |
| 5 | Gameplay: shared life, combat, building, input | not started |

## How to run stages 1–3

`Launch.ps1` at the repository root starts both games:

1. `bridge-base/zomboid/pz-bridge/Launch-PZ.bat` — PZ with the original
   ProjectZomboid64 JVM args, but main class `dev.ermc.pzbridge.MainWrapper`
   (adds `-debug`, so the debug menu and god mode stay available). It opens
   `runtime/bridge.shm` and publishes player state from a daemon thread.
2. After the host heartbeat advances in `bridge.shm`, the Fabric dev client
   starts (`gradlew runClient`, `ERMC_DIR` already set). `WorldBootstrap`
   auto-opens (or creates) the void world `er-bridge`.
3. Load a PZ save. `TerrainManager.updateAnchor` anchors zone 1 at the PZ
   player, teleports Steve, then `followHostPlayer` moves him onto the live
   PZ position each server tick (yaw from the state quaternion). A temporary
   terrain floor patch keeps him from falling until Stage 4 supplies ground.

Verify without the games running: `python tools/status.py` (heartbeat, player
validity), and `runtime/mc_cmd.txt` with the `status` command while the client
runs (Steve position/yaw/anchor in `run/logs/latest.log`, `[devcmd]` lines).

## Coordinate mapping (PZ → protocol → Minecraft)

PZ's world axes (X east, Y map-south, Z altitude) are remapped in
`PzShm.publishPlayerState` to the protocol's D3D convention (X right, Y up,
Z forward): `hostX = pzX, hostY = pzZ, hostZ = -pzY`. The yaw quaternion
writes the Y component with a +180° offset (PZ 0° = north → host 180°).
PZ tiles are metres, so `unitsPerMeter` is 1.0 and `CoordMap` maps 1:1 with
`HOST_FLIP_Z`. `stageId` is 1 while a PZ player exists: mc-bridge reads 0 as
a loading screen and would never anchor a zone or teleport.

## Known constraints

- **The Windows overlay must stay off for PZ.** `runs.client.vmArgs` in
  `mc-bridge/build.gradle` sets `-Derbridge.disableOverlay=true`: the overlay's
  `SetWindowLongPtr` window-ownership swap reproducibly crashed PZ's GLFW
  render thread (`glfw.dll` in `lockStepRenderStep`, two hs_err logs). The
  link itself is unaffected; the overlay returns when Stage 4 replaces the
  window trick with proper frame compositing.
- Terrain ray queries (`submitRays`) have no PZ implementation: the host never
  answers, the mailbox reports "stalled" once, and mc-bridge keeps its
  temporary floor patches. Real ground needs Stage 4.
- `missing tile vegetation_gro*` errors in PZ's console are vanilla PZ noise
  during map generation, unrelated to the bridge.
- Don't run two PZ instances against the same `runtime/`; the shm has one
  host slot.

## Stage 4 outline (host renderer = Project Viewpoint)

Viewpoint (Workshop 3809306528, requires ZombieBuddy) replaces PZ's renderer
with a 3D OpenGL pipeline and gives first/third-person cameras. Plan:

1. Verify Viewpoint alone is stable on the target GPU.
2. Run Viewpoint + bridge in link-only mode (current state) — the classpath
   wrapper and ZombieBuddy must coexist; both inject Java into one JVM.
3. Synchronize cameras: the perspective camera makes `CameraSync`
   FOLLOW_HOST/DRIVE_HOST meaningful for PZ (publish fovY, aspect, cam pos and
   the view quaternion in `PzShm`, currently zeroed).
4. Composite frames (the Elden Ring D3D12 path does not apply: PZ is OpenGL;
   a swap-chain hook must be written for Viewpoint's pipeline).
