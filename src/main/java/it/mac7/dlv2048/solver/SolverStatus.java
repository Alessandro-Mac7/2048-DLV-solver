package it.mac7.dlv2048.solver;

/** Esito dell'interazione con il solver esterno, mostrabile in UI. */
public enum SolverStatus {
    OK("ok"),
    BINARIO_ASSENTE("DLV non trovato — esegui scripts/fetch-dlv.sh"),
    CHECKSUM_ERRATO("binario DLV con checksum inatteso"),
    TIMEOUT("DLV ha superato il tempo massimo"),
    NESSUNA_SOLUZIONE("nessuna mossa possibile"),
    ERRORE("errore durante l'esecuzione di DLV");

    private final String messaggio;

    SolverStatus(String messaggio) {
        this.messaggio = messaggio;
    }

    public String messaggio() {
        return messaggio;
    }
}
