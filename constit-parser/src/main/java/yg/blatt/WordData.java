package yg.blatt;

import edu.berkeley.nlp.util.Numberer;

public class WordData {

	private static Numberer _tagNumberer = Numberer.getGlobalNumberer("tags");

	public int start;
	public int end;
	public String form;
	public String tagString;
	public short tagNum;
	public double prob;

	public WordData(int start, int end, String form, String tag, double prob) {
		this.start = start;
		this.end = end;
		this.form = form;
		this.tagString = tag;
		this.tagNum = (short) WordData._tagNumberer.number(tag);
		this.prob = prob;
	}

	public WordData(int start, int end, String form, short tag, double prob) {
		// System.out.println("tagnum:" + tag);
		this.start = start;
		this.end = end;
		this.form = form;
		this.tagNum = tag;
		this.tagString = (String) _tagNumberer.object(tag);
		// System.out.println("tagstr:" + this.tagString);
		this.prob = prob;
	}

}
