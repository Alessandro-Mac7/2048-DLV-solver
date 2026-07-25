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

import javax.swing.JPanel;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import it.mac7.dlv2048.core.Game;
import it.mac7.dlv2048.core.GameState;
import it.mac7.dlv2048.solver.SolverOutcome;
import it.mac7.dlv2048.solver.SolverStatus;

@SuppressWarnings("serial")
public class GamePanel extends JPanel implements Runnable, KeyListener{

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

	private Color gridColor = new Color(0xBBADA0);
    private Color emptyColor = new Color(0xCDC1B4);
    private Color startColor = new Color(0xFFEBCD);

    private Thread thread;
	private boolean goal=false;

    private Game game;

    /** Ultimo esito richiesto a DLV con il tasto S; null finche' non e' mai stato premuto. */
    private SolverOutcome ultimoEsitoSolver;

    public GamePanel(){

    	this.setPreferredSize(new Dimension(900, 700));
    	this.setBackground(new Color(0xFAF8EF));
    	this.setFont(new Font("SansSerif", Font.BOLD, 48));
    	this.setFocusable(true);

    	game = new Game();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (game.stato() != GameState.RUNNING) {
                    game.inizia();
                    ultimoEsitoSolver = null;
                }
                repaint();
            }
        });
    }

	@Override
    public void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        Graphics2D g = (Graphics2D) gg;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        drawGrid(g);
    }

	public void drawGrid(Graphics2D g) {
        g.setColor(gridColor);
        g.fillRoundRect(200, 100, 499, 499, 15, 15);

        if (game.stato() == GameState.RUNNING) {

            for (int r = 0; r < Board.SIZE; r++) {
                for (int c = 0; c < Board.SIZE; c++) {
                    if (game.board().valueAt(r, c) == 0) {
                        g.setColor(emptyColor);
                        g.fillRoundRect(215 + c * 121, 115 + r * 121, 106, 106, 7, 7);
                    } else {
                        drawTile(g, r, c);
                    }
                }
            }
        } else {
            g.setColor(startColor);
            g.fillRoundRect(215, 115, 469, 469, 7, 7);

            g.setColor(gridColor.darker());
            g.setFont(new Font("SansSerif", Font.BOLD, 128));
            g.drawString("2048", 310, 270);

            g.setFont(new Font("SansSerif", Font.BOLD, 20));

            if (game.stato() == GameState.WON) {
                g.drawString("Vittoria!", 390, 350);

            } else if (game.stato() == GameState.OVER)
                g.drawString("Hai perso", 400, 350);

            g.setColor(gridColor);
            g.drawString("Clicca per iniziare la partita", 330, 470);
            g.drawString("(usa le frecce per muoverti e premi", 310, 520);
            g.drawString(" S per avere un suggerimento)", 310, 545);
        }

        if (ultimoEsitoSolver != null && ultimoEsitoSolver.status() != SolverStatus.OK) {
            g.setColor(Color.RED.darker());
            g.setFont(new Font("SansSerif", Font.BOLD, 16));
            g.drawString("DLV: " + ultimoEsitoSolver.status().messaggio(), 215, 625);
        }
    }

    void drawTile(Graphics2D g, int r, int c) {
        int value = game.board().valueAt(r, c);

        g.setColor(COLOR_TABLE[indiceColore(value)]);
        g.fillRoundRect(215 + c * 121, 115 + r * 121, 106, 106, 7, 7);
        String s = String.valueOf(value);

        g.setColor(value < 128 ? COLOR_TABLE[0] : COLOR_TABLE[1]);

        FontMetrics fm = g.getFontMetrics();
        int asc = fm.getAscent();
        int dec = fm.getDescent();

        int x = 215 + c * 121 + (106 - fm.stringWidth(s)) / 2;
        int y = 115 + r * 121 + (asc + (106 - (asc + dec)) / 2);

        g.drawString(s, x, y);
    }

	public void keyPressed(KeyEvent e) {
		switch (e.getKeyCode()) {
        case KeyEvent.VK_UP -> {
        	if(game.stato() == GameState.RUNNING)
        		game.muovi(Direction.UP);
        }
        case KeyEvent.VK_DOWN -> {
        	if(game.stato() == GameState.RUNNING)
        		game.muovi(Direction.DOWN);
        }
        case KeyEvent.VK_LEFT -> {
        	if(game.stato() == GameState.RUNNING)
        		game.muovi(Direction.LEFT);
        }
        case KeyEvent.VK_RIGHT -> {
        	if(game.stato() == GameState.RUNNING)
        		game.muovi(Direction.RIGHT);
        }
        case KeyEvent.VK_S -> {
            if (game.stato() == GameState.RUNNING) {
                SolverOutcome esito = game.suggerisci();
                esito.move().ifPresent(game::muovi);
                ultimoEsitoSolver = esito;
            }
        }
		default -> { }
		}

	}

	public void keyReleased(KeyEvent arg0) {}

	public void keyTyped(KeyEvent arg0) {}

	public void run() {
		int fps = 60;
		double timePerUpdate = 1000000000 / fps;
		double delta = 0;
		long now;
		long lastTime = System.nanoTime();
		long timer = 0;

		while(goal){

			now = System.nanoTime();
			delta += (now - lastTime) / timePerUpdate;
			timer += now - lastTime;
			lastTime = now;
			if(delta >= 1){
				repaint();
				delta--;
			}
			if(timer >= 1000000000){
				timer = 0;
			}
		}

		stop();

	}

	public synchronized void start(){
		if(goal)
			return;
		goal=true;
		thread = new Thread(this);
		thread.start();

	}

	public synchronized void stop(){
		if(!goal)
			return;
		goal=false;
		try{
			thread.join();
		}
		catch (InterruptedException e){
			e.printStackTrace();
		}
	}

}
