package dev.ermc.pzbridge;

import java.nio.file.Path;

/** Same resolution order as mc-bridge's BridgePaths so both sides meet in one directory. */
public final class BridgePaths {
    private BridgePaths() {}

    public static String file(String name) {
        String dir = System.getProperty("erbridge.dir");
        if (dir == null || dir.isBlank()) dir = System.getenv("ERMC_DIR");
        if (dir == null || dir.isBlank()) dir = Path.of(System.getProperty("java.io.tmpdir"), "ermc").toString();
        return Path.of(dir, name).toString();
    }
}
