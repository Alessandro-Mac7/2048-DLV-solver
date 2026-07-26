package it.mac7.dlv2048.gui;

import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import it.mac7.dlv2048.core.Game;
import it.mac7.dlv2048.solver.SolverOutcome;
import it.mac7.dlv2048.solver.SolverStatus;

/**
 * Punteggio, tessera massima e stato del solver. Si aggiorna sui cambi di
 * stato notificati da {@link GamePanel} (mossa, inizio/fine ricerca, nuova
 * partita), non a ogni frame: non serve un timer proprio.
 */
public class StatusPanel extends JPanel {

    private final GamePanel gamePanel;
    private final JLabel punteggioLabel = new JLabel();
    private final JLabel tesseraMassimaLabel = new JLabel();
    private final JLabel solverLabel = new JLabel();

    public StatusPanel(GamePanel gamePanel) {
        this.gamePanel = gamePanel;

        setLayout(new FlowLayout(FlowLayout.LEFT, 24, 10));
        setBorder(new EmptyBorder(4, 12, 4, 12));

        Font f = getFont().deriveFont(Font.BOLD, 15f);
        for (JLabel l : new JLabel[]{punteggioLabel, tesseraMassimaLabel, solverLabel}) {
            l.setFont(f);
            add(l);
        }

        gamePanel.aggiungiAscoltatore(this::aggiorna);
        aggiorna();
    }

    private void aggiorna() {
        Game game = gamePanel.game();
        int esponenteMax = game.board().maxExponent();
        int tesseraMassima = esponenteMax == 0 ? 0 : (1 << esponenteMax);

        punteggioLabel.setText("Punteggio: " + game.punteggio());
        tesseraMassimaLabel.setText("Tessera massima: " + tesseraMassima);
        solverLabel.setText(testoStatoSolver());
    }

    private String testoStatoSolver() {
        if (gamePanel.solverInCorso()) return "DLV sta pensando...";

        SolverOutcome esito = gamePanel.ultimoEsitoSolver();
        if (esito == null) return "DLV: premi S per un suggerimento";
        if (esito.status() == SolverStatus.OK) {
            return "DLV: orizzonte " + esito.horizonRaggiunto() + " raggiunto";
        }
        return "DLV: " + esito.status().messaggio();
    }
}
