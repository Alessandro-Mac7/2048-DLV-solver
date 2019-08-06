package core;

import java.util.LinkedList;
import java.util.Random;

import dlv.Solver;

public class Game {
	 
	    final static int target = 2048;
	 
	    static int highest;
	    static int score;
	 
	    private Random rand = new Random();
	 
	    private Tile[][] tiles;
	    public final int SIZE = 4;
	    private State gamestate = State.start;
	    private boolean checkingAvailableMoves;
		
		//DLV
	    public Solver solver;
	    public LinkedList<TileDLV> allTile;
		private boolean checkingAvailableMovesDLV;
		private int scoreDLV;


	    
	    public Game() {}
	 
	    public void startGame() {
	        if (gamestate != State.running) {
	        	solver = new Solver();
	            score = 0;
	            scoreDLV = 0;
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
	    
	    
	    //DLV AI
	    boolean movesAvailiableDLV(Tile[][] matrix){
	    	checkingAvailableMovesDLV = true;
	        boolean hasMoves = moveDLV(0, -1, 0, matrix) || moveDLV(SIZE * SIZE - 1, 1, 0, matrix) || moveDLV(0, 0, -1, matrix) || moveDLV(SIZE * SIZE - 1, 0, 1, matrix);
	        checkingAvailableMovesDLV = false;
	        return hasMoves;
	    }
	    
	    Tile[][] cloneTile(Tile[][] matrix){
	    	Tile[][] newmatrix = new Tile[SIZE][SIZE]; 
	    	for (int i = 0; i < SIZE; i++) {
				for (int j = 0; j < SIZE; j++) {
					if(matrix[i][j]!=null){
						int val = matrix[i][j].getValue();
						Tile t = new Tile(val);
						newmatrix[i][j] = t;
					}
				}
			}
	    	return newmatrix;
	    }
	    
	    public LinkedList<TileDLV> generateDLVMatrix(){
	    	
	    	LinkedList<TileDLV> dlv_tiles = null;
	    	
	    	Tile[][] right = cloneTile(this.tiles);
	    	Tile[][] left = cloneTile(this.tiles);
	    	Tile[][] up = cloneTile(this.tiles);
	    	Tile[][] down = cloneTile(this.tiles);
	    	
	    	if(moveDLV(0, -1, 0, up)){
	    		moveDLV(0, -1, 0, up);
	    		dlv_tiles = convert(up, 0, dlv_tiles);
	    		scoreDLV = 0;
	    	}
	    	
	    	if(moveDLV(SIZE * SIZE - 1, 1, 0, down)){
	    		moveDLV(SIZE * SIZE - 1, 1, 0, down);
	    		dlv_tiles = convert(down, 1, dlv_tiles);
	    		scoreDLV = 0;
	    	}
	    	
	    	if(moveDLV(0, 0, -1, left)){
	    		moveDLV(0, 0, -1, left);
	    		dlv_tiles = convert(left, 2, dlv_tiles);
	    		scoreDLV = 0;
	    	}
	    	
	    	if(moveDLV(SIZE * SIZE - 1, 0, 1, right)){
	    		dlv_tiles = convert(right, 3, dlv_tiles);
	    		scoreDLV = 0;
	    	}
	    	
	    	return dlv_tiles;
	    }
	    
	    public LinkedList<TileDLV> convert (Tile[][] matrix, int id, LinkedList<TileDLV> tilesDLV){
	    	LinkedList<TileDLV> list = null;
	    	if(tilesDLV == null){
	    		list = new LinkedList<TileDLV>();
	    	}
	    	else
	    		list = tilesDLV;
	    	for (int i = 0; i < SIZE; i++) {
				for (int j = 0; j < SIZE; j++) {
					if(matrix[i][j]!=null){
						int val = matrix[i][j].getValue();
						TileDLV t;
						if(matrix[i][j].getMerged())
							t = new TileDLV(i,j,val,1,scoreDLV,id);
						else
							t = new TileDLV(i,j,val,0,scoreDLV,id);
						list.add(t);
					}
				}
			}
	    	return list;
	    }  
	    
	    private boolean moveDLV(int countDownFrom, int yIncr, int xIncr, Tile[][] matrix) {
	        boolean moved = false;
	 
	        for (int i = 0; i < SIZE * SIZE; i++) {
	            int j = Math.abs(countDownFrom - i);
	 
	            int r = j / SIZE;
	            int c = j % SIZE;
	 
	            if (matrix[r][c] == null)
	                continue;
	 
	            int nextR = r + yIncr;
	            int nextC = c + xIncr;
	 
	            while (nextR >= 0 && nextR < SIZE && nextC >= 0 && nextC < SIZE) {
	 
	                Tile next = matrix[nextR][nextC];
	                Tile curr = matrix[r][c];
	 
	                if (next == null) {
	 
	                    if (checkingAvailableMovesDLV)
	                        return true;
	 
	                    matrix[nextR][nextC] = curr;
	                    matrix[r][c] = null;
	                    r = nextR;
	                    c = nextC;
	                    nextR += yIncr;
	                    nextC += xIncr;
	                    moved = true;
	 
	                } else if (next.canMergeWith(curr)) {
	 
	                    if (checkingAvailableMovesDLV)
	                        return true;
	 
	                    int value = next.mergeWith(curr);
	                    if (value > highest)
	                        highest = value;
	                    scoreDLV += value;
	                    matrix[r][c] = null;
	                    moved = true;
	                    break;
	                } else
	                    break;
	            }
	        }
	 
	        return moved;
	    }
	    
	    int countMerged(Tile[][] tiles) {
	    	int c = 0;
	        for (Tile[] row : tiles){
	        	for (Tile tile : row){
	        		if (tile != null && tile.getMerged()){
	                	c++;
	                }
	        	}
	        }
	        return c;
	    }
	    
	    
	    public void solve(){
	    	LinkedList<TileDLV> test = generateDLVMatrix();

	    	switch(solver.solve(test)){
	    		case 0:
		    		if(!moveUp())
		    			moveDown();
		    		else
		    			moveUp();
		    		System.out.println("Mossa ottima: sopra!");
		    		break;
	    		case 1:
		    		moveDown();
		    		System.out.println("Mossa ottima: sotto!");
		    		break;
	    		case 2:
		    		moveLeft();
		    		System.out.println("Mossa ottima: sinistra!");
		    		break;
	    		case 3:
		    		moveRight();
		    		System.out.println("Mossa ottima: destra!");
		    		break;
	    		default:
	    			moveUp();
	    			break;
	    	}
    		System.out.println(score + " DLV:" + scoreDLV);

	    }
}
