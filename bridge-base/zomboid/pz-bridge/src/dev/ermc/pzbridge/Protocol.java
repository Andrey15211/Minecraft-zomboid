package dev.ermc.pzbridge;

/**
 * Java mirror of the offsets mc-bridge uses (link/Protocol.java in the Fabric mod).
 * Offsets are copied verbatim so the file mapping is layout-compatible without any
 * shared code artifact between the two builds.
 */
public final class Protocol {
    private Protocol() {}

    public static final String SHM_PATH = BridgePaths.file("bridge.shm");
    public static final int MAGIC = 0x434D484D;
    public static final int VERSION = 1;
    public static final int SHM_SIZE = 8 * 1024 * 1024;

    public static final int OFF_HEADER = 0x000000;
    public static final int OFF_STATE = 0x000100;
    public static final int OFF_CONTROL = 0x000800;

    // ErmcHeader
    public static final int H_MAGIC = 0x00;
    public static final int H_VERSION = 0x04;
    public static final int H_SIZE = 0x08;
    public static final int H_HOST_HEARTBEAT = 0x10;
    public static final int H_MC_HEARTBEAT = 0x18;
    public static final int H_HOST_PID = 0x20;
    public static final int H_MC_PID = 0x24;
    public static final int H_HOST_START = 0x28;
    public static final int H_MC_START = 0x30;

    // ErmcGameState.flags
    public static final int STATE_PLAYER_VALID = 1 << 1;
    public static final int STATE_WINDOW_VALID = 1 << 2;

    // ErmcGameState offsets (relative to OFF_STATE)
    public static final int S_SEQ = 0x00;
    public static final int S_FLAGS = 0x04;
    public static final int S_FRAME = 0x08;
    public static final int S_CAM_POS = 0x10;
    public static final int S_FOVY = 0x34;
    public static final int S_NEAR = 0x38;
    public static final int S_FAR = 0x3C;
    public static final int S_ASPECT = 0x40;
    public static final int S_UNITS_PER_METER = 0x44;
    public static final int S_PLAYER_POS = 0x48;
    public static final int S_PLAYER_QUAT = 0x54;
}
