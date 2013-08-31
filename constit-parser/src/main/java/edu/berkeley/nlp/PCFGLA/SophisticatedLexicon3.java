/*
 * TODO
 *   redo score and sigScore
 *
 */
package edu.berkeley.nlp.PCFGLA;

import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.PriorityQueue;
import edu.berkeley.nlp.util.ScalingTools;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.Writer;
import java.util.*;

/**
 * Simple default implementation of a lexicon, which scores word, tag pairs with
 * a smoothed estimate of P(tag|word)/P(tag).
 * 
 * for simplicity the lexicon will store words and tags as strings, while the
 * grammar will be using integers -> Numberer()
 */

public class SophisticatedLexicon3 extends SophisticatedLexicon {

   final static double SMOOTH_KC_TB = 0.00001;
   final static double SMOOTH_TB_W  = 0.0001;

	/**
	 * A POS tag has to have been attributed to more than this number of word
	 * types before it is regarded as an open-class tag. Unknown words will only
	 * possibly be tagged as open-class tags (unless flexiTag is on).
	 */
   public static int openClassTypesThreshold = 50;

	/**
	 * Start to aggregate signature-tag pairs only for words unseen in the first
	 * this fraction of the data.
	 */
	public static double fractionBeforeUnseenCounting = 0.5; // -> secondHalf
	/**
	 * Has counts for taggings in terms of unseen signatures. The IntTagWords
	 * are for (tag,sig), (tag,null), (null,sig), (null,null). (None for basic
	 * UNK if there are signatures.)
	 */
	protected static final int nullWord = -1;
	protected static final short nullTag = -1;
	double smoothInUnknownsThreshold = 100;

	public boolean isKnown(String word) {
		return wordCounter.keySet().contains(word);
	}
   public boolean isRare(String word) {
		return !(isKnown(word) && wordCounter.getCount(word) > 20);
   }

   //YG this is new
	Counter<String> kcCounter = new Counter<String>();

	public String toString() {
		Numberer n = Numberer.getGlobalNumberer("tags");
		StringBuilder sb = new StringBuilder();
		 for (int tag = 0; tag < wordToTagCounters.length; tag++) {
			String tagState = (String) n.object(tag);
			if (wordToTagCounters[tag] != null) {
				for (String word : wordToTagCounters[tag].keySet()) {
               if (sb !=null) {continue; }
					double[] scores = score(word, (short) tag, 0, false, false);
					sb.append(tagState + " " + word + " "
							+ Arrays.toString(scores) + "\n");
				}
			}
			if (unseenWordToTagCounters[tag] != null) {
				for (String word : unseenWordToTagCounters[tag].keySet()) {
               if (sb !=null) {continue; }
					double[] scores = score(word, (short) tag, 0, false, true);
					sb.append(tagState + " " + word + " "
							+ Arrays.toString(scores) + "\n");
				}
			}
         for (String kctag : this.kcCounter.keySet()) {
            for (int subs=0; subs<this.numSubStates[tag]; subs++) {
               double score = this.getKcGivenTb(kctag, (short)tag, subs);
               if (score>0) {
                  sb.append("SIG:" + kctag + "," + tagState + "-" + subs + "=" + score + "\n");
               }
            }
         }
		}
		return sb.toString();
	}


	public double[] score2(String word, short tag, int loc, boolean noSmoothing) {
      throw new RuntimeException("unimplemented");
	}

	/**
	 * <p>
	 * This condenses counting arrays into essential statistics. It is used
	 * after all calls to tallyStateSetTree and before any getScore calls.
	 * <p>
	 * Currently the trees are taken into account immediately, so this does
	 * nothing, but in the future this may contain some precomputation
	 */
	public void optimize() {
		// make up the set of which tags are preterminal tags
		allTags = new HashSet<Short>();
		for (short i = 0; i < wordToTagCounters.length; i++) {
			if (wordToTagCounters[i] != null) {
				allTags.add(i);
			}
		}
		for (short i = 0; i < unseenWordToTagCounters.length; i++) {
			if (wordToTagCounters[i] != null) {
				allTags.add(i);
			}
		}
		// remove the unlikely ones
		removeUnlikelyTags(threshold, -1.0);
	}

	/**
	 * Create a blank Lexicon object. Fill it by calling tallyStateSetTree for
	 * each training tree, then calling optimize().
	 * 
	 * @param numSubStates
	 */
	@SuppressWarnings("unchecked")
	public SophisticatedLexicon3(short[] numSubStates, int smoothingCutoff,
			double[] smoothParam, Smoother smoother, double threshold) {
      super(numSubStates, smoothingCutoff, smoothParam, smoother, threshold);
      //System.out.println("smooth is:" + this.smooth);
	}

