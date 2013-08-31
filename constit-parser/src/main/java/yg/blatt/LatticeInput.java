package yg.blatt;

import java.util.List;

public interface LatticeInput {

	// num of items
	public int length();

	public List<WordData> spansData();

	// throws RuntimeException (IndexError) if no word exist between start and
	// end.
	public String getWord(int start, int end, int tag);

	public String getProperty(String prop);
	public void setProperty(String prop, String val);

	public String stringRep();
}
