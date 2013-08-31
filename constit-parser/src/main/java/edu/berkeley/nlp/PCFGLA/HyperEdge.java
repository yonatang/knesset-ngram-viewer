/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

/**
 * @author petrov
 *
 */
import edu.berkeley.nlp.util.Numberer;

//lhuang
import java.util.Formatter;
import java.util.Locale;

public class HyperEdge {
	final int parentState, lChildState, rChildState, childState;
	boolean isUnary;
	double score, ruleScore;
	int start, split, end;
	int parentBest, lChildBest, rChildBest, childBest;
	boolean alreadyExpanded;

	// lhuang
	static Numberer tagNumberer = null;
	static boolean intokbest = false;
	static Grammar grammar = null;

	//private HyperEdge() { parentState = -9; lChildState = -9; rChildState = -9; childState = -9;};
	
	/*
	public boolean equals(HyperEdge other) {
		//System.out.println("other:" + other);
		return (
				   this.parentState == other.parentState 
				&& this.lChildState == other.lChildState
				&& this.rChildState == other.rChildState
				&& this.parentBest == other.parentBest
				&& this.lChildBest == other.lChildBest
				&& this.childState == other.childState
				&& this.start == other.start 
				&& this.split == other.split
				&& this.end   == other.end
				//&& this.score == other.score
				//&& this.ruleScore == other.ruleScore );
				);
	}*/
	
	// lhuang: binary
	public HyperEdge(int pState, int lState, int rState, int pBest, int lBest,
			int rBest, int begin, int mid, int finale, double cost,
			double ruleCost) {
		//NOTE: lState == rState == -1 for lexical items (where pState is pos-tag) 
		//System.out.println("@@lState:" + lState + " "  + rState);
		this.parentState = pState;
		this.lChildState = lState;
		this.rChildState = rState;
		this.parentBest = pBest;
		this.lChildBest = lBest;
		this.rChildBest = rBest;
		this.childState = -1;
		this.start = begin;
		this.split = mid;
		this.end = finale;
		this.score = cost;
		this.isUnary = false;
		this.ruleScore = ruleCost;
	}

	// lhuang: unary
	public HyperEdge(int pState, int cState, int pBest, int cBest, int begin,
			int finale, double cost, double ruleCost) {
		//assert(cState != -1);
		//System.out.println("@@cState:" + cState);
		this.parentState = pState;
		this.childState = cState;
		this.lChildState = -1;
		this.rChildState = -1;
		this.parentBest = pBest;
		this.childBest = cBest;
		this.start = begin;
		this.end = finale;
		this.score = cost;
		this.isUnary = true;
		this.ruleScore = ruleCost;
	}

	// lhuang
	protected static String labelstr(int label) {
		String s = (String) tagNumberer.object(label);
		if (s.endsWith("^g"))
			s = s.substring(0, s.length() - 2);

		if (s.equals("ROOT"))
			s = "TOP";

		if (s.equals("")) { // warning!!
			s = "XXX";
			// System.err.println("WARNING! state " + label + " is " +
			// (String)tagNumberer.object(label));
		}

		return s;
	}

	protected static String labelspan(int label, int left, int right) {
		return labelstr(label) + " [" + left + "-" + right + "]";
	}