	/**
	 * Split all substates in two, producing a new lexicon. The new Lexicon
	 * gives the same scores to words under both split versions of the tag.
	 * (Leon says: It may not be okay to use the same scores, but I think that
	 * symmetry is sufficiently broken in Grammar.splitAllStates to ignore the
	 * randomness here.)
	 * 
	 * @param randomness
	 *            , mode (currently ignored)
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public SophisticatedLexicon splitAllStates(int[] counts, boolean moreSubstatesThanCounts, int mode) { 
		short[] newNumSubStates = new short[numSubStates.length];
		newNumSubStates[0] = 1; // never split ROOT
		for (short i = 1; i < numSubStates.length; i++) {
			newNumSubStates[i] = (short) (numSubStates[i] * 2);
		}
		SophisticatedLexicon3 lexicon = new SophisticatedLexicon3(
				newNumSubStates, this.smoothingCutoff, smooth, smoother,
				this.threshold);
		// copy and alter all data structures
		lexicon.wordToTagCounters = new HashMap[numSubStates.length];
		lexicon.unseenWordToTagCounters = new HashMap[numSubStates.length];
		for (int tag = 0; tag < wordToTagCounters.length; tag++) {
			if (wordToTagCounters[tag] != null) {
				lexicon.wordToTagCounters[tag] = new HashMap<String, double[]>();
				for (String word : wordToTagCounters[tag].keySet()) {
					lexicon.wordToTagCounters[tag].put(word, new double[newNumSubStates[tag]]);
					for (int substate = 0; substate < wordToTagCounters[tag].get(word).length; substate++) {
						int splitFactor = 2;
						if (newNumSubStates[tag] == numSubStates[tag]) {
							splitFactor = 1;
						}
						for (int i = 0; i < splitFactor; i++) {
							lexicon.wordToTagCounters[tag].get(word)[substate
									* splitFactor + i] = (1.f / splitFactor)
									* wordToTagCounters[tag].get(word)[substate];
						}
					}
				}
			}
		}
		for (int tag = 0; tag < unseenWordToTagCounters.length; tag++) {
			if (unseenWordToTagCounters[tag] != null) {
				lexicon.unseenWordToTagCounters[tag] = new HashMap<String, double[]>();
				for (String word : unseenWordToTagCounters[tag].keySet()) {
					lexicon.unseenWordToTagCounters[tag].put(word,
							new double[newNumSubStates[tag]]);
					for (int substate = 0; substate < unseenWordToTagCounters[tag]
							.get(word).length; substate++) {
						int splitFactor = 2;
						if (newNumSubStates[tag] == numSubStates[tag]) {
							splitFactor = 1;
						}
						for (int i = 0; i < splitFactor; i++) {
							lexicon.unseenWordToTagCounters[tag].get(word)[substate
									* splitFactor + i] = (1.f / splitFactor)
									* unseenWordToTagCounters[tag].get(word)[substate];
						}
					}
				}
			}
		}
		lexicon.totalWordTypes = totalWordTypes;
		lexicon.totalTokens = totalTokens;
		lexicon.totalUnseenTokens = totalUnseenTokens;
		lexicon.totalWords = totalWords;
		lexicon.smoother = smoother;
		lexicon.typeTagCounter = new double[typeTagCounter.length][];
		lexicon.tagCounter = new double[tagCounter.length][];
		lexicon.unseenTagCounter = new double[unseenTagCounter.length][];
		lexicon.simpleTagCounter = new double[tagCounter.length];
		for (int tag = 0; tag < typeTagCounter.length; tag++) {
			lexicon.typeTagCounter[tag] = new double[newNumSubStates[tag]];
			lexicon.tagCounter[tag] = new double[newNumSubStates[tag]];
			lexicon.unseenTagCounter[tag] = new double[newNumSubStates[tag]];
			lexicon.simpleTagCounter[tag] = simpleTagCounter[tag];
			for (int substate = 0; substate < typeTagCounter[tag].length; substate++) {
				int splitFactor = 2;
				if (newNumSubStates[tag] == numSubStates[tag]) {
					splitFactor = 1;
				}
				for (int i = 0; i < splitFactor; i++) {
					lexicon.typeTagCounter[tag][substate * splitFactor + i] = (1.f / splitFactor)
							* typeTagCounter[tag][substate];
					lexicon.tagCounter[tag][substate * splitFactor + i] = (1.f / splitFactor)
							* tagCounter[tag][substate];
					lexicon.unseenTagCounter[tag][substate * splitFactor + i] = (1.f / splitFactor)
							* unseenTagCounter[tag][substate];
				}
			}
		}
		lexicon.allTags = new HashSet<Short>(allTags);
		lexicon.wordCounter = new Counter<String>();
		for (String word : wordCounter.keySet()) {
			lexicon.wordCounter.setCount(word, wordCounter.getCount(word));
		}
		lexicon.kcCounter = new Counter<String>();
		for (String word : kcCounter.keySet()) {
			lexicon.kcCounter.setCount(word, kcCounter.getCount(word));
		}
		lexicon.smoothingCutoff = smoothingCutoff;
		lexicon.addXSmoothing = addXSmoothing;
		lexicon.smoothInUnknownsThreshold = smoothInUnknownsThreshold;

		lexicon.wordNumberer = wordNumberer;
		return lexicon;
	}

	/**
	 * This routine returns a String that is the "signature" of the class of a
	 * word. For, example, it might represent whether it is a number of ends in
	 * -s. The strings returned by convention match the pattern UNK-.* , which
	 * is just assumed to not match any real word. Behavior depends on the
	 * unknownLevel (-uwm flag) passed in to the class. The recognized numbers
	 * are 1-5: 5 is fairly English-specific; 4, 3, and 2 look for various word
	 * features (digits, dashes, etc.) which are only vaguely English-specific;
	 * 1 uses the last two characters combined with a simple classification by
	 * capitalization.
	 * 
	 * @param word
	 *            The word to make a signature for
	 * @param loc
	 *            Its position in the sentence (mainly so sentence-initial
	 *            capitalized words can be treated differently)
	 * @return A String that is its signature (equivalence class)
	 */
	public String getSignature(String word, int loc) {
      if (null==null) throw new RuntimeException("not implemented");
		// int unknownLevel = Options.get().useUnknownWordSignatures;
      StringBuilder sb = new StringBuilder("UNK");

		if (word.length() == 0)
			return sb.toString();

		switch (unknownLevel) {

		case 5: {
			// Reformed Mar 2004 (cdm); hopefully much better now.
			// { -CAPS, -INITC ap, -LC lowercase, 0 } +
			// { -KNOWNLC, 0 } + [only for INITC]
			// { -NUM, 0 } +
			// { -DASH, 0 } +
			// { -last lowered char(s) if known discriminating suffix, 0}
			int wlen = word.length();
			int numCaps = 0;
			boolean hasDigit = false;
			boolean hasDash = false;
			boolean hasLower = false;
			for (int i = 0; i < wlen; i++) {
				char ch = word.charAt(i);
				if (Character.isDigit(ch)) {
					hasDigit = true;
				} else if (ch == '-') {
					hasDash = true;
				} else if (Character.isLetter(ch)) {
					if (Character.isLowerCase(ch)) {
						hasLower = true;
					} else if (Character.isTitleCase(ch)) {
						hasLower = true;
						numCaps++;
					} else {
						numCaps++;
					}
				}
			}
			char ch0 = word.charAt(0);
			String lowered = word.toLowerCase();
			if (Character.isUpperCase(ch0) || Character.isTitleCase(ch0)) {
				if (loc == 0 && numCaps == 1) {
					sb.append("-INITC");
					if (isKnown(lowered)) {
						sb.append("-KNOWNLC");
					}
				} else {
					sb.append("-CAPS");
				}
			} else if (!Character.isLetter(ch0) && numCaps > 0) {
				sb.append("-CAPS");
			} else if (hasLower) { // (Character.isLowerCase(ch0)) {
				sb.append("-LC");
			}
			if (hasDigit) {
				sb.append("-NUM");
			}
			if (hasDash) {
				sb.append("-DASH");
			}
			if (lowered.endsWith("s") && wlen >= 3) {
				// here length 3, so you don't miss out on ones like 80s
				char ch2 = lowered.charAt(wlen - 2);
				// not -ess suffixes or greek/latin -us, -is
				if (ch2 != 's' && ch2 != 'i' && ch2 != 'u') {
					sb.append("-s");
				}
			} else if (word.length() >= 5 && !hasDash
					&& !(hasDigit && numCaps > 0)) {
				// don't do for very short words;
				// Implement common discriminating suffixes
				/*
				 * if (Corpus.myLanguage==Corpus.GERMAN){
				 * sb.append(lowered.substring(lowered.length()-1)); }else{
				 */
				if (lowered.endsWith("ed")) {
					sb.append("-ed");
				} else if (lowered.endsWith("ing")) {
					sb.append("-ing");
				} else if (lowered.endsWith("ion")) {
					sb.append("-ion");
				} else if (lowered.endsWith("er")) {
					sb.append("-er");
				} else if (lowered.endsWith("est")) {
					sb.append("-est");
				} else if (lowered.endsWith("ly")) {
					sb.append("-ly");
				} else if (lowered.endsWith("ity")) {
					sb.append("-ity");
				} else if (lowered.endsWith("y")) {
					sb.append("-y");
				} else if (lowered.endsWith("al")) {
					sb.append("-al");
					// } else if (lowered.endsWith("ble")) {
					// sb.append("-ble");
					// } else if (lowered.endsWith("e")) {
					// sb.append("-e");
				}
			}
			break;
		}

		case 4: {
			boolean hasDigit = false;
			boolean hasNonDigit = false;
			boolean hasLetter = false;
			boolean hasLower = false;
			boolean hasDash = false;
			boolean hasPeriod = false;
			boolean hasComma = false;
			for (int i = 0; i < word.length(); i++) {
				char ch = word.charAt(i);
				if (Character.isDigit(ch)) {
					hasDigit = true;
				} else {
					hasNonDigit = true;
					if (Character.isLetter(ch)) {
						hasLetter = true;
						if (Character.isLowerCase(ch)
								|| Character.isTitleCase(ch)) {
							hasLower = true;
						}
					} else {
						if (ch == '-') {
							hasDash = true;
						} else if (ch == '.') {
							hasPeriod = true;
						} else if (ch == ',') {
							hasComma = true;
						}
					}
				}
			}
			// 6 way on letters
			if (Character.isUpperCase(word.charAt(0))
					|| Character.isTitleCase(word.charAt(0))) {
				if (!hasLower) {
					sb.append("-AC");
				} else if (loc == 0) {
					sb.append("-SC");
				} else {
					sb.append("-C");
				}
			} else if (hasLower) {
				sb.append("-L");
			} else if (hasLetter) {
				sb.append("-U");
			} else {
				// no letter
				sb.append("-S");
			}
			// 3 way on number
			if (hasDigit && !hasNonDigit) {
				sb.append("-N");
			} else if (hasDigit) {
				sb.append("-n");
			}
			// binary on period, dash, comma
			if (hasDash) {
				sb.append("-H");
			}
			if (hasPeriod) {
				sb.append("-P");
			}
			if (hasComma) {
				sb.append("-C");
			}
			if (word.length() > 3) {
				// don't do for very short words: "yes" isn't an "-es" word
				// try doing to lower for further densening and skipping digits
				char ch = word.charAt(word.length() - 1);
				if (Character.isLetter(ch)) {
					sb.append("-");
					sb.append(Character.toLowerCase(ch));
				}
			}
			break;
		}

		case 3: {
			// This basically works right, except note that 'S' is applied to
			// all
			// capitalized letters in first word of sentence, not just first....
			sb.append("-");
			char lastClass = '-'; // i.e., nothing
			char newClass;
			int num = 0;
			for (int i = 0; i < word.length(); i++) {
				char ch = word.charAt(i);
				if (Character.isUpperCase(ch) || Character.isTitleCase(ch)) {
					if (loc == 0) {
						newClass = 'S';
					} else {
						newClass = 'L';
					}
				} else if (Character.isLetter(ch)) {
					newClass = 'l';
				} else if (Character.isDigit(ch)) {
					newClass = 'd';
				} else if (ch == '-') {
					newClass = 'h';
				} else if (ch == '.') {
					newClass = 'p';
				} else {
					newClass = 's';
				}
				if (newClass != lastClass) {
					lastClass = newClass;
					sb.append(lastClass);
					num = 1;
				} else {
					if (num < 2) {
						sb.append('+');
					}
					num++;
				}
			}
			if (word.length() > 3) {
				// don't do for very short words: "yes" isn't an "-es" word
				// try doing to lower for further densening and skipping digits
				char ch = Character.toLowerCase(word.charAt(word.length() - 1));
				sb.append('-');
				sb.append(ch);
			}
			break;
		}

		case 2: {
			// {-ALLC, -INIT, -UC, -LC, zero} +
			// {-DASH, zero} +
			// {-NUM, -DIG, zero} +
			// {lowerLastChar, zeroIfShort}
			boolean hasDigit = false;
			boolean hasNonDigit = false;
			boolean hasLower = false;
			for (int i = 0; i < word.length(); i++) {
				char ch = word.charAt(i);
				if (Character.isDigit(ch)) {
					hasDigit = true;
				} else {
					hasNonDigit = true;
					if (Character.isLetter(ch)) {
						if (Character.isLowerCase(ch)
								|| Character.isTitleCase(ch)) {
							hasLower = true;
						}
					}
				}
			}
			if (Character.isUpperCase(word.charAt(0))
					|| Character.isTitleCase(word.charAt(0))) {
				if (!hasLower) {
					sb.append("-ALLC");
				} else if (loc == 0) {
					sb.append("-INIT");
				} else {
					sb.append("-UC");
				}
			} else if (hasLower) { // if (Character.isLowerCase(word.charAt(0)))
				// {
				sb.append("-LC");
			}
			// no suffix = no (lowercase) letters
			if (word.indexOf('-') >= 0) {
				sb.append("-DASH");
			}
			if (hasDigit) {
				if (!hasNonDigit) {
					sb.append("-NUM");
				} else {
					sb.append("-DIG");
				}
			} else if (word.length() > 3) {
				// don't do for very short words: "yes" isn't an "-es" word
				// try doing to lower for further densening and skipping digits
				char ch = word.charAt(word.length() - 1);
				sb.append(Character.toLowerCase(ch));
			}
			// no suffix = short non-number, non-alphabetic
			break;
		}

		default:
			sb.append("-");
			sb.append(word.substring(Math.max(word.length() - 2, 0), word
					.length()));
			sb.append("-");
			if (Character.isLowerCase(word.charAt(0))) {
				sb.append("LOWER");
			} else {
				if (Character.isUpperCase(word.charAt(0))) {
					if (loc == 0) {
						sb.append("INIT");
					} else {
						sb.append("UPPER");
					}
				} else {
					sb.append("OTHER");
				}
			}
		} // end switch (unknownLevel)
		// System.err.println("Summarized " + word + " to " + sb.toString());
		return sb.toString();
	} // end getSignature()

