package it.mac7.dlv2048.core;

import java.util.Random;

public class Game {

	    final static int target = 2048;

	    static int highest;
	    static int score;

	    private Random rand = new Random();

	    private Tile[][] tiles;
	    public final int SIZE = 4;
	    private State gamestate = State.start;
	    private boolean checkingAvailableMoves;

	    public Game() {}

	    public void startGame() {
	        if (gamestate != State.running) {
	            score = 0;
	            highest = 0;
	            gamestate = State.running;
	            tiles = new Tile[SIZE][SIZE];
	            addRandomTile(this.tiles);
	            addRandomTile(this.tiles);
	            
	        }
	    }
	    
	    private void addRandomTile(Tile[][] matrix) {
	        int pos = rand.nextInt(SIZE * SIZE);
	        int row, col;
	        do {
	            pos = (pos + 1) % (SIZE * SIZE);
	            row = pos / SIZE;
	            col = pos % SIZE;
	        } while (matrix[row][col] != null);
	 
	        int val = rand.nextInt(10) == 0 ? 4 : 2;
	        matrix[row][col] = new Tile(val);
	    }
	 
	    private boolean move(int countDownFrom, int yIncr, int xIncr) {
	        boolean moved = false;
	 
	        for (int i = 0; i < SIZE * SIZE; i++) {
	            int j = Math.abs(countDownFrom - i);
	 
	            int r = j / SIZE;
	            int c = j % SIZE;
	 
	            if (tiles[r][c] == null)
	                continue;
	 
	            int nextR = r + yIncr;
	            int nextC = c + xIncr;
	 
	            while (nextR >= 0 && nextR < SIZE && nextC >= 0 && nextC < SIZE) {
	 
	                Tile next = tiles[nextR][nextC];
	                Tile curr = tiles[r][c];
	 
	                if (next == null) {
	 
	                    if (checkingAvailableMoves)
	                        return true;
	 
	                    tiles[nextR][nextC] = curr;
	                    tiles[r][c] = null;
	                    r = nextR;
	                    c = nextC;
	                    nextR += yIncr;
	                    nextC += xIncr;
	                    moved = true;
	 
	                } else if (next.canMergeWith(curr)) {
	 
	                    if (checkingAvailableMoves)
	                        return true;
	 
	                    int value = next.mergeWith(curr);
	                    if (value > highest)
	                        highest = value;
	                    score += value;
	                    tiles[r][c] = null;
	                    moved = true;
	                    break;
	                } else
	                    break;
	            }
	        }
	 
	        if (moved) {
	            if (highest < target) {
	                clearMerged(this.tiles);
	                addRandomTile(this.tiles);
	                if (!movesAvailable()) {
	                    gamestate = State.over;
	                }
	            } else if (highest == target)
	                gamestate = State.won;
	        }
	 
	        return moved;
	    }
	 
	    public boolean moveUp() {
	        return move(0, -1, 0);
	    }
	 
	    public boolean moveDown() {
	        return move(SIZE * SIZE - 1, 1, 0);
	    }
	 
	    public boolean moveLeft() {
	        return move(0, 0, -1);
	    }
	 
	    public boolean moveRight() {
	        return move(SIZE * SIZE - 1, 0, 1);
	    }
	 
	    void clearMerged(Tile[][] tiles) {
	        for (Tile[] row : tiles)
	            for (Tile tile : row)
	                if (tile != null)
	                    tile.setMerged(false);
	    }
	 
	    boolean movesAvailable() {
	        checkingAvailableMoves = true;
	        boolean hasMoves = moveUp() || moveDown() || moveLeft() || moveRight();
	        checkingAvailableMoves = false;
	        return hasMoves;
	    }
	    
	    public State getState(){
	    	return gamestate;
	    }
	    
	    public Tile getTile(int row, int col){
	    	return tiles[row][col];
	    }
}
