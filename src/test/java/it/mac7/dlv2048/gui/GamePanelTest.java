package it.mac7.dlv2048.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GamePanelTest {

    /**
     * Regressione: l'indice del colore era log2(valore)+1 su una tabella di 12
     * elementi, quindi una tessera 2048 (log2 = 11) chiedeva l'indice 12 e
     * faceva saltare drawTile con ArrayIndexOutOfBounds. Oggi il caso e'
     * irraggiungibile solo per un accidente dell'ordine con cui Game aggiorna
     * lo stato dopo la vittoria: non e' una garanzia su cui appoggiarsi.
     */
    @Test
    void l_indice_di_colore_resta_dentro_la_tabella_per_ogni_tessera() {
        for (int esponente = 1; esponente <= 17; esponente++) {
            int valore = 1 << esponente;
            int i = GamePanel.indiceColore(valore);
            assertTrue(i >= 0 && i < GamePanel.COLOR_TABLE.length,
                    "tessera " + valore + ": indice " + i + " fuori da una tabella di "
                            + GamePanel.COLOR_TABLE.length + " colori");
        }
    }

    @Test
    void la_tessera_2048_usa_l_ultimo_colore_della_tabella() {
        assertEquals(GamePanel.COLOR_TABLE.length - 1, GamePanel.indiceColore(2048));
    }

    @Test
    void sotto_2048_l_indice_resta_quello_di_sempre() {
        assertEquals(2, GamePanel.indiceColore(2));
        assertEquals(3, GamePanel.indiceColore(4));
        assertEquals(8, GamePanel.indiceColore(128));
        assertEquals(11, GamePanel.indiceColore(1024));
    }
}