	public double[] score(StateSet stateSet, short tag, boolean noSmoothing,
			boolean isSignature) {
		return score(stateSet.getWord(), tag, stateSet.from, noSmoothing,
				isSignature);
	}

	public double[] score(String word, short tag, int loc, boolean noSmoothing,
			boolean isSignature) {
      return score(word,tag,loc,noSmoothing,isSignature,-2.0);
   }

   protected double getKcGivenTb(String kctag,short tbtag,int substate) {
         double c_T_KC = 0;
         if (unseenWordToTagCounters[tbtag] != null
               && unseenWordToTagCounters[tbtag].get(kctag) != null) {
            c_T_KC = unseenWordToTagCounters[tbtag].get(kctag)[substate];
         }
         // how often did we see this kctag
         double c_KC = kcCounter.getCount(kctag);
         //if (c_KC==0) {c_KC=0.5;}
         double c_U = totalUnseenTokens;
         double total = totalTokens; // seenCounter.getCount(iTW);
         double c_T = unseenTagCounter[tbtag][substate];// unSeenCounter.getCount(iTW);
         double c_Tseen = tagCounter[tbtag][substate]; // seenCounter.getCount(iTW);
         double p_T_U = c_T / c_U;

         double pb_T_KC = (c_T_KC + SMOOTH_KC_TB*p_T_U) / (c_KC + SMOOTH_KC_TB);
         //@@YG-TODO verify smoothing
         //if (kcCounter.getProbability(kctag)==0) {System.out.println("WTF! " + kctag);}
         double p_T = (c_Tseen / total);
         double p_KC = (c_KC>0 ? c_KC : 1.0) / totalUnseenTokens;
         double pb_KC_T = (pb_T_KC * p_KC / p_T);
         return pb_KC_T;
   }

