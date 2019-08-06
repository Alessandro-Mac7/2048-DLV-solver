package gui;

import java.awt.BorderLayout;

import javax.swing.JFrame;

public class Launcher {

	public static void main(String[] args) {
           JFrame f = new JFrame();
           GamePanel game = new GamePanel();
           f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
           f.setTitle("2048");
           f.setResizable(true);
           
           f.setFocusable(true);
           f.setVisible(true);
           
           f.add(game, BorderLayout.CENTER);
   		   f.addKeyListener(game);
           f.pack();
           
           f.setLocationRelativeTo(null);

           game.start();
    }
}
