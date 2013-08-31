/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.List;
import java.util.Set;

import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Counter;

/**
 * @author petrov
 * 
 */
public interface ExtLexicon extends Lexicon {

   // priorProb is p(t|w) to be used as the lexicon sees fit
	public double[] score(String word, short tag, int loc, boolean noSmoothing,
			boolean isSignature, double priorProb);

}
