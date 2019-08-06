package core;

public class Tile {

	private boolean merged;
    private int value;
 
    Tile(int val) {
        value = val;
    }
 
    public int getValue() {
        return value;
    }
    
    boolean getMerged(){
    	return merged;
    }
 
    void setMerged(boolean m) {
        merged = m;
    }
 
    boolean canMergeWith(Tile other) {
        return !merged && other != null && !other.merged && value == other.getValue();
    }
 
    int mergeWith(Tile other) {
        if (canMergeWith(other)) {
            value *= 2;
            merged = true;
            return value;
        }
        return -1;
    }
}
