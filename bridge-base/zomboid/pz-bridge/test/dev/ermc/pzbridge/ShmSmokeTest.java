package dev.ermc.pzbridge;

import java.nio.file.Path;

/** Standalone check: open the shm twice (host + fake guest), write and read back. */
public final class ShmSmokeTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("erbridge.dir", args.length > 0 ? args[0] : "runtime");
        PzShm host = PzShm.open();
        PzShm guest = PzShm.open(); // same JVM: simulates a second mapper
        host.bumpHostHeartbeat();
        host.publishPlayerState(10754.5f, 9823.25f, 0f, 90f, true, true);
        host.bumpHostHeartbeat();
        String state = host.describe();
        System.out.println("pz-bridge smoke: " + state);
        if (!state.contains("hostHb=2")) throw new AssertionError("heartbeat not visible: " + state);
        java.nio.MappedByteBuffer b = guestBuffer();
        float px = b.getFloat(Protocol.OFF_STATE + Protocol.S_PLAYER_POS);
        float py = b.getFloat(Protocol.OFF_STATE + Protocol.S_PLAYER_POS + 4);
        int flags = b.getInt(Protocol.OFF_STATE + Protocol.S_FLAGS);
        System.out.println("pz-bridge smoke: guest sees player " + px + ", " + py + " flags=" + flags);
        if (px != 10754.5f || py != 9823.25f) throw new AssertionError("position mismatch");
        if ((flags & Protocol.STATE_PLAYER_VALID) == 0) throw new AssertionError("player-valid flag not set");
        host.close();
        System.out.println("pz-bridge smoke: PASS");
    }
    static java.nio.MappedByteBuffer guestBuffer() throws Exception {
        java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(
                Path.of(Protocol.SHM_PATH),
                java.nio.file.StandardOpenOption.READ, java.nio.file.StandardOpenOption.WRITE);
        java.nio.MappedByteBuffer b = ch.map(java.nio.channels.FileChannel.MapMode.READ_WRITE, 0, Protocol.SHM_SIZE);
        b.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        ch.close();
        return b;
    }
}