   protected double kcScore(double kc_g_w, double c_W, String kctag, short tag, int substate) {
      double kc_given_tb = getKcGivenTb(kctag, tag, substate);
      // bayes inverse
      double p_W = (c_W>0 ? c_W : 1.0) / this.totalTokens;
      double c_KC = kcCounter.getCount(kctag);
      if (c_KC==0) c_KC=0.5;
      double p_KC = c_KC / kcCounter.totalCount();
      if (p_KC==0) {
         //System.out.println("Zeroprob KC! " + kctag);
         return 0;
      }
      double w_g_kc = kc_g_w * p_W / p_KC;
      double res= w_g_kc*kc_given_tb;
      if (Double.isNaN(res)) {
         //System.out.println("@@NaN in kcScore:" + kctag + " " + w_g_kc + " " + kc_given_tb);
         return 0;
      }
      return res;
   }

	/**
	 * Get the score of this word with this tag (as an IntTaggedWord) at this
	 * loc. (Presumably an estimate of P(word | tag).)
	 * <p>
	 * <i>Implementation documentation:</i> 
    * Seen: c_W = count(W) 
    *       c_TW = count(T,W) 
    *       c_T = count(T) 
    *       c_Tunseen = count(T) among new words in 2nd half
	 *       total = count(seen words) 
    *       totalUnseen = count("unseen" words) 
    *       p_T_U = Pmle(T|"unseen") 
    *       pb_T_W = P(T|W). 
    *             If (c_W > smoothInUnknownsThreshold) = c_TW/c_W 
    *             Else (if not smart mutation) pb_T_W = bayes prior smooth[1] with p_T_U 
    *       p_T= Pmle(T) 
    *       p_W = Pmle(W) 
    *       pb_W_T = pb_T_W * p_W / p_T [Bayes rule] Note that this doesn't really properly reserve mass to unknowns.
	 * 
	 * Unseen: 
    *       c_TS = count(T,Sig|Unseen) 
    *       c_S = count(Sig) 
    *       c_T = count(T|Unseen)
	 *       c_U = totalUnseen above 
    *       p_T_U = Pmle(T|Unseen) 
    *       pb_T_S = Bayes smooth of Pmle(T|S) with P(T|Unseen) [smooth[0]] 
    *       pb_W_T = P(W|T) inverted
	 * 
	 * @param iTW
	 *            An IntTaggedWord pairing a word and POS tag
	 * @param loc
	 *            The position in the sentence. <i> In the default implementation</i> 
    *            this is used only for unknown words to change their probability 
    *            distribution when sentence initial
    * @return A double valued score, usually P(word|tag)
	 */
	public double[] score(String origWord, short tag, int loc, boolean noSmoothing,
			boolean isSignature, double priorProb) {
		if (isConditional) throw new RuntimeException("unsupported");

      String[] word_poses = origWord.split("@!@");
      String word = word_poses[0];
      //System.out.println("word:" + word);
      String[] kctags = word_poses[1].split("@");
      double[] kc_given_w = new double[kctags.length];
      for (int i=0;i<kctags.length;i++) {
         kc_given_w[i] = Double.parseDouble(kctags[i].split("/")[1]);
         kctags[i] = kctags[i].split("/")[0];
      }

		double c_W = wordCounter.getCount(word);
		double pb_W_T = 0; // always set below

		// simulate no smoothing
		// smooth[0] = 0.0; smooth[1] = 0.0;

		double[] resultArray = new double[numSubStates[tag]];

		for (int substate = 0; substate < numSubStates[tag]; substate++) {
			boolean seen = (c_W > 0.0);
			if (!isSignature && (seen || noSmoothing)) {
				// known word model for P(T|W)
				double c_tag = tagCounter[tag][substate];
				double c_T = c_tag;// seenCounter.getCount(iTW);
				if (c_T == 0)
					continue;

				double c_TW = 0;
				if (wordToTagCounters[tag] != null && wordToTagCounters[tag].get(word) != null) {
					c_TW = wordToTagCounters[tag].get(word)[substate];
				}
				// if (c_TW==0) continue;

				double c_Tunseen = unseenTagCounter[tag][substate];
				double total = totalTokens;
				double totalUnseen = totalUnseenTokens;

				double p_T_U = (totalUnseen == 0) ? 1 : c_Tunseen / totalUnseen;
				double pb_T_W; // always set below

				if (c_W > smoothInUnknownsThreshold || noSmoothing) {
					// we've seen the word enough times to have confidence in
					// its tagging
               //System.out.println("no need to smooth:" + word + " " + c_W);
					if (noSmoothing && c_W == 0)
						pb_T_W = c_TW / 1;
					else
						pb_T_W = (c_TW + SMOOTH_TB_W * p_T_U) / (c_W + SMOOTH_TB_W);

               double p_T = (c_T / total);
               double p_W = (c_W / total);
               pb_W_T = pb_T_W * p_W / p_T;
				} else {
					// we haven't seen the word enough times to have confidence
					// in its tagging
               // @@YG TODO
					
               //pb_T_W = (c_TW + smooth[1] * p_T_U) / (c_W + smooth[1]);
               pb_T_W = (c_TW + SMOOTH_TB_W * p_T_U) / (c_W + SMOOTH_TB_W);

               double p_T = (c_T / total);
               double p_W = (c_W / total);
               pb_W_T = pb_T_W * p_W / p_T;

               double pb_W_T2 = 0;
               for (int i=0;i<kctags.length;i++) {
                  String kctag = kctags[i];
                  double kc_g_w = kc_given_w[i];
                  pb_W_T2 += kcScore(kc_g_w, c_W, kctag, tag, substate);
               }
               pb_W_T = 0.7*pb_W_T + 0.3*pb_W_T2;
               if (Double.isNaN(pb_W_T)) {System.out.println("@@1: " + pb_W_T);}
				}
				if (pb_T_W == 0)
					continue;


			} else { // unseen words
				// unknown word model for P(T|S): sum_{S}P(T|S)
            pb_W_T = 0;
            for (int i=0;i<kctags.length;i++) {
               String kctag = kctags[i];
               double kc_g_w = kc_given_w[i];
               pb_W_T += kcScore(kc_g_w, 1.0, kctag, tag, substate);
            }
            //if (Double.isNaN(pb_W_T) || pb_W_T==0) {System.out.println("@@2: " + pb_W_T + " " + word + " " + origWord);}
            //System.out.println("@@2: " + pb_W_T);
			}

			// give very low scores when needed, but try to avoid -Infinity
			if (pb_W_T == 0) {// NOT sure whether this is a good idea - slav. 
            //System.out.println("pb_W_T==0:" + origWord + " " + tag);
				resultArray[substate] = 1e-87;
         } else if (Double.isNaN(pb_W_T)) {
            System.out.println("pb_W_T==NaN:" + origWord + " " + tag);
			} else {
				resultArray[substate] = Double.isNaN(pb_W_T) ? 0 : pb_W_T;
			}
         if (Double.isNaN(resultArray[substate])) {
            System.out.println("@@NaN in substates. tag:" + tag + " substate:" + substate);
         }

		}
		smoother.smooth(tag, resultArray);
      int __i=0;
      for (double __d : resultArray) {
        if (Double.isNaN(__d)) {
            System.out.println("@@resultsarray has NaN after smoothing");
            //System.out.println("@@scores:" + word + " " + __i + " " + tag);
         }
         __i++;
      }

		if (logarithmMode) {
			for (int i = 0; i < resultArray.length; i++) {
				resultArray[i] = Math.log(resultArray[i]);
				if (Double.isNaN(resultArray[i]))
					resultArray[i] = Double.NEGATIVE_INFINITY;
			}
		}

		return resultArray;
	} // end score()