	public String str() {
		String s = 
		  "[" + this.start + " " + this.end + "] " +
		  tagNumberer.object(this.parentState)
		+ " -> "
		+ (this.isUnary ?
			tagNumberer.object(this.childState)
			: (   tagNumberer.object(this.lChildState)
				+ " "
				+  tagNumberer.object(this.rChildState) ))
		+ " " 
		+ this.score;
		return s;
	}
	// lhuang
	public String toString() {
		// if (tagNumberer.object(parentState).equals("ROOT"))
		// System.out.println("ROOT " + Math.exp(score));

		// ridiculous Java
		StringBuilder sb = new StringBuilder();
		Formatter formatter = new Formatter(sb, Locale.US);
		StringBuilder sb2 = new StringBuilder();
		Formatter formatter2 = new Formatter(sb2, Locale.US);
		StringBuilder sb3 = new StringBuilder();
		Formatter formatter3 = new Formatter(sb3, Locale.US);
		String s = "";
		if (!intokbest) {
			if (!isUnary) {
				if (start != split)
					s = labelspan(parentState, start, end) + " -> "
							+ labelspan(lChildState, start, split) + "  "
							+ labelspan(rChildState, split, end);
				else
					// POS -> word case
					s = labelspan(parentState, start, end) + " -> word "
							+ start + " " + end; // @YG: added +end

				s += " : "
						+ (Math.abs(ruleScore) < 1e-6 ? 0 : formatter.format(
								"%4f", ruleScore))
						+ " "
						+ (Math.abs(score) < 1e-6 ? 0 : formatter2.format(
								"%4f", score)) + "\n";
			} else { // unary
				if (parentState != childState) {
					// make unary lhs symbol one level higher
					int inter = grammar.getUnaryIntermediate(
							(short) parentState, (short) childState);
					if (inter > 0) {
						// N.B. must have unique id!
						// A->B->C : p becomes:
						// B-A-C -> C : 0
						// A -> B-A-C : p
						String specialtag = "-" + labelstr(parentState) + "-"
								+ labelstr(childState);
						s = labelspan(inter, start, end) + specialtag + " -> ";
						s += labelspan(childState, start, end);
						s += " : "
								+ (Math.abs(ruleScore) < 1e-6 ? 0 : formatter
										.format("%4f", ruleScore))
								+ " "
								+ (Math.abs(score) < 1e-6 ? "0" : formatter2
										.format("%4f", score)) + "\n";
						s += labelspan(parentState, start, end) + "-1 -> ";
						s += labelspan(inter, start, end)
								+ specialtag
								+ " : 0 "
								+ (Math.abs(score) < 1e-6 ? "0" : formatter3
										.format("%4f", score)) + " \n"; // intermediate
																		// rule
					} else {
						s = labelspan(parentState, start, end) + "-1 -> ";
						s += labelspan(childState, start, end);
						s += " : "
								+ (Math.abs(ruleScore) < 1e-6 ? 0 : formatter
										.format("%4f", ruleScore))
								+ " "
								+ (Math.abs(score) < 1e-6 ? 0 : formatter2
										.format("%4f", score)) + "\n";
					}
				}
			}
		}
		return s;
	}

	// @@YG TODO : understand this guy and modify to fit lattice-init if needed!
	public boolean differsInPOSatMost(HyperEdge other, boolean[] grammarTags) {
		// assume the edges go over the same span and have the same head
		if (this.split != other.split)
			return false;
		// if (this.score==other.score)
		// return true;
		if (this.isUnary) {
			if (/* this.score==other.score */this.childBest == other.childBest
					&& other.childState == this.childState)
				return true;
		} else {
			// YG: I assume end-split is supposed to indicate that rChild is
			// POS, and extend the check with ||
			
			if ((this.end - this.split == 1 || !grammarTags[this.rChildState])
					&& this.lChildState == other.lChildState
					&& /* this.score==other.score */this.lChildBest == other.lChildBest
					&& !grammarTags[this.rChildState]
					&& !grammarTags[other.rChildState]
					)
				return true;
			// YG: I assume split-start is supposed to indicate that lChild is
			// POS, and extend the check with ||
			if ((this.split - this.start == 1 || !grammarTags[this.lChildState])
					&& this.rChildState == other.rChildState
					&& /* this.score==other.score */this.rChildBest == other.rChildBest
			//		// YG: added the next two lines, which I think were missing
					&& !grammarTags[this.lChildState]
					&& !grammarTags[other.lChildState]
					)
				return true;
			if (this.lChildState == other.lChildState
					&& this.rChildState == other.rChildState
					&& /* this.score==other.score */this.rChildBest == other.rChildBest
					&& this.lChildBest == other.lChildBest
					&& !grammarTags[this.lChildState]
					&& !grammarTags[other.lChildState]
					)
				return true;
		}

		return false;
	}

}
