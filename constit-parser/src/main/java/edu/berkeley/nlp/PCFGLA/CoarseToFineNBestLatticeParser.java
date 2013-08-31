/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

//////////////////////////////////////////////////////////////////
//
// WHY??? am I getting many more POS in the forest when I set LatticeInputsFromRow to have 2 instead of 1 wide items???
//
/////////////////////////////////////////////////////////////////

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import yg.blatt.LatticeInput;
import yg.blatt.WordData;
import edu.berkeley.nlp.PCFGLA.BerkeleyLatticeParser.Options;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * @author petrov
 * 
 */
public class CoarseToFineNBestLatticeParser extends
		CoarseToFineMaxRuleLatticeParser {
	LazyList[][][] chartBeforeU;
	LazyList[][][] chartAfterU;
	int k;
	List<Double> maxRuleScores;
	int tmp_k;
	
	final Options opts;
	/**
	 * @param gr
	 * @param lex
	 * @param unaryPenalty
	 * @param endL
	 * @param viterbi
	 * @param sub
	 * @param score
	 * @param accurate
	 * @param variational
	 * @param useGoldPOS
	 * @param opts 
	 */
	public CoarseToFineNBestLatticeParser(Grammar gr, Lexicon lex, int k,
			double unaryPenalty, int endL, boolean viterbi, boolean sub,
			boolean score, boolean accurate, boolean variational,
			boolean useGoldPOS, boolean initCascade, Options opts) {
		super(gr, lex, unaryPenalty, endL, viterbi, sub, score, accurate,
				variational, useGoldPOS, initCascade);
		this.opts = opts;
		this.k = k;
	}

	/**
	 * Assumes that inside and outside scores (sum version, not viterbi) have
	 * been computed. In particular, the narrowRExtent and other arrays need not
	 * be updated.
	 */
	void doConstrainedMaxCScores(LatticeInput lat, Grammar grammar,
			Lexicon lexicon, final boolean scale) {

		// lhuang: static members
		HyperEdge.tagNumberer = tagNumberer;
		HyperEdge.intokbest = false; // still gathering hyperedges to build the
										// complete hypergraph
		HyperEdge.grammar = grammar;

		numSubStatesArray = grammar.numSubStates;
		double initVal = Double.NEGATIVE_INFINITY;
		chartBeforeU = new LazyList[length][length + 1][numStates];
		// lhuang: lazylist
		chartAfterU = new LazyList[length][length + 1][numStates];

		double logNormalizer = iScore[0][length][0][0];
		// double thresh2 = threshold*logNormalizer;
		// System.out.println(Math.log(logNormalizer));

		// fill in the lexical (tag-->word) scores
		// fillTagsFromLattice(sentLat);
		// We treat Tag --> Word exactly as if it was a unary rule, except the
		// score of the rule is
		// given by the lexicon rather than the grammar and that we allow a
		// unary on top of any lexical rules.
		for (WordData wd : lat.spansData()) {
			// lhuang: original POS-tags
			int start = wd.start;
			int end = wd.end;
			int tag = wd.tagNum;
			
			// for (int tag=0; tag<numSubStatesArray.length; tag++){ //}
			if (!allowedStates[start][end][tag])
				continue;

			hasPosItem[start][end]=true;
			//System.out.println("WD:" + start + " " + end + " " + tag);
			
			chartBeforeU[start][end][tag] = new LazyList(grammar.isGrammarTag);
			int nTagStates = numSubStatesArray[tag];
			String word = wd.form;
			assert (!grammar.isGrammarTag(tag)); // if
													// (grammar.isGrammarTag(tag))
													// continue;
			if (grammar.isGrammarTag(tag)) { throw new RuntimeException("should be a pos tag!!"); }
			// System.out.println("Computing maxcScore for span " +start +
			// " to "+end);
			double[] lexiconScoreArray = lexicon.score(word, (short) tag,
					start, false, false);
			double lexiconScores = 0;
			// lhuang: substates of POS "tag"
			for (int tp = 0; tp < nTagStates; tp++) {
				double pOS = oScore[start][end][tag][tp];
				// if (pOS < thresh2) continue;
				double ruleS = lexiconScoreArray[tp];
				lexiconScores += (pOS * ruleS) / logNormalizer; // The inside
																// score of a
																// word is 0.0f
			}
			double scalingFactor = 0.0;
			if (scale)
				scalingFactor = Math.log(ScalingTools
						.calcScaleFactor(oScale[start][end][tag]
								- iScale[0][length][0]));

			lexiconScores = Math.log(lexiconScores);
			double gScore = lexiconScores + scalingFactor;

			// lhuang: terminal case, add everything
			// YG: don't add if == NEGATIVE_INFINITY
			if (gScore > Double.NEGATIVE_INFINITY && lexiconScores > Double.NEGATIVE_INFINITY) {  
				//System.out.println("@creating hyperedge:" + tagNumberer.object(tag) + " " + start*2 + " " + end*2 + " " + gScore);
				HyperEdge newElement = new HyperEdge(tag, -1, -1, 0, 0, 0, start,
						start, end, gScore, lexiconScores);
				chartBeforeU[start][end][tag].addToFringe(newElement);
				//System.out.println("chartBeforeYou1:" + start + " " + end + " " + tag + " " + tagNumberer.object(tag)); // YG SAME
			}
		}

		// lhuang:  is span-length
		for (int diff = 1; diff <= length; diff++) {
			
			// System.out.print(diff + " ");
			// lhuang: start is left-index i
			for (int start = 0; start < (length - diff + 1); start++) {
				// lhuang: end is j
				int end = start + diff;
				if (diff > 1) {
					// diff > 1: Try binary rules
					// lhuang: original non-terminals (numSubStatesArray maps
					// nonterminal to number of substates)
					for (int pState = 0; pState < numStates; pState++) {
						//if (!grammar.isGrammarTag[pState])
						//	continue; // YG: we can't match any rules on
										// pos-tags.. @@TODO: verify
						if (!allowedStates[start][end][pState])
							continue;
						// YG: this may have been initialized previously in the
						// lattice, if so, keep previous
						// TODO: verify we clear all to null for each new
						// sentence / pass!
						if (chartBeforeU[start][end][pState] == null) {
						chartBeforeU[start][end][pState] = new LazyList(
								grammar.isGrammarTag);
						}
						// lhuang: original rules for non-terminal pState
						BinaryRule[] parentRules = grammar
								.splitRulesWithP(pState);
						int nParentStates = numSubStatesArray[pState]; // ==
																		// scores[0][0].length;
						double bestScore = Double.NEGATIVE_INFINITY;
						HyperEdge bestElement = null;

						for (int i = 0; i < parentRules.length; i++) {
							BinaryRule r = parentRules[i];
							int lState = r.leftChildState;
							int rState = r.rightChildState;

							int narrowR = narrowRExtent[start][lState];
							boolean iPossibleL = (narrowR < end); // can this
																	// left
																	// constituent
																	// leave
																	// space for
																	// a right
																	// constituent?
							if (!iPossibleL) {
								continue;
							}

							int narrowL = narrowLExtent[end][rState];
							boolean iPossibleR = (narrowL >= narrowR); // can
																		// this
																		// right
																		// constituent
																		// fit
																		// next
																		// to
																		// the
																		// left
																		// constituent?
							if (!iPossibleR) {
								continue;
							}

							int min1 = narrowR;
							int min2 = wideLExtent[end][rState];
							int min = (min1 > min2 ? min1 : min2); // can this
																	// right
																	// constituent
																	// stretch
																	// far
																	// enough to
																	// reach the
																	// left
																	// constituent?
							if (min > narrowL) {
								continue;
							}

							int max1 = wideRExtent[start][lState];
							int max2 = narrowL;
							int max = (max1 < max2 ? max1 : max2); // can this
																	// left
																	// constituent
																	// stretch
																	// far
																	// enough to
																	// reach the
																	// right
																	// constituent?
							if (min > max) {
								continue;
							}

							double[][][] scores = r.getScores2();
							int nLeftChildStates = numSubStatesArray[lState]; // ==
																				// scores.length;
							int nRightChildStates = numSubStatesArray[rState]; // ==
																				// scores[0].length;
							for (int split = min; split <= max; split++) {
								double ruleScore = 0;
								if (!allowedStates[start][split][lState])
									continue;
								if (!allowedStates[split][end][rState])
									continue;

								//System.out.println("cau:" + chartAfterU[start][split][lState]);
								HyperEdge bestLeft = chartAfterU[start][split][lState]
										.getKbest(0);
								double leftChildScore = (bestLeft == null) ? Double.NEGATIVE_INFINITY
										: bestLeft.score;

								HyperEdge bestRight = chartAfterU[split][end][rState]
										.getKbest(0);
								double rightChildScore = (bestRight == null) ? Double.NEGATIVE_INFINITY
										: bestRight.score;

								// double leftChildScore =
								// maxcScore[start][split][lState];
								// double rightChildScore =
								// maxcScore[split][end][rState];
								if (leftChildScore == initVal
										|| rightChildScore == initVal)
									continue;

								double scalingFactor = 0.0;
								if (scale)
									scalingFactor = Math
											.log(ScalingTools
													.calcScaleFactor(oScale[start][end][pState]
															+ iScale[start][split][lState]
															+ iScale[split][end][rState]
															- iScale[0][length][0]));
								double gScore = leftChildScore + scalingFactor
										+ rightChildScore;

								if (gScore == Double.NEGATIVE_INFINITY)
									continue; // no chance of finding a better
												// derivation

								// lhuang: 3 nested for-loops for substates
								for (int lp = 0; lp < nLeftChildStates; lp++) {
									double lIS = iScore[start][split][lState][lp];
									if (lIS == 0)
										continue;
									// if (lIS < thresh2) continue;
									// if
									// (!allowedSubStates[start][split][lState][lp])
									// continue;

									for (int rp = 0; rp < nRightChildStates; rp++) {
										if (scores[lp][rp] == null)
											continue;
										double rIS = iScore[split][end][rState][rp];
										if (rIS == 0)
											continue;
										// if (rIS < thresh2) continue;
										// if
										// (!allowedSubStates[split][end][rState][rp])
										// continue;
										for (int np = 0; np < nParentStates; np++) {
											// if
											// (!allowedSubStates[start][end][pState][np])
											// continue;
											double pOS = oScore[start][end][pState][np];
											if (pOS == 0)
												continue;
											// if (pOS < thresh2) continue;

											double ruleS = scores[lp][rp][np];
											if (ruleS == 0)
												continue;
											// lhuang: parent-outside * rule *
											// left-inisde * right-inside
											ruleScore += (pOS * ruleS * lIS * rIS)
													/ logNormalizer;
										}
									}
								}
								if (ruleScore == 0)
									continue;

								ruleScore = Math.log(ruleScore);
								// lhuang: cumulative score bottom-up
								gScore += ruleScore;

								if (gScore > Double.NEGATIVE_INFINITY) {
									// lhuang: new combination
									HyperEdge newElement = new HyperEdge(
											pState, lState, rState, 0, 0, 0,
											start, split, end, gScore,
											ruleScore);
									//if (gScore > bestScore) {
									// YG bestElement is going to be used for the
									// "just above postags" case, so let's make sure it is really just above the postags
									
									if (opts.kbest_pruning) {
										if (gScore > bestScore && (!grammar.isGrammarTag(lState) || !grammar.isGrammarTag(rState))) {
											//if (hasPosItem[start][split] && hasPosItem[split][end]) { // verify that both places COULD be pos in principle (even if only one is in practice).  this leaves the option of a unary on top of pos.
												bestScore = gScore;
												bestElement = newElement;
											//}
										}
										// @@YG TODO what is this guy doing??
										// binary rule with diff > 2 is supposed to mean "at least one child is not pos-tag.
										// let's replace it with a rule that really says so also in the lattice case..
										//if (diff > 2)
										if (diff > 2 && (grammar.isGrammarTag(lState) || grammar.isGrammarTag(rState))) {
											//YG THIS is where I allow more options!
											// when lexicals are size-1, we don't get here with a chance of lexical item!
											// HOW TO FIX??
											// (changing || to && --> bad.)
											// How do I say "diff > 2" when diff size does not matter anymore?
											
											//if (start==6 && end==10 && pState == 20) {}
											//else if (start==10 && end==14 && pState==5) {} 
											//else {
											chartBeforeU[start][end][pState]
													.addToFringe(newElement);
											//System.out.println("chartBeforeYou:" + start + " " + end + " " + pState + " " + tagNumberer.object(pState)); 
											//}
										}
									}
									else {
										chartBeforeU[start][end][pState].addToFringe(newElement);
									}
									
								}
							}
						}
						//if (diff == 2 && bestElement != null)
						//if (bestElement != null)
						//	System.out.println("best!=null:" + tagNumberer.object(bestElement.parentState) + " "  
						//			+ tagNumberer.object(bestElement.lChildState) + " " 
						//			+ tagNumberer.object(bestElement.rChildState) + " " 
						//			+ tagNumberer.object(bestElement.childState));
					
						// @@YG TODO what is this guy doing??
						//if (diff == 2 && bestElement != null)
						// diff == 2 is suppoed to mean "both child are POS (or unary, but let's ignore the unary case..)
						// let's make it mean what it almost means
						// bestElement get it's value only when two childs are pos-tags, so if it;s not null, it's good for us.
						if (bestElement != null) // shouldn't be null if opts.weired_pruning is false
							chartBeforeU[start][end][pState]
									.addToFringe(bestElement);
						// chartBeforeU[start][end][pState].expandNextBest();
					}
				} else { // diff == 1
					/*
					 * diff==1, this is a lexical rule, and we already filled
					 * those. nothing to do here. can continue to unary rules.
					 */
				}
				// Try unary rules
				// Replacement for maxcScore[start][end], which is updated in
				// batch
				// double[] maxcScoreStartEnd = new double[numStates];
				// for (int i = 0; i < numStates; i++) {
				// maxcScoreStartEnd[i] = maxcScore[start][end][i];
				// }
				
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (!allowedStates[start][end][pState])
						continue;
					chartAfterU[start][end][pState] = new LazyList(
							grammar.isGrammarTag);

					//if (!grammar.isGrammarTag[pState]) continue; // YG: we can't match any rules on pos-tags..
									// @@TODO: verify

					int nParentStates = numSubStatesArray[pState]; // ==
																	// scores[0].length;
					UnaryRule[] unaries = grammar
							.getClosedSumUnaryRulesByParent(pState);
					HyperEdge bestElement = null;
					double bestScore = Double.NEGATIVE_INFINITY;

					for (int r = 0; r < unaries.length; r++) {
						UnaryRule ur = unaries[r];
						int cState = ur.childState;
						if ((pState == cState))
							continue;// && (np == cp))continue;
						if (iScore[start][end][cState] == null)
							continue;

						double childScore = Double.NEGATIVE_INFINITY;
						if (chartBeforeU[start][end][cState] != null) {
							HyperEdge bestChild = chartBeforeU[start][end][cState]
									.getKbest(0);
							childScore = (bestChild == null) ? Double.NEGATIVE_INFINITY
									: bestChild.score;
						}

						// double childScore = maxcScore[start][end][cState];
						if (childScore == initVal)
							continue;

						double scalingFactor = 0.0;
						if (scale)
							scalingFactor = Math.log(ScalingTools
									.calcScaleFactor(oScale[start][end][pState]
											+ iScale[start][end][cState]
											- iScale[0][length][0]));

						double gScore = scalingFactor + childScore;
						// if (gScore < maxcScoreStartEnd[pState]) continue;

						double[][] scores = ur.getScores2();
						int nChildStates = numSubStatesArray[cState]; // ==
																		// scores.length;
						double ruleScore = 0;
						for (int cp = 0; cp < nChildStates; cp++) {
							double cIS = iScore[start][end][cState][cp];
							if (cIS == 0)
								continue;
							// if (cIS < thresh2) continue;
							// if (!allowedSubStates[start][end][cState][cp])
							// continue;

							if (scores[cp] == null)
								continue;
							for (int np = 0; np < nParentStates; np++) {
								// if
								// (!allowedSubStates[start][end][pState][np])
								// continue;
								double pOS = oScore[start][end][pState][np];
								if (pOS < 0)
									continue;
								// if (pOS < thresh2) continue;

								double ruleS = scores[cp][np];
								if (ruleS == 0)
									continue;
								ruleScore += (pOS * ruleS * cIS)
										/ logNormalizer;
							}
						}
						if (ruleScore == 0)
							continue;

						ruleScore = Math.log(ruleScore);
						gScore += ruleScore;

						if (gScore > Double.NEGATIVE_INFINITY) {
							HyperEdge newElement = new HyperEdge(pState,
									cState, 0, 0, start, end, gScore, ruleScore);
							if (opts.kbest_pruning) {
								//if (gScore > bestScore) {
								if (gScore > bestScore && !grammar.isGrammarTag(cState)) {
									bestScore = gScore;
									bestElement = newElement;
								}
								// @@YG TODO what is this guy doing?
								//if (diff > 1)
								// this is adding LESS candidates for lexical rules,
								// but it will miss some lexical rules
								// in our case, and will add the alternatives for
								// them...
								// instead, let's verify that the child is not a pos-tag
								if (grammar.isGrammarTag(cState)) 
									chartAfterU[start][end][pState]
									                        .addToFringe(newElement);
							} else {
								chartAfterU[start][end][pState].addToFringe(newElement);
							}
						}
					}
					// @@YG TODO what is this guy doing?
					//if (diff == 1 && bestElement != null)
					// this is adding LESS candidates for lexical rules, but it
					// will miss some lexical rules
					// in our case, and will add the alternatives for them...
					// instead, bestElement above will be != null only for unaries above postags, 
					// so the != null check is sufficient
					if (bestElement != null)
						chartAfterU[start][end][pState]
								.addToFringe(bestElement);
				
					if (chartBeforeU[start][end][pState] != null) {
						HyperEdge bestSelf = chartBeforeU[start][end][pState]
								.getKbest(0);
						if (bestSelf != null) {
							HyperEdge selfRule = new HyperEdge(pState, pState,
									0, 0, start, end, bestSelf.score, 0);
							chartAfterU[start][end][pState]
									.addToFringe(selfRule);
						}
					}

					// chartAfterU[start][end][pState].expandNextBest();
				}
				// maxcScore[start][end] = maxcScoreStartEnd;
			}
		}
		// lhuang: transition from 1-best phase to k-best phase
		HyperEdge.intokbest = true;
	}

	/**
	 * Returns the best parse, the one with maximum expected labelled recall.
	 * Assumes that the maxc* arrays have been filled.
	 */
	public Tree<String> extractBestMaxRuleParse(int start, int end,
			LatticeInput lat) {
		// lhuang: 0, 0 means ROOT, 1st-best
		return extractBestMaxRuleParse1(start, end, 0, 0, lat);
	}

	public List<Tree<String>> extractKBestMaxRuleParses(int start, int end,
			LatticeInput lat, int k) {
		List<Tree<String>> list = new ArrayList<Tree<String>>(k);
		maxRuleScores = new ArrayList<Double>(k);
		tmp_k = 0;
		for (int i = 0; i < k; i++) {
			// lhuang: extract 1st, 2nd, 3rd, ... kth best
			Tree<String> tmp = extractBestMaxRuleParse1(start, end, 0, i, lat);
			// HyperEdge parentNode = chartAfterU[start][end][0].getKbest(i);
			// if (parentNode!=null) System.out.println(parentNode.score+" ");
			if (tmp != null)
				list.add(tmp);
			else
				break;
		}
		return list;

	}

	public double getModelScore(Tree<String> parsedTree) {
		return maxRuleScores.get(tmp_k++);
	}

	/**
	 * Returns the best parse for state "state", potentially starting with a
	 * unary rule
	 */
	// lhuang: kbest code
	public Tree<String> extractBestMaxRuleParse1(int start, int end, int state,
			int suboptimalities, LatticeInput lat) {
		// System.out.println(start+", "+end+";");

		// if (suboptimalities == 0)
		// System.out.println(start + " " + end + " " + state + " " +
		// System.out.println(start+", "+end+";");

		HyperEdge parentNode = chartAfterU[start][end][state]
				.getKbest(suboptimalities);
		if (parentNode == null) {
			System.err.println("Don't have a " + (suboptimalities + 1)
					+ "-best tree.");
			return null;
		}
		int cState = parentNode.childState;
		Tree<String> result = null;

		HyperEdge childNode = chartBeforeU[start][end][cState]
				.getKbest(parentNode.childBest);
		//if (grammar.isGrammarTag(state)) {
		//	System.out.println("childNode is:" + " " + tagNumberer.object(childNode.parentState) + " " + childNode.parentState + " " + childNode.childState + " " + childNode.lChildState + " " + childNode.rChildState);
		//}
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		String stateStr = (String) tagNumberer.object(cState);// +""+start+""+end;
		if (stateStr.endsWith("^g"))
			stateStr = stateStr.substring(0, stateStr.length() - 2);

		// boolean posLevel = (end - start == 1);
		boolean posLevel = !grammar.isGrammarTag(childNode.parentState); // @YG if not grammar
		// tag, it's a POS.
		if (posLevel) {
			children.add(new Tree<String>(lat.getWord(start, end, childNode.parentState)));
		} else {
			int split = childNode.split;
			if (split == -1) {
				System.err
						.println("Warning: no symbol can generate the span from "
								+ start + " to " + end + ".");
				System.err.println("The score is "
						+ maxcScore[start][end][state]
						+ " and the state is supposed to be " + stateStr);
				System.err.println("The insideScores are "
						+ Arrays.toString(iScore[start][end][state])
						+ " and the outsideScores are "
						+ Arrays.toString(oScore[start][end][state]));
				System.err.println("The maxcScore is "
						+ maxcScore[start][end][state]);
				// return extractBestMaxRuleParse2(start, end,
				// maxcChild[start][end][state], sentence);
				return new Tree<String>("ROOT");
			}
			int lState = childNode.lChildState;
			int rState = childNode.rChildState;
			// lhuang: combination of
			Tree<String> leftChildTree = extractBestMaxRuleParse1(start, split,
					lState, childNode.lChildBest, lat);
			Tree<String> rightChildTree = extractBestMaxRuleParse1(split, end,
					rState, childNode.rChildBest, lat);
			children.add(leftChildTree);
			children.add(rightChildTree);
		}

		boolean scale = false;
		// lhuang: updates lBest, rBest
		updateConstrainedMaxCScores(lat, scale, childNode);

		result = new Tree<String>(stateStr, children);
		if (cState != state) { // unaryRule
			stateStr = (String) tagNumberer.object(state);// +""+start+""+end;
			if (stateStr.endsWith("^g"))
				stateStr = stateStr.substring(0, stateStr.length() - 2);

			int intermediateNode = grammar.getUnaryIntermediate((short) state,
					(short) cState);
			if (intermediateNode > 0) {
				List<Tree<String>> restoredChild = new ArrayList<Tree<String>>();
				String stateStr2 = (String) tagNumberer
						.object(intermediateNode);
				if (stateStr2.endsWith("^g"))
					stateStr2 = stateStr2.substring(0, stateStr2.length() - 2);
				restoredChild.add(result);
				result = new Tree<String>(stateStr2, restoredChild);
			}
			List<Tree<String>> childs = new ArrayList<Tree<String>>();
			childs.add(result);
			result = new Tree<String>(stateStr, childs);
		}
		updateConstrainedMaxCScores(lat, scale, parentNode);

		return result;
	}

	void updateConstrainedMaxCScores(LatticeInput lat, final boolean scale,
			HyperEdge parent) {

		int start = parent.start;
		int end = parent.end;
		int pState = parent.parentState;
		int suboptimalities = parent.parentBest + 1;
		double ruleScore = parent.ruleScore;

		if (parent.alreadyExpanded)
			return;

		if (!parent.isUnary) {
			// if (chartBeforeU[start][end][pState].sortedListSize() >=
			// suboptimalities) return; // already have enough derivations

			int lState = parent.lChildState;
			int rState = parent.rChildState;
			int split = parent.split;

			HyperEdge newParentL = null, newParentR = null;
			// @@ YG TODO what is this guy doing? [fixed?]
			// if (split-start>1) { // left is not a POS //}
			if (lState > -1 && grammar.isGrammarTag[lState]) { // left is not a POS, lState>-1 should be sufficient..
				int lBest = parent.lChildBest + 1;
				HyperEdge lChild = chartAfterU[start][split][lState]
						.getKbest(lBest);
				if (lChild != null) {
					int rBest = parent.rChildBest;
					HyperEdge rChild = chartAfterU[split][end][rState]
							.getKbest(rBest);
					double newScore = lChild.score + rChild.score + ruleScore;
					newParentL = new HyperEdge(pState, lState, rState,
							suboptimalities, lBest, rBest, start, split, end,
							newScore, ruleScore);
					// chartBeforeU[start][end][pState].addToFringe(newParentL);
				}
			}
			// @@ YG TODO what is this guy doing? [fixed?]
			// if (end-split>1){ // right is not a POS //}
			if (rState > -1 && grammar.isGrammarTag[rState]) { // right is not POS, rState>-1 should be sufficient..
				int rBest = parent.rChildBest + 1;
				HyperEdge rChild = chartAfterU[split][end][rState]
						.getKbest(rBest);
				if (rChild != null) {
					int lBest = parent.lChildBest;
					HyperEdge lChild = chartAfterU[start][split][lState]
							.getKbest(lBest);
					double newScore = lChild.score + rChild.score + ruleScore;
					newParentR = new HyperEdge(pState, lState, rState,
							suboptimalities, lBest, rBest, start, split, end,
							newScore, ruleScore);
					// chartBeforeU[start][end][pState].addToFringe(newParentR);
				}
			}

			if (newParentL != null && newParentR != null
					&& newParentL.score > newParentR.score)
				chartBeforeU[start][end][pState].addToFringe(newParentL);
			else if (newParentL != null && newParentR != null)
				chartBeforeU[start][end][pState].addToFringe(newParentR);
			else if (newParentL != null || newParentR != null) {
				if (newParentL != null)
					chartBeforeU[start][end][pState].addToFringe(newParentL);
				else
					/* newParentR!=null */chartBeforeU[start][end][pState]
							.addToFringe(newParentR);
			}
			parent.alreadyExpanded = true;

			// chartBeforeU[start][end][pState].expandNextBest();
		} else { // unary
			// if (chartAfterU[start][end][pState].sortedListSize() >=
			// suboptimalities) return; // already have enough derivations

			int cState = parent.childState;
			int cBest = parent.childBest + 1;

			// @@ YG TODO what is this guy doing? [fixed?]
			// if (end-start>1){ is it not a pos? //}
			if (grammar.isGrammarTag[pState]) { //YG should it be cState?
				HyperEdge child = chartBeforeU[start][end][cState]
						.getKbest(cBest);
				if (child != null) {
					double newScore = child.score + ruleScore;
					HyperEdge newParent = new HyperEdge(pState, cState,
							suboptimalities, cBest, start, end, newScore,
							ruleScore);
					// if (newScore>=parent.score)
					// System.out.println("ullala");
					chartAfterU[start][end][pState].addToFringe(newParent);
				}
				parent.alreadyExpanded = true;
				// chartAfterU[start][end][pState].expandNextBest();
			}
		}
	}

	public List<Tree<String>> getKBestConstrainedParses(LatticeInput lat, int k) {
		if (lat.length() == 0) {
			ArrayList<Tree<String>> result = new ArrayList<Tree<String>>();
			result.add(new Tree<String>("ROOT"));
			return result;
		}
		doPreParses(lat, null, false);
		List<Tree<String>> bestTrees = null;
		double score = 0;
		// bestTree = extractBestViterbiParse(0, 0, 0, length, sentence);
		// score = viScore[0][length][0];
		if (true) {// score != Double.NEGATIVE_INFINITY) {
			// score = Math.log(score) + (100*iScale[0][length][0]);
			// System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");

			// voScore[0][length][0] = 0.0;
			// doConstrainedViterbiOutsideScores(baseGrammar);

			// pruneChart(pruningThreshold, baseGrammar.numSubStates,
			// grammar.numSubStates, true);
			Grammar curGrammar = grammarCascade[endLevel - startLevel + 1];
			Lexicon curLexicon = lexiconCascade[endLevel - startLevel + 1];
			// numSubStatesArray = grammar.numSubStates;
			// clearArrays();
			double initVal = (viterbiParse) ? Double.NEGATIVE_INFINITY : 0;
			int level = isBaseline ? 1 : endLevel;
			createArrays(lat, false, curGrammar.numStates,
					curGrammar.numSubStates, level, initVal, false);
			initializeChartFromLattice(lat, curLexicon, false, false, false);
			doConstrainedInsideScores(curGrammar, viterbiParse, viterbiParse);

			score = iScore[0][length][0][0];
			if (!viterbiParse)
				score = Math.log(score);// + (100*iScale[0][length][0]);
			logLikelihood = score;
			if (score != Double.NEGATIVE_INFINITY) {
				// System.out.println("\nFinally found a parse for sentence with length "+length+". The LL is "+score+".");

				if (!viterbiParse) {
					oScore[0][length][0][0] = 1.0;
					doConstrainedOutsideScores(curGrammar, viterbiParse, false);
					doConstrainedMaxCScores(lat, curGrammar, curLexicon, false);

				}

				// Tree<String> withoutRoot = extractBestMaxRuleParse(0, length,
				// sentence);
				// add the root
				// ArrayList<Tree<String>> rootChild = new
				// ArrayList<Tree<String>>();
				// rootChild.add(withoutRoot);
				// bestTree = new Tree<String>("ROOT",rootChild);

				// System.out.print(bestTree);
			} else {
				// System.out.println("Using scaling code for sentence with length "+length+".");
				setupScaling();
				initializeChartFromLattice(lat, curLexicon, false, false, true);
				doScaledConstrainedInsideScores(curGrammar);
				score = iScore[0][length][0][0];
				if (!viterbiParse)
					score = Math.log(score) + (100 * iScale[0][length][0]);
				// System.out.println("Finally found a parse for sentence with length "+length+". The LL is "+score+".");
				// System.out.println("Scale: "+iScale[0][length][0]);
				oScore[0][length][0][0] = 1.0;
				oScale[0][length][0] = 0;
				doScaledConstrainedOutsideScores(curGrammar);
				doConstrainedMaxCScores(lat, curGrammar, curLexicon, true);
			}

			grammar = curGrammar;
			lexicon = curLexicon;
			bestTrees = extractKBestMaxRuleParses(0, length, lat, k);
		}
		return bestTrees;
	}

	public CoarseToFineNBestLatticeParser newInstance() {
		CoarseToFineNBestLatticeParser newParser = new CoarseToFineNBestLatticeParser(
				grammar, lexicon, k, unaryPenalty, endLevel, viterbiParse,
				outputSub, outputScore, accurate, this.doVariational,
				useGoldPOS, false, this.opts);
		newParser.initCascade(this);
		return newParser;
	}

	public synchronized Object call() {
		List<Tree<String>> result = getKBestConstrainedParses(nextSentence,
				null, k);
		nextSentence = null;
		synchronized (queue) {
			queue.add(result, -nextSentenceID);
			queue.notifyAll();
		}
		return null;
	}

}
