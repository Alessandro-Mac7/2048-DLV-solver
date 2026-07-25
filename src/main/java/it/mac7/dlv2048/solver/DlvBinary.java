package it.mac7.dlv2048.solver;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Optional;

/** Trova e verifica il binario DLV2. */
public final class DlvBinary {

    public static final String EXPECTED_SHA256 =
            "b169b75dd7ee780b14ebf03158804ec010a71f27e532a3c9204b7ab01c3c92d7";

    private DlvBinary() {}

    /** Cerca $DLV2_HOME/dlv2, poi ./dlv2, poi dlv2 nel PATH. */
    public static Optional<Path> locate() {
        String home = System.getenv("DLV2_HOME");
        if (home != null && !home.isBlank()) {
            Path p = Path.of(home, "dlv2");
            if (Files.isExecutable(p)) return Optional.of(p);
        }
        Path local = Path.of("dlv2").toAbsolutePath();
        if (Files.isExecutable(local)) return Optional.of(local);

        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(":")) {
                Path p = Path.of(dir, "dlv2");
                if (Files.isExecutable(p)) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Files.readAllBytes(file));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }

    public static boolean checksumValido(Path binary) {
        try {
            return EXPECTED_SHA256.equals(sha256(binary));
        } catch (IOException e) {
            return false;
        }
    }
}
