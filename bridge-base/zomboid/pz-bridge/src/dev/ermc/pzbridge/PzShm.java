package dev.ermc.pzbridge;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * PZ-side counterpart of mc-bridge's BridgeShm: same file, same offsets (Protocol.java),
 * but this JVM is the HOST game (Project Zomboid), so it writes host fields:
 * H_HOST_PID, H_HOST_START, H_HOST_HEARTBEAT and publishes player state in OFF_STATE.
 */
public final class PzShm {
    private static final VarHandle INT = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private final MappedByteBuffer buf;
    private int stateSeq = 0;

    private PzShm(MappedByteBuffer buf) {
        this.buf = buf;
    }

    public static PzShm open() throws IOException {
        Path file = Path.of(Protocol.SHM_PATH);
        Files.createDirectories(file.getParent());
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            if (ch.size() < Protocol.SHM_SIZE) {
                ch.write(ByteBuffer.wrap(new byte[1]), Protocol.SHM_SIZE - 1);
            }
            MappedByteBuffer b = ch.map(FileChannel.MapMode.READ_WRITE, 0, Protocol.SHM_SIZE);
            b.order(ByteOrder.LITTLE_ENDIAN);
            PzShm shm = new PzShm(b);
            shm.initHeader();
            return shm;
        }
    }

    private void initHeader() {
        if ((int) INT.getAcquire(buf, Protocol.H_MAGIC) != Protocol.MAGIC
                || (int) INT.getAcquire(buf, Protocol.H_VERSION) != Protocol.VERSION) {
            // The MC side may already hold the same file: only zero the header block
            // (other regions have their own seqlocks and are safe to leave).
            for (int i = 0; i < Protocol.OFF_STATE; i += 8) {
                buf.putLong(i, 0L);
            }
            buf.putInt(Protocol.H_VERSION, Protocol.VERSION);
            buf.putInt(Protocol.H_SIZE, Protocol.SHM_SIZE);
            INT.setRelease(buf, Protocol.H_MAGIC, Protocol.MAGIC);
        }
        buf.putInt(Protocol.H_HOST_PID, (int) ProcessHandle.current().pid());
        LONG.setRelease(buf, Protocol.H_HOST_START, System.currentTimeMillis());
    }

    public void bumpHostHeartbeat() {
        long v = (long) LONG.getOpaque(buf, Protocol.H_HOST_HEARTBEAT);
        LONG.setRelease(buf, Protocol.H_HOST_HEARTBEAT, v + 1);
    }

    public long mcHeartbeat() {
        return (long) LONG.getAcquire(buf, Protocol.H_MC_HEARTBEAT);
    }

    /**
     * Publishes the player snapshot as ErmcGameState, in the host-game coordinate
     * convention the protocol expects (Elden Ring / D3D: X right, Y up, Z forward).
     * PZ's own axes are X = east, Y = map-south, Z = altitude, so they are remapped:
     * hostX = pzX, hostY = pzZ (height), hostZ = -pzY (flip keeps the map a mirror
     * of MC, matching CoordMap's HOST_FLIP_Z for a 2D top-down world).
     * PZ world units are tiles (1 tile = 1 metre), same as MC blocks: 1:1.
     */
    public void publishPlayerState(float x, float y, float z, float dirAngle, boolean playerValid, boolean windowValid) {
        int base = Protocol.OFF_STATE;
        int seq = stateSeq;
        INT.setOpaque(buf, base + Protocol.S_SEQ, seq + 1); // odd = write in progress
        VarHandle.releaseFence();
        int flags = 0;
        if (playerValid) flags |= Protocol.STATE_PLAYER_VALID;
        if (windowValid) flags |= Protocol.STATE_WINDOW_VALID;
        buf.putInt(base + Protocol.S_FLAGS, flags);
        buf.putLong(base + Protocol.S_FRAME, seq + 1);
        float hx = x, hy = z, hz = -y;
        // PZ camera == player position for now (isometric camera comes later)
        buf.putFloat(base + Protocol.S_CAM_POS, hx);
        buf.putFloat(base + Protocol.S_CAM_POS + 4, hy);
        buf.putFloat(base + Protocol.S_CAM_POS + 8, hz);
        buf.putFloat(base + Protocol.S_FOVY, 0f);
        buf.putFloat(base + Protocol.S_NEAR, 0.1f);
        buf.putFloat(base + Protocol.S_FAR, 100f);
        buf.putFloat(base + Protocol.S_ASPECT, 16f / 9f);
        buf.putFloat(base + Protocol.S_UNITS_PER_METER, 1.0f);
        buf.putFloat(base + Protocol.S_PLAYER_POS, hx);
        buf.putFloat(base + Protocol.S_PLAYER_POS + 4, hy);
        buf.putFloat(base + Protocol.S_PLAYER_POS + 8, hz);
        // host yaw: 0 = -Z (protocol forward), PZ angle 0 = north (+Z in host frame
        // after the remap, since pzY map-south -> hostZ = -pzY). North = +Z, so the
        // host yaw is 180 degrees when facing north; west (-pzX) is 90.
        float hostYawDeg = dirAngle + 180f;
        float yawRad = (float) Math.toRadians(hostYawDeg);
        // Y-rotation quaternion (host up axis); mc-bridge reads quat[1] (Y) for its yaw.
        buf.putFloat(base + Protocol.S_PLAYER_QUAT, 0f);
        buf.putFloat(base + Protocol.S_PLAYER_QUAT + 4, (float) Math.sin(yawRad / 2f));
        buf.putFloat(base + Protocol.S_PLAYER_QUAT + 8, 0f);
        buf.putFloat(base + Protocol.S_PLAYER_QUAT + 12, (float) Math.cos(yawRad / 2f));
        // stageId 1 = "in world": mc-bridge treats 0 as a loading screen and never
        // anchors a zone, so the teleport to the host player would never fire.
        buf.putInt(base + Protocol.S_STAGE, playerValid ? 1 : 0);
        INT.setRelease(buf, base + Protocol.S_SEQ, seq + 2);
        stateSeq = seq + 2;
    }

    /** Logs the current link state for diagnostics; returns the line written. */
    public String describe() {
        long hostHb = (long) LONG.getAcquire(buf, Protocol.H_HOST_HEARTBEAT);
        long mcHb = (long) LONG.getAcquire(buf, Protocol.H_MC_HEARTBEAT);
        int mcPid = (int) INT.getAcquire(buf, Protocol.H_MC_PID);
        return "hostHb=" + hostHb + " mcHb=" + mcHb + " mcPid=" + mcPid;
    }

    public void close() {
        // Unmapping is left to the OS at JVM exit; only clear the host PID so a
        // stale value does not confuse a later launcher check.
        try {
            buf.putInt(Protocol.H_HOST_PID, 0);
            LONG.setRelease(buf, Protocol.H_HOST_HEARTBEAT, 0L);
        } catch (Throwable ignored) {
        }
    }
}
