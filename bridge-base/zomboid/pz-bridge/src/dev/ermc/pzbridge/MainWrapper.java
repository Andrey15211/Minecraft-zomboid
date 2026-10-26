package dev.ermc.pzbridge;

import zombie.characters.IsoPlayer;
import zombie.core.Core;

/**
 * Replacement main class: ProjectZomboid's ProjectZomboid64.json points at
 * zombie.gameStates.MainScreenState; the bridge launcher points here instead.
 * Starts the PZ bridge link (shared memory with the Minecraft side), then
 * delegates to the original main so the game boots exactly as before.
 *
 * A background thread bumps H_HOST_HEARTBEAT and, once a world is loaded,
 * publishes the player position into ErmcGameState. All game access happens
 * through the public API only (IsoPlayer.getInstance/getX/..., Core.getInstance)
 * with guards, so a failed read logs instead of killing the game.
 */
public final class MainWrapper {
    private static volatile boolean stopped = false;
    private static PzShm shm;

    public static void main(String[] args) {
        try {
            shm = PzShm.open();
            Runtime.getRuntime().addShutdownHook(new Thread(MainWrapper::shutdown, "pz-bridge-shutdown"));
            Thread hb = new Thread(MainWrapper::heartbeatLoop, "pz-bridge-heartbeat");
            hb.setDaemon(true);
            hb.start();
            System.out.println("pz-bridge: shm opened at " + Protocol.SHM_PATH
                    + " pid=" + ProcessHandle.current().pid());
        } catch (Throwable t) {
            // A dead link must never stop the game from starting.
            System.out.println("pz-bridge: FAILED to open shared memory, bridge disabled: " + t);
            t.printStackTrace(System.out);
            shm = null;
        }
        zombie.gameStates.MainScreenState.main(args);
    }

    private static void heartbeatLoop() {
        long lastPlayerLog = 0L;
        while (!stopped) {
            try {
                if (shm != null) {
                    shm.bumpHostHeartbeat();
                    publishPlayerIfLoaded();
                    long now = System.currentTimeMillis();
                    if (now - lastPlayerLog >= 5000) {
                        lastPlayerLog = now;
                        System.out.println("pz-bridge: " + shm.describe());
                    }
                }
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                // One broken tick (e.g. mid-world-load) is not a reason to die.
                System.out.println("pz-bridge: tick error (continuing): " + t);
                sleepQuietly(500L);
            }
        }
    }

    private static void publishPlayerIfLoaded() {
        if (shm == null) {
            return;
        }
        try {
            IsoPlayer p = IsoPlayer.getInstance();
            if (p != null) {
                shm.publishPlayerState(p.getX(), p.getY(), p.getZ(), p.getDirectionAngle(), true, true);
            } else {
                // Still in menus: mark the snapshot invalid so the MC side waits.
                shm.publishPlayerState(0f, 0f, 0f, 0f, false, false);
            }
        } catch (NoClassDefFoundError | NoSuchFieldError | NoSuchMethodError e) {
            // The world classes exist but are not initialised yet (main menu); keep the heartbeat only.
            shm.publishPlayerState(0f, 0f, 0f, 0f, false, false);
        }
    }

    private static void shutdown() {
        stopped = true;
        if (shm != null) {
            try {
                shm.close();
                System.out.println("pz-bridge: shm closed cleanly");
            } catch (Throwable t) {
                System.out.println("pz-bridge: close error: " + t);
            }
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
