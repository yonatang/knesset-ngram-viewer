package yg.blatt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import edu.berkeley.nlp.PCFGLA.Lexicon;

// TODO this guy is terribly inefficient.  Add caching or return the probs with the tags..
public class BerkeleyTypesLexicon implements TypesLexicon {

	List<edu.berkeley.nlp.PCFGLA.Lexicon> blexes = new ArrayList<edu.berkeley.nlp.PCFGLA.Lexicon>();
   Set<Short> possibleTags = new HashSet<Short>();
	
	public BerkeleyTypesLexicon(Lexicon blex) {
		this.blexes.add(blex);
      this.possibleTags.addAll(blex.getAllTags());
	}
   public Set<Short> allPossibleTags() { return this.possibleTags; }

	@Override
	public List<Short> possibleNumericTagsForWord(String word, int loc) {
		List<Short> tags = new ArrayList<Short>();

		// @@ TODO: add real support for multiple lexicons (for the product-of-grammars)
		for (Lexicon blex : this.blexes) {
			for (short tag : blex.getAllTags()) {
				double[] lexiconScores = blex.score(word, tag, loc, false, false);
				for (double prob : lexiconScores) {
					// System.out.println("pr:"+prob);
					// @YG does not work in log-mode! TODO fix then check viterbi
					if (blex.isLogarithmMode() ? prob > Double.NEGATIVE_INFINITY
							: prob > 0) {
						tags.add(tag);
						break;
					}
				}
			}
		}
		return tags;
	}

	@Override
	public List<Short> possibleNumericTagsForWord(String word) {
		return this.possibleNumericTagsForWord(word, 2);
	}
}
