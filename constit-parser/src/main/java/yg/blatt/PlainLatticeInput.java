package yg.blatt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class PlainLatticeInput implements LatticeInput {

	private int _length;
	List<WordData> _spansData = new ArrayList<WordData>();
	HashMap<String, String> _props = new HashMap<String, String>();
	public final String DEFAULT_PROP = "";

	public String getWord(int start, int end, int tag) { // @YG TODO make more
		// efficient..
		// System.out.println("getWord " + start + " " + end);
		// if (true) return "foo";
		for (WordData wd : _spansData)
			if (wd.start == start && wd.end == end && wd.tagNum==tag) {
				// System.out.println("return: " + wd.form);
				return wd.form; // + ":" + start + "/" + end;
			}
		throw new RuntimeException("no word in given span:" + start + "," + end);
	}

	public void addWordData(int start, int end, String form, String tag,
			double prob) {
		this._spansData.add(new WordData(start, end, form, tag, prob));
		if (end > _length)
			_length = end;
	}

	public void addWordData(int start, int end, String form, short tag,
			double prob) {
		this._spansData.add(new WordData(start, end, form, tag, prob));
		if (end > _length)
			_length = end;
	}

	public void addWordData(int start, int end, String form, String tag) {
		this._spansData.add(new WordData(start, end, form, tag, -1.0));
		if (end > _length)
			_length = end;
	}

	public void addWordData(int start, int end, String form, short tag) {
		this._spansData.add(new WordData(start, end, form, tag, -1.0));
		if (end > _length)
			_length = end;
	}

	public int length() {
		return this._length;
	}

	public List<WordData> spansData() {
		return this._spansData;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("lattice:");
		for (WordData wd : _spansData) {
			sb.append(" ");
			sb.append(wd.form);
		}
		return sb.toString();
	}

	public static LatticeInput fromWords(List<String> words, TypesLexicon lex) {
		PlainLatticeInput lat = new PlainLatticeInput();
		int start = 0;
		int end = start + 1;
		for (String word : words) {
			end = start + 1;
			for (short tag : lex.possibleNumericTagsForWord(word)) {
				lat.addWordData(start, end, word, tag);
			}
			start++;
		}
		return lat;
	}

	@Override
	public String getProperty(String prop) {
		if (this._props.containsKey(prop)) {
			return this._props.get(prop);
		}
		return DEFAULT_PROP ;
	}

	@Override
	public void setProperty(String prop, String val) {
		this._props.put(prop, val);
	}

	@Override
	public String stringRep() {
		HashSet<String> seen = new HashSet<String>();
		StringBuilder sb = new StringBuilder("@lat@ ");
		StringBuilder sb2 = new StringBuilder();
		for (WordData wd : this.spansData()) {
			sb2.append(wd.start).append("-").append(wd.end);
			String sig = sb2.toString();
			sb2.delete(0, sb2.length());
			if (seen.contains(sig)) continue;
			seen.add(sig);
			sb.append(sig).append(":").append(wd.form).append(" "); 	
		}
		return sb.toString();
	}
}

