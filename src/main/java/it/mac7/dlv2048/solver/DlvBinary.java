package it.mac7.dlv2048.solver;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Trova e verifica il binario DLV2. */
public final class DlvBinary {

    public static final String EXPECTED_SHA256 =
            "b169b75dd7ee780b14ebf03158804ec010a71f27e532a3c9204b7ab01c3c92d7";

    private DlvBinary() {}

    /** Cerca $DLV2_HOME/dlv2, poi ./dlv2, poi dlv2 nel PATH. Restituisce sempre percorsi assoluti. */
    public static Optional<Path> locate() {
        String home = System.getenv("DLV2_HOME");
        if (home != null && !home.isBlank()) {
            Path p = Path.of(home, "dlv2").toAbsolutePath();
            if (Files.isExecutable(p)) return Optional.of(p);
        }
        Path local = Path.of("dlv2").toAbsolutePath();
        if (Files.isExecutable(local)) return Optional.of(local);

        for (Path candidato : candidatiInPath(System.getenv("PATH"))) {
            if (Files.isExecutable(candidato)) return Optional.of(candidato);
        }
        return Optional.empty();
    }

    /**
     * Espande la variabile PATH in candidati "dir/dlv2" assoluti, saltando i
     * segmenti vuoti (es. "/usr/bin::/bin", frequente con PATH malformate):
     * un segmento vuoto non deve produrre il Path relativo "dlv2", che
     * duplicherebbe il controllo ./dlv2 gia' fatto altrove e romperebbe la
     * garanzia che locate() restituisca sempre un percorso assoluto.
     * Pacchetto-visibile per i test.
     */
    static List<Path> candidatiInPath(String path) {
        List<Path> candidati = new ArrayList<>();
        if (path == null) return candidati;
        for (String dir : path.split(":")) {
            if (dir.isEmpty()) continue;
            candidati.add(Path.of(dir, "dlv2").toAbsolutePath());
        }
        return candidati;
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
