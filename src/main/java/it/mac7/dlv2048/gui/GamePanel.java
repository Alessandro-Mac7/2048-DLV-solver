package it.mac7.dlv2048.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import it.mac7.dlv2048.core.Game;
import it.mac7.dlv2048.core.GameState;
import it.mac7.dlv2048.core.MoveResult;
import it.mac7.dlv2048.core.TileMove;
import it.mac7.dlv2048.solver.SolverOutcome;
import it.mac7.dlv2048.solver.SolverStatus;

@SuppressWarnings("serial")
public class GamePanel extends JPanel implements KeyListener{

	static final Color[] COLOR_TABLE = {
	        new Color(0x701710), new Color(0xFFE4C3), new Color(0xfff4d3),
	        new Color(0xffdac3), new Color(0xe7b08e), new Color(0xe7bf8e),
	        new Color(0xffc4c3), new Color(0xE7948e), new Color(0xbe7e56),
	        new Color(0xbe5e56), new Color(0x9c3931), new Color(0x701710)};

	/**
	 * Colore di una tessera dal suo valore. L'indice log2(valore)+1 esce dalla
	 * tabella gia' a 2048 (chiede il 12 su 12 colori): va limitato, altrimenti
	 * disegnare la tessera della vittoria fa saltare la paintComponent.
	 */
	static int indiceColore(int value) {
		int i = (int) (Math.log(value) / Math.log(2)) + 1;
		return Math.max(0, Math.min(i, COLOR_TABLE.length - 1));
	}

	/** Geometria della griglia per le dimensioni correnti del pannello. */
	record Layout(int gridX, int gridY, int gridSize, int cellSize, int gap) {
		int cellX(int col) { return gridX + gap + col * (cellSize + gap); }
		int cellY(int row) { return gridY + gap + row * (cellSize + gap); }
	}

	private static final int PADDING = 40;
	private static final int MIN_GRID_SIZE = 160;

	/** Durata dello scivolamento/fusione: abbastanza breve da non far sembrare il gioco lento. */
	private static final long DURATA_ANIMAZIONE_MS = 130;

	private Color gridColor = new Color(0xBBADA0);
    private Color emptyColor = new Color(0xCDC1B4);
    private Color startColor = new Color(0xFFEBCD);

    private final Game game;

    /** Ultimo esito richiesto a DLV con il tasto S; null finche' non e' mai stato premuto. */
    private volatile SolverOutcome ultimoEsitoSolver;

    /**
     * Vero mentre una ricerca e' in volo. Volatile perche' lo legge anche chi
     * osserva il pannello da fuori dall'EDT (i test).
     */
    private volatile boolean solverInCorso;

    /**
     * Guida il ridisegno durante lo scivolamento/fusione delle tessere: parte
     * solo quando una mossa sposta qualcosa e si ferma da se' a fine
     * animazione. Prima qui girava un thread perenne in busy-loop a 60 fps
     * anche a partita ferma; ora, a riposo, non gira nulla.
     */
    private final Timer animazioneTimer;
    private List<TileMove> movimentiInCorso = List.of();
    private long inizioAnimazione;

    /** Osservatori dei cambi di stato (punteggio, tessera massima, solver): es. il pannello di stato. */
    private final List<Runnable> ascoltatori = new ArrayList<>();

    public GamePanel(){
    	this(new Game());
    }