	public Counter<String> getWordCounter() {
		return wordCounter;
	}
	public Counter<String> getKcCounter() {
		return kcCounter;
	}

   //@@YG didn't touch 
	public void tieRareWordStats(int threshold) {
		for (int ni = 0; ni < numSubStates.length; ni++) {
			double unseenTagTokens = 0;
			for (int si = 0; si < numSubStates[ni]; si++) {
				unseenTagTokens += unseenTagCounter[ni][si];
			}
			if (unseenTagTokens == 0) {
				continue;
			}
			for (Map.Entry<String, double[]> wordToTagEntry : wordToTagCounters[ni].entrySet()) {
				String word = wordToTagEntry.getKey();
				double[] substateCounter = wordToTagEntry.getValue();
				if (wordCounter.getCount(word) < threshold + 0.5) {
					double wordTagTokens = 0;
					for (int si = 0; si < numSubStates[ni]; si++) {
						wordTagTokens += substateCounter[si];
					}
					for (int si = 0; si < numSubStates[ni]; si++) {
						substateCounter[si] = unseenTagCounter[ni][si]
								* wordTagTokens / unseenTagTokens;
					}
				}
			}
		}
	}

	/**
	 * Trains this lexicon on the Collection of trees.
	 */
   //@@YG I assume that if randomness >= 0, then this is the initialization.
	public void trainTree(Tree<StateSet> trainTree, double randomness,
			Lexicon oldLexicon, boolean secondHalf, boolean noSmoothing,
			int threshold) {
		// scan data
		// for all substates that the word's preterminal tag has
		double sentenceScore = 0;
		if (randomness == -1) {
			sentenceScore = trainTree.getLabel().getIScore(0);
			if (sentenceScore == 0) {
				System.out.println("Something is wrong with this tree. I will skip it.");
				return;
			}
		}
		int sentenceScale = trainTree.getLabel().getIScale();

		List<StateSet> words = trainTree.getYield();
		List<StateSet> tags = trainTree.getPreTerminalYield();
		if (words.size() != tags.size()) {
			System.out.println("Yield an preterminal yield do not match!");
			System.out.println(words.toString());
			System.out.println(tags.toString());
		}

		Counter<String> oldWordCounter = null;
		Counter<String> oldKcCounter = null;
		if (oldLexicon != null) {
			oldWordCounter = oldLexicon.getWordCounter();
			oldKcCounter = ((SophisticatedLexicon3)oldLexicon).getKcCounter();
		}
		// for all words in sentence
		for (int position = 0; position < words.size(); position++) {
			totalWords++;

         String origWord = words.get(position).getWord();
         String[] word_poses = origWord.split("@!@");
         String word = word_poses[0];
         String[] kctags = word_poses[1].split("@");
         double[] kc_given_w = new double[kctags.length];
         for (int i=0;i<kctags.length;i++) {
            kc_given_w[i] = Double.parseDouble(kctags[i].split("/")[1]);
            kctags[i] = kctags[i].split("/")[0];
         }


			int nSubStates = tags.get(position).numSubStates();
			short tag = tags.get(position).getState();

         for (String kctag : kctags) {
            kcCounter.incrementCount(kctag, 0);
         }

			if (unseenWordToTagCounters[tag] == null) {
				unseenWordToTagCounters[tag] = new HashMap<String, double[]>();
			}

         for (String kctag : kctags) {
            double[] substateCounter2 = unseenWordToTagCounters[tag].get(kctag);
            if (substateCounter2 == null) {
               // System.out.print("Sig "+sig+" word "+ word+" pos "+position);
               substateCounter2 = new double[numSubStates[tag]];
               unseenWordToTagCounters[tag].put(kctag, substateCounter2);
            }
         }

			// guarantee that the wordToTagCounter element exists so we can
			// tally the combination
			if (wordToTagCounters[tag] == null) {
				wordToTagCounters[tag] = new HashMap<String, double[]>();
			}
			double[] substateCounter = wordToTagCounters[tag].get(word);
			if (substateCounter == null) {
				substateCounter = new double[numSubStates[tag]];
				wordToTagCounters[tag].put(word, substateCounter);
			}

			double[] oldLexiconScores = null;
			if (randomness == -1) {
				oldLexiconScores = oldLexicon.score(origWord, tag, position,
						noSmoothing, false);
			}

			StateSet currentState = tags.get(position);
			double scale = ScalingTools.calcScaleFactor(currentState
					.getOScale()
					- sentenceScale)
					/ sentenceScore;
			// double weightSum = 0;

			for (short substate = 0; substate < nSubStates; substate++) {
				double weight = 1;
				if (randomness == -1) {
					// weight by the probability of seeing the tag and word
					// together, given the sentence
					if (!Double.isInfinite(scale)) {
						weight = currentState.getOScore(substate)
								* oldLexiconScores[substate] * scale;
					} else {
						weight = Math.exp(Math.log(ScalingTools.SCALE)
								* (currentState.getOScale() - sentenceScale)
								- Math.log(sentenceScore)
								+ Math.log(currentState.getOScore(substate))
								+ Math.log(oldLexiconScores[substate]));
					}
					// weightSum+=weight;
				} else if (randomness == 0) {
					// for the baseline
               if (currentState.initialSubstate < 0) {
                  weight = 1;
               } else {
                  weight = currentState.initialSubstate == substate ?  1 : 0;
                  //System.out.println("tag is " + tag + " substate is " + substate + " and weight is " + weight);
               }
				} else {
					// add a bit of randomness
               //System.err.print("randmoness in lexicon"); // this should happen only at initialization
               if (currentState.initialSubstate < 0) 
                  weight = GrammarTrainer.RANDOM.nextDouble() * randomness / 100.0 + 1.0;
               else {
                  weight = (currentState.initialSubstate == substate) ? GrammarTrainer.RANDOM.nextDouble() * randomness / 100.0 + 1.0 : 0.0;
                  //System.out.println("tag is " + tag + " substate is " + substate + " and weight is " + weight);
               }
				}
				if (weight == 0) {
					continue;
				}
				// tally in the tag with the given weight
				substateCounter[substate] += weight;
				// update the counters
				tagCounter[tag][substate] += weight;
				wordCounter.incrementCount(word, weight);
				totalTokens += weight;

				if (Double.isNaN(totalTokens)) {
					throw new Error(
							"totalTokens is NaN: this would fail if we let it continue!");
				}

            // re-counting the rare events as rare
				if (randomness >= 0 || oldLexicon != null && oldWordCounter.getCount(word) < threshold + 0.5) {
               /* I have a rare word, its tbtag and substate.
                * I also have p(kc|word) for all KC tags.
                *
                * I want to add counts for c_rare_KC_TB, c_rare_KC, c_rare_TB.
                * These will then be used for calculating the lexicon-smoothed scores.
                *
                * c_rare_TB += expected probability of seeing TB (denoted as X)
                * c_rare_KC += expected probability of seeing KC here (denoted as Y)
                * c_rare_KC_TB += expected prob of seeing KC and TB (denoted as Z)
                *
                * Y = Z 
                * X = sum_{KC} Z
                * 
                */
               //System.out.println("training lexicon and I'm in here!!" + threshold + " " + oldWordCounter.getCount(word) + " " + word + " substate:" + substate + " tag:" + tag);
               for (int i=0;i<kctags.length;i++) {
                  String kctag = kctags[i];
                  double kctagWeight=0; // set below
                  if (randomness>=0 && oldLexicon==null) {
                     /* initial round: 
                      * @@TODO YG am I sure?
                      */
                     kctagWeight = weight/kctags.length;  
                  } else {
                     double kctag_g_w = kc_given_w[i];
                     // bayes inverse
                     double c_W = oldWordCounter.getCount(word);
                     double p_W = (c_W>0 ? c_W : 1.0) / ((SophisticatedLexicon3)oldLexicon).totalTokens;

                     //double p_KC = ((SophisticatedLexicon3)oldLexicon).kcCounter.getProbability(kctag);
                     //if (p_KC==0) {System.out.println("here??! " + kctag);}
                     double c_KC = kcCounter.getCount(kctag);
                     if (c_KC==0) c_KC=0.5;
                     double p_KC = c_KC / kcCounter.totalCount();
                     double w_g_kc = kctag_g_w * p_W / p_KC;
                     // weight is p(tbtag,w)
                     // and we need p(kctag=X|tbtag,w) = p(kctag,tbtag,w)/p(tbtag,w) = p(tbtag)*p(kctag|tbtag)*p(w|kctag)/weight
                     // =  currentState.getOScore(substate)*oldLexicon.getKcGivenTb(kctag,tbtag,substate)*w_g_kc/weight
                     double kc_g_tb = ((SophisticatedLexicon3)oldLexicon).getKcGivenTb(kctag,tag,substate);
                     if (randomness==-1)
                        //kctagWeight = currentState.getOScore(substate)*kc_g_tb*w_g_kc/weight;
                        //kctagWeight = 1.0*kc_g_tb*w_g_kc/weight;
                        //kctagWeight = weight/kctags.length; //1.0*kc_g_tb*w_g_kc/weight;
                        kctagWeight = weight*kc_g_tb;
                     else // (first round, but with initialized lexicon)
                        kctagWeight = weight/kctags.length; //1.0*kc_g_tb*w_g_kc/weight;
                  }

                  kcCounter.incrementCount(kctag, kctagWeight);

                  double[] substateCounter2 = unseenWordToTagCounters[tag].get(kctag);
                  substateCounter2[substate] += kctagWeight; 

                  unseenTagCounter[tag][substate] += kctagWeight;
                  totalUnseenTokens += kctagWeight;
                  if (randomness==-1) {
                     //System.out.println("@@counting " + kctag + ":" + Numberer.getGlobalNumberer("tags").object(tag)+ "-"+substate+" +=" + kctagWeight + " " + weight);
                  }
               }
				}
			}
		}
	}

