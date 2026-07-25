package it.mac7.dlv2048.solver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Verifica memoizzata del checksum del binario DLV.
 *
 * <p>Il binario pesa circa 3 MB: rileggerlo e digerirlo a ogni suggerimento
 * consuma una fetta del budget del solver senza aggiungere garanzie, perche'
 * fra due richieste consecutive il file e' quasi sempre lo stesso. L'esito
 * viene quindi ricordato e invalidato quando cambia il percorso o il
 * timestamp di modifica, cioe' esattamente quando il file puo' essere un
 * altro (aggiornamento, ri-download, DLV2_HOME diverso).
 */
final class ChecksumCache {

    /** Calcolo dell'impronta, iniettabile per contarne le invocazioni nei test. */
    @FunctionalInterface
    interface Impronta {
        String sha256(Path file) throws IOException;
    }

    private record Voce(Path file, FileTime modificato, boolean valido) {}

    private final Impronta impronta;

    /** Letta e scritta anche dal worker del solver: volatile per pubblicarne le scritture. */
    private volatile Voce voce;

    ChecksumCache() {
        this(DlvBinary::sha256);
    }

    ChecksumCache(Impronta impronta) {
        this.impronta = impronta;
    }

    boolean valido(Path binary) {
        FileTime modificato;
        try {
            modificato = Files.getLastModifiedTime(binary);
        } catch (IOException e) {
            // il file e' sparito o non e' leggibile: nessuna voce da conservare
            voce = null;
            return false;
        }

        Voce corrente = voce;
        if (corrente != null && corrente.file().equals(binary) && corrente.modificato().equals(modificato)) {
            return corrente.valido();
        }

        boolean esito;
        try {
            esito = DlvBinary.EXPECTED_SHA256.equals(impronta.sha256(binary));
        } catch (IOException e) {
            voce = null;
            return false;
        }
        voce = new Voce(binary, modificato, esito);
        return esito;
    }
}
