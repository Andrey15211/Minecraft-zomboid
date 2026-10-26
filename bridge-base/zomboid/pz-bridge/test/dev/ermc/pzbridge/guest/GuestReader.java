package dev.ermc.pzbridge.guest;

import dev.ermc.bridge.link.BridgeShm;
import dev.ermc.bridge.link.GameState;

/**
 * Fake guest: uses the real mc-bridge BridgeShm.readState() to prove the PZ side
 * publishes a state the Fabric mod can consume. Prints a line per second with the
 * host heartbeat, player position and validity flags, and fails if a valid player
 * snapshot never appears while the host heartbeat keeps advancing.
 */
public final class GuestReader {
    public static void main(String[] args) throws Exception {
        // Argument is seconds (integer); default 60.
        long deadlineMs = System.currentTimeMillis() + (args.length > 0 ? Long.parseLong(args[0]) * 1000L : 60000L);
        GameState st = new GameState();
        BridgeShm shm = BridgeShm.open();
        System.out.println("guest: opened " + dev.ermc.bridge.link.Protocol.SHM_PATH);
        long lastHb = -1;
        int validSeen = 0;
        float lastX = 0f, lastY = 0f;
        boolean movedSeen = false;
        while (System.currentTimeMillis() < deadlineMs) {
            long hb = shm.hostHeartbeat();
            boolean ok = shm.readState(st);
            if (ok && st.has(dev.ermc.bridge.link.Protocol.STATE_PLAYER_VALID)) {
                validSeen++;
                if (lastX != 0f || lastY != 0f) {
                    if (Math.abs(st.playerPos[0] - lastX) > 0.01f || Math.abs(st.playerPos[1] - lastY) > 0.01f) {
                        movedSeen = true;
                    }
                }
                lastX = st.playerPos[0];
                lastY = st.playerPos[1];
            }
            if (hb != lastHb) {
                System.out.println("guest: hb=" + hb
                        + " valid=" + (ok && st.has(dev.ermc.bridge.link.Protocol.STATE_PLAYER_VALID))
                        + " player=(" + st.playerPos[0] + ", " + st.playerPos[1] + ", " + st.playerPos[2] + ")"
                        + (movedSeen ? " MOVED" : ""));
                lastHb = hb;
            }
            Thread.sleep(1000);
        }
        System.out.println("guest: summary validSnapshots=" + validSeen + " movementSeen=" + movedSeen);
        if (validSeen == 0) {
            System.out.println("guest: FAIL - no valid player state observed");
            System.exit(1);
        }
        System.out.println("guest: PASS (movement will be confirmed by the user walk-around)");
    }
}