    public GamePanel(Game game){

    	this.setPreferredSize(new Dimension(900, 700));
    	this.setBackground(new Color(0xFAF8EF));
    	this.setFont(new Font("SansSerif", Font.BOLD, 48));
    	this.setFocusable(true);

    	this.game = game;

    	this.animazioneTimer = new Timer(1000 / 60, e -> avanzaAnimazione());

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (game.stato() != GameState.RUNNING) {
                    game.inizia();
                    ultimoEsitoSolver = null;
                }
                notificaCambioStato();
            }
        });
    }

    /** Registra un osservatore invocato a ogni cambio di stato reale (non a ogni frame). */
    public void aggiungiAscoltatore(Runnable r) {
    	ascoltatori.add(r);
    }

    private void notificaCambioStato() {
    	for (Runnable r : ascoltatori) r.run();
    	repaint();
    }

    private void avanzaAnimazione() {
    	long trascorso = System.currentTimeMillis() - inizioAnimazione;
    	if (trascorso >= DURATA_ANIMAZIONE_MS) {
    		animazioneTimer.stop();
    		movimentiInCorso = List.of();
    	}
    	repaint();
    }

    /** Pacchetto-visibile per i test: vero mentre l'animazione di una mossa sta girando. */
    boolean animazioneInCorso() {
    	return animazioneTimer.isRunning();
    }

    private void avviaAnimazione(MoveResult r) {
    	movimentiInCorso = r.movimenti();
    	inizioAnimazione = System.currentTimeMillis();
    	animazioneTimer.start();
    }

	@Override
    public void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        Graphics2D g = (Graphics2D) gg;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        drawGrid(g);
    }

    /** Geometria calcolata dalle dimensioni correnti del pannello: la griglia segue il ridimensionamento. */
    Layout geometria() {
    	int w = Math.max(getWidth(), MIN_GRID_SIZE + PADDING * 2);
    	int h = Math.max(getHeight(), MIN_GRID_SIZE + PADDING * 2);
    	int side = Math.max(MIN_GRID_SIZE, Math.min(w, h) - PADDING * 2);
    	int gridX = (w - side) / 2;
    	int gridY = (h - side) / 2;
    	int gap = Math.max(4, side / 70);
    	int cellSize = (side - gap * (Board.SIZE + 1)) / Board.SIZE;
    	return new Layout(gridX, gridY, side, cellSize, gap);
    }

	public void drawGrid(Graphics2D g) {
		Layout lay = geometria();

        g.setColor(gridColor);
        g.fillRoundRect(lay.gridX(), lay.gridY(), lay.gridSize(), lay.gridSize(), 15, 15);

        if (game.stato() == GameState.RUNNING) {
        	drawTiles(g, lay);
        } else {
        	drawSchermataIniziale(g, lay);
        }
    }

	private void drawTiles(Graphics2D g, Layout lay) {
		for (int r = 0; r < Board.SIZE; r++) {
			for (int c = 0; c < Board.SIZE; c++) {
				g.setColor(emptyColor);
				g.fillRoundRect(lay.cellX(c), lay.cellY(r), lay.cellSize(), lay.cellSize(), 7, 7);
			}
		}

		if (animazioneTimer.isRunning()) {
			double t = easeOut(Math.min(1.0, (System.currentTimeMillis() - inizioAnimazione) / (double) DURATA_ANIMAZIONE_MS));
			for (TileMove m : movimentiInCorso) {
				int x0 = lay.cellX(m.fromCol());
				int y0 = lay.cellY(m.fromRow());
				int x1 = lay.cellX(m.toCol());
				int y1 = lay.cellY(m.toRow());
				int x = (int) Math.round(x0 + (x1 - x0) * t);
				int y = (int) Math.round(y0 + (y1 - y0) * t);
				drawTileAt(g, lay, x, y, 1 << m.esponenteIniziale());
			}
		} else {
			for (int r = 0; r < Board.SIZE; r++) {
				for (int c = 0; c < Board.SIZE; c++) {
					int v = game.board().valueAt(r, c);
					if (v != 0) drawTile(g, lay, r, c, v);
				}
			}
		}
	}

	private static double easeOut(double t) {
		double u = 1 - t;
		return 1 - u * u * u;
	}

	private void drawSchermataIniziale(Graphics2D g, Layout lay) {
        g.setColor(startColor);
        int inset = lay.gap() * 2;
        g.fillRoundRect(lay.gridX() + inset, lay.gridY() + inset,
                lay.gridSize() - inset * 2, lay.gridSize() - inset * 2, 7, 7);

        int cx = lay.gridX() + lay.gridSize() / 2;
        int titoloSize = Math.max(24, lay.gridSize() / 4);
        int testoSize = Math.max(12, lay.gridSize() / 25);

        g.setColor(gridColor.darker());
        Font titolo = new Font("SansSerif", Font.BOLD, titoloSize);
        g.setFont(titolo);
        drawCentrato(g, "2048", cx, lay.gridY() + lay.gridSize() / 3);

        Font testo = new Font("SansSerif", Font.BOLD, testoSize);
        g.setFont(testo);

        if (game.stato() == GameState.WON) {
            drawCentrato(g, "Vittoria!", cx, lay.gridY() + lay.gridSize() / 2);
        } else if (game.stato() == GameState.OVER) {
            drawCentrato(g, "Hai perso", cx, lay.gridY() + lay.gridSize() / 2);
        }

        g.setColor(gridColor);
        int riga = lay.gridY() + lay.gridSize() * 2 / 3;
        int passo = testoSize + 6;
        drawCentrato(g, "Clicca per iniziare la partita", cx, riga);
        drawCentrato(g, "(usa le frecce per muoverti e premi", cx, riga + passo);
        drawCentrato(g, " S per avere un suggerimento)", cx, riga + passo * 2);
	}

	private static void drawCentrato(Graphics2D g, String s, int cx, int baseline) {
		FontMetrics fm = g.getFontMetrics();
		g.drawString(s, cx - fm.stringWidth(s) / 2, baseline);
	}

    void drawTile(Graphics2D g, Layout lay, int r, int c, int value) {
    	drawTileAt(g, lay, lay.cellX(c), lay.cellY(r), value);
    }

    private void drawTileAt(Graphics2D g, Layout lay, int x, int y, int value) {
        g.setColor(COLOR_TABLE[indiceColore(value)]);
        g.fillRoundRect(x, y, lay.cellSize(), lay.cellSize(), 7, 7);
        String s = String.valueOf(value);

        g.setColor(value < 128 ? COLOR_TABLE[0] : COLOR_TABLE[1]);

        int fontSize = Math.max(10, lay.cellSize() * 2 / 5);
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        int asc = fm.getAscent();
        int dec = fm.getDescent();

        int tx = x + (lay.cellSize() - fm.stringWidth(s)) / 2;
        int ty = y + (asc + (lay.cellSize() - (asc + dec)) / 2);

        g.drawString(s, tx, ty);
    }

	public void keyPressed(KeyEvent e) {
		switch (e.getKeyCode()) {
        case KeyEvent.VK_UP -> tentaMossa(Direction.UP);
        case KeyEvent.VK_DOWN -> tentaMossa(Direction.DOWN);
        case KeyEvent.VK_LEFT -> tentaMossa(Direction.LEFT);
        case KeyEvent.VK_RIGHT -> tentaMossa(Direction.RIGHT);
        case KeyEvent.VK_S -> avviaSuggerimento();
		default -> { }
		}
	}

	private void tentaMossa(Direction d) {
		if (game.stato() != GameState.RUNNING) return;
		MoveResult r = game.muoviDettagliato(d);
		if (r.moved()) {
			avviaAnimazione(r);
			notificaCambioStato();
		}
	}

	/**
	 * La ricerca di DLV dura secondi: eseguirla qui, sull'Event Dispatch Thread,
	 * congelava la finestra per tutto il tempo — niente ridisegno, niente
	 * risposta ai tasti, l'utente vede l'applicazione morta. Va su un
	 * SwingWorker, e il risultato torna sull'EDT in done(), che e' l'unico
	 * posto da cui si puo' toccare lo stato del gioco e ridipingere.
	 *
	 * <p>La guardia su solverInCorso impedisce che tenere premuto S accodi
	 * ricerche concorrenti, cioe' processi DLV in parallelo che si contendono
	 * la CPU e applicano mosse calcolate su board ormai vecchie.
	 */
	private void avviaSuggerimento() {
		if (game.stato() != GameState.RUNNING || solverInCorso) return;

		solverInCorso = true;
		ultimoEsitoSolver = null;
		notificaCambioStato();

		Board boardRichiesta = game.board();

		new SwingWorker<SolverOutcome, Void>() {
			@Override
			protected SolverOutcome doInBackground() {
				return game.suggerisci();
			}

			@Override
			protected void done() {
				try {
					SolverOutcome esito = get();
					// Fra la richiesta e questo momento l'utente puo' aver mosso con le
					// frecce o aver avviato un'altra partita col mouse: un suggerimento
					// calcolato su una board che non e' piu' quella attuale va scartato
					// per intero, non solo la mossa.
					if (game.stato() == GameState.RUNNING && game.board().equals(boardRichiesta)) {
						esito.move().ifPresent(d -> {
							MoveResult r = game.muoviDettagliato(d);
							if (r.moved()) avviaAnimazione(r);
						});
						ultimoEsitoSolver = esito;
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					ultimoEsitoSolver = SolverOutcome.fallito(SolverStatus.ERRORE, 0);
				} finally {
					solverInCorso = false;
					notificaCambioStato();
				}
			}
		}.execute();
	}

	/** Pacchetto-visibile per i test: vero mentre una ricerca e' in corso. */
	boolean solverInCorso() {
		return solverInCorso;
	}

	/** Pacchetto-visibile per i test: ultimo esito ricevuto da DLV, null se nessuno. */
	SolverOutcome ultimoEsitoSolver() {
		return ultimoEsitoSolver;
	}

	/** Pacchetto-visibile: la partita mostrata, per il pannello di stato. */
	Game game() {
		return game;
	}

	public void keyReleased(KeyEvent arg0) {}

	public void keyTyped(KeyEvent arg0) {}

}
