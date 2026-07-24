package it.mac7.dlv2048.core;

/** Le quattro direzioni di gioco. */
public enum Direction {
    UP, DOWN, LEFT, RIGHT;

    /** Sigla usata nei fatti ASP: deve combaciare con dir(u;d;l;r). */
    public char aspCode() {
        return switch (this) {
            case UP -> 'u';
            case DOWN -> 'd';
            case LEFT -> 'l';
            case RIGHT -> 'r';
        };
    }

    public static Direction fromAspCode(char c) {
        return switch (c) {
            case 'u' -> UP;
            case 'd' -> DOWN;
            case 'l' -> LEFT;
            case 'r' -> RIGHT;
            default -> throw new IllegalArgumentException("direzione ASP ignota: " + c);
        };
    }
}
