package it.mac7.dlv2048.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;

public class Launcher {

	public static void main(String[] args) {
		FlatLightLaf.setup();
		SwingUtilities.invokeLater(Launcher::creaFinestra);
	}

	private static void creaFinestra() {
		GamePanel game = new GamePanel();
		StatusPanel stato = new StatusPanel(game);

		JFrame f = new JFrame();
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setTitle("2048");
		f.setLayout(new BorderLayout());
		f.add(stato, BorderLayout.NORTH);
		f.add(game, BorderLayout.CENTER);
		f.addKeyListener(game);

		f.setResizable(true);
		f.setMinimumSize(new Dimension(420, 480));
		f.pack();
		f.setLocationRelativeTo(null);
		f.setVisible(true);
		game.requestFocusInWindow();
	}
}
