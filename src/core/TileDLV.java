package core;

public class TileDLV {

	private int x;
	private int y;
	private int value;
	private int merge;
	private int scoreDLV;
	private int id;
	
	public TileDLV() {}
	
	public TileDLV(int x, int y, int value, int merge, int scoreDLV, int id) {
		this.x = x;
		this.y = y;
		this.value = value;
		this.merge = merge;
		this.scoreDLV = scoreDLV;
		this.id = id;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public int getMerge() {
		return merge;
	}

	public void setMerge(int merge) {
		this.merge = merge;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}
	@Override
	public String toString() {
		return x + " " + y + ", value: " + value;
	}

	public int getScoreDLV() {
		return scoreDLV;
	}

	public void setScoreDLV(int scoreDLV) {
		this.scoreDLV = scoreDLV;
	}

	
}