	/**
	 * Merge states, combining information about words we have seen. THIS DOES
	 * NOT UPDATE INFORMATION FOR UNSEEN WORDS! For that, retrain the Lexicon!
	 * 
	 * @param mergeThesePairs
	 * @param mergeWeights
	 */
	public void mergeStates(boolean[][][] mergeThesePairs,
			double[][] mergeWeights) {
      //@@YG is it correct to use the parent's?
      super.mergeStates(mergeThesePairs, mergeWeights);
   }

	public Map<String, double[][]> getUnseenScores() {
      throw new RuntimeException("unimplemented");
   }

   //@@YG this is the original. TODO add unseen/rare words info?
	public void removeUnlikelyTags(double threshold, double exponent) {
		// System.out.print("Removing unlikely tags...");
		if (isLogarithmMode())
			threshold = Math.log(threshold);
		int removed = 0, total = 0;
      for (int tag = 0; tag < numSubStates.length; tag++) {
         double[] c_TW;
         if (wordToTagCounters[tag] != null) {
            for (String word : wordToTagCounters[tag].keySet()) {
               c_TW = wordToTagCounters[tag].get(word);
               for (int substate = 0; substate < numSubStates[tag]; substate++) {
                  total++;
                  if (c_TW[substate] < threshold) {
                     c_TW[substate] = 0;
                     removed++;
                  }
               }
            }
         }
      }
   }

