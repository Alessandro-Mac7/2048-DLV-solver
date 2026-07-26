package it.mac7.dlv2048.core;

/**
 * Spostamento di una singola tessera durante una mossa, per l'animazione.
 * Con {@code fuso}, due movimenti condividono la stessa destinazione: sono
 * la coppia che si fonde in una sola tessera di esponente {@code esponenteFinale}.
 */
public record TileMove(int fromRow, int fromCol, int toRow, int toCol,
                        int esponenteIniziale, int esponenteFinale, boolean fuso) {}