	public void logarithmMode() {
		logarithmMode = true;
	}

	public boolean isLogarithmMode() {
		return logarithmMode;
	}

	public SophisticatedLexicon3 projectLexicon(double[] condProbs,
			int[][] mapping, int[][] toSubstateMapping) {
		short[] newNumSubStates = new short[numSubStates.length];
		for (int state = 0; state < numSubStates.length; state++) {
			newNumSubStates[state] = (short) toSubstateMapping[state][0];
		}
		Smoother newSmoother = this.smoother.copy();
		newSmoother.updateWeights(toSubstateMapping);
		SophisticatedLexicon3 newLexicon = new SophisticatedLexicon3(
				newNumSubStates, this.smoothingCutoff, this.smooth,
				newSmoother, this.threshold);

		double[][] newTagCounter = new double[newNumSubStates.length][];
		double[][] newUnseenTagCounter = new double[newNumSubStates.length][];
      for (int tag = 0; tag < numSubStates.length; tag++) {
         // update tag counters
         newTagCounter[tag] = new double[newNumSubStates[tag]];
         newUnseenTagCounter[tag] = new double[newNumSubStates[tag]];
         for (int substate = 0; substate < numSubStates[tag]; substate++) {
            newTagCounter[tag][toSubstateMapping[tag][substate + 1]] += condProbs[mapping[tag][substate]]
                  * tagCounter[tag][substate];
         }
         for (int substate = 0; substate < numSubStates[tag]; substate++) {
            newUnseenTagCounter[tag][toSubstateMapping[tag][substate + 1]] += condProbs[mapping[tag][substate]]
                  * unseenTagCounter[tag][substate];
         }
         // update wordToTagCounters
         if (wordToTagCounters[tag] != null) {
            newLexicon.wordToTagCounters[tag] = new HashMap<String, double[]>();
            for (String word : wordToTagCounters[tag].keySet()) {
               double[] scores = wordToTagCounters[tag].get(word);
               double[] newScores = new double[newNumSubStates[tag]];
               for (int i = 0; i < numSubStates[tag]; i++) {
                  newScores[toSubstateMapping[tag][i + 1]] += condProbs[mapping[tag][i]]
                        * scores[i];
               }
               newLexicon.wordToTagCounters[tag].put(word, newScores);
            }
         }
         // update wordToTagCounters
         if (unseenWordToTagCounters[tag] != null) {
            newLexicon.unseenWordToTagCounters[tag] = new HashMap<String, double[]>();
            for (String word : unseenWordToTagCounters[tag].keySet()) {
               double[] scores = unseenWordToTagCounters[tag]
                     .get(word);
               double[] newScores = new double[newNumSubStates[tag]];
               for (int i = 0; i < numSubStates[tag]; i++) {
                  newScores[toSubstateMapping[tag][i + 1]] += condProbs[mapping[tag][i]]
                        * scores[i];
               }
               newLexicon.unseenWordToTagCounters[tag].put(word,
                     newScores);
            }
         }
      }

		newLexicon.totalWordTypes = totalWordTypes;
		newLexicon.totalTokens = totalTokens;
		newLexicon.totalUnseenTokens = totalUnseenTokens;
		newLexicon.totalWords = totalWords;
		// newLexicon.smoother = smoother;
		newLexicon.allTags = null;
		newLexicon.wordCounter = new Counter<String>();
		newLexicon.kcCounter = new Counter<String>();
		for (String word : wordCounter.keySet()) {
			newLexicon.wordCounter.setCount(word, wordCounter.getCount(word));
		}
		for (String kctag : kcCounter.keySet()) {
			newLexicon.kcCounter.setCount(kctag, kcCounter.getCount(kctag));
		}
		newLexicon.smoothingCutoff = smoothingCutoff;
		newLexicon.addXSmoothing = addXSmoothing;
		newLexicon.smoothInUnknownsThreshold = smoothInUnknownsThreshold;

		newLexicon.tagCounter = newTagCounter;
		newLexicon.unseenTagCounter = newUnseenTagCounter;
		newLexicon.numSubStates = newNumSubStates;
		newLexicon.wordNumberer = wordNumberer;
		newLexicon.unknownLevel = unknownLevel;
		return newLexicon;
	}

	public SophisticatedLexicon3 copyLexicon() {
		short[] newNumSubStates = numSubStates.clone();
		SophisticatedLexicon3 newLexicon = new SophisticatedLexicon3(
				newNumSubStates, this.smoothingCutoff, this.smooth,
				this.smoother, this.threshold);

		double[][] newTagCounter = ArrayUtil.copy(tagCounter);
		double[][] newUnseenTagCounter = ArrayUtil.copy(unseenTagCounter);
		for (int tag = 0; tag < numSubStates.length; tag++) {
			if (wordToTagCounters[tag] != null) {
				newLexicon.wordToTagCounters[tag] = new HashMap<String, double[]>();
				for (String word : wordToTagCounters[tag].keySet()) {
					double[] scores = wordToTagCounters[tag].get(word);
					double[] newScores = scores.clone();
					newLexicon.wordToTagCounters[tag].put(word, newScores);
				}
			}
			// update wordToTagCounters
			if (unseenWordToTagCounters[tag] != null) {
				newLexicon.unseenWordToTagCounters[tag] = new HashMap<String, double[]>();
				for (String word : unseenWordToTagCounters[tag].keySet()) {
					double[] scores = unseenWordToTagCounters[tag].get(word);
					double[] newScores = scores.clone();
					newLexicon.unseenWordToTagCounters[tag]
							.put(word, newScores);
				}
			}
		}

		if (conditionalWeights != null)
			newLexicon.conditionalWeights = conditionalWeights.clone();
		newLexicon.isConditional = isConditional;
		newLexicon.totalWordTypes = totalWordTypes;
		newLexicon.totalTokens = totalTokens;
		newLexicon.totalUnseenTokens = totalUnseenTokens;
		newLexicon.totalWords = totalWords;
		newLexicon.smoother = smoother;
		newLexicon.allTags = new HashSet<Short>(allTags);
		newLexicon.wordCounter = new Counter<String>();
		for (String word : wordCounter.keySet()) {
			newLexicon.wordCounter.setCount(word, wordCounter.getCount(word));
		}
		newLexicon.kcCounter = new Counter<String>();
		for (String word : kcCounter.keySet()) {
			newLexicon.kcCounter.setCount(word, kcCounter.getCount(word));
		}
		newLexicon.smoothingCutoff = smoothingCutoff;
		newLexicon.addXSmoothing = addXSmoothing;
		newLexicon.smoothInUnknownsThreshold = smoothInUnknownsThreshold;

		newLexicon.tagCounter = newTagCounter;
		newLexicon.unseenTagCounter = newUnseenTagCounter;
		newLexicon.numSubStates = newNumSubStates;

		newLexicon.wordNumberer = this.wordNumberer;
		newLexicon.unknownLevel = this.unknownLevel;
		return newLexicon;
	}

	public void delinearizeLexicon(double[] probs) {
      throw new RuntimeException("unsupported");
	}

	public void setConditional(boolean b) {
      if (b) { throw new RuntimeException("unsupported"); }
	}

	public double[] scoreConditional(String word, short tag, int loc,
			boolean noSmoothing, boolean isSignature) {
      throw new RuntimeException("unsupported");
	}

	public double[] getConditionalSignatureScore(String sig, short tag,
			boolean noSmoothing) {
      throw new RuntimeException("unsupported");
	}

	public double[] getConditionalWordScore(String word, short tag,
			boolean noSmoothing) {
      throw new RuntimeException("unsupported");
	}

	@SuppressWarnings("unchecked")
	public SophisticatedLexicon3 remapStates(Numberer thisNumberer,
			Numberer newNumberer) {
      throw new RuntimeException("unsupported");
	}
}
