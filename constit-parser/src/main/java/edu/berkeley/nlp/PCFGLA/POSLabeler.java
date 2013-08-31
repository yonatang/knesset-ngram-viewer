/**
 * @@YG 
 *    POS-Labeler: given treebank and grammar, output the split-POS posteriors for every word location in the treebank.
 *       based on TreeLabeler.java.
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;
import edu.berkeley.nlp.syntax.Trees.PennTreeRenderer;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class POSLabeler {

	public static class Options {

		@Option(name = "-gr", usage = "Input File for Grammar (Required)\n")
		public String inFileName;

		@Option(name = "-labelLevel", usage = "Parse with projected grammar from this level (yielding 2^level substates) (Default: -1 = input grammar)")
		public int labelLevel = -1;

		@Option(name = "-scores", usage = "Output inside scores. (Default: false)")
		public boolean scores;

		@Option(name = "-getYield", usage = "Get the sentences only")
		public boolean getyield;

		@Option(name = "-labelOnlyPOS", usage = "Labels only the POS categories")
		public boolean labelOnlyPOS;

		@Option(name = "-onlyConfidence", usage = "Output only confidence measure, i.e. tree likelihood: P(T|w) (Default: false)")
		public boolean onlyConfidence;

		@Option(name = "-maxLength", usage = "Remove sentences that are longer than this (doesn't print an empty line)")
		public int maxLength = 1000;

		@Option(name = "-inputFile", usage = "Read input from this file instead of reading it from STDIN.")
		public String inputFile;

		@Option(name = "-outputFile", usage = "Store output in this file instead of printing it to STDOUT.")
		public String outputFile;

		@Option(name = "-prettyPrint", usage = "Print in human readable form rather than one tree per line")
		public boolean prettyPrint;

		@Option(name = "-getPOSandYield", usage = "Get POS and words in CoNLL format")
		public boolean getPOSandYield;

		@Option(name = "-annotateTrees", usage = "Binarize and annotate trees")
		public boolean annotateTrees;

		@Option(name = "-horizontalMarkovization", usage = "Level of horizontal Markovization (Default: 0, i.e. no sibling information)")
		public int h_markov = 0;

		@Option(name = "-verticalMarkovization", usage = "Level of vertical Markovization (Default: 1, i.e. no parent information)")
		public int v_markov = 1;

		@Option(name = "-b", usage = "LEFT/RIGHT Binarization (Default: RIGHT)")
		public Binarization binarization = Binarization.RIGHT;

	}

	/**
	 * @param grammar
	 * @param lexicon
	 * @param labelLevel
	 */
	Grammar grammar;
	SophisticatedLexicon lexicon;
	ArrayParser2 labeler;
	CoarseToFineMaxRuleParser parser;
	Numberer tagNumberer;
	Binarization binarization;

	public POSLabeler(Grammar grammar, SophisticatedLexicon lexicon, int labelLevel, Binarization bin) {
		if (labelLevel == -1) {
			this.grammar = grammar.copyGrammar(false);
			this.lexicon = lexicon.copyLexicon();
		} else { // need to project
			int[][] fromMapping = grammar.computeMapping(1);
			int[][] toSubstateMapping = grammar
					.computeSubstateMapping(labelLevel);
			int[][] toMapping = grammar.computeToMapping(labelLevel,
					toSubstateMapping);
			double[] condProbs = grammar.computeConditionalProbabilities(
					fromMapping, toMapping);

			this.grammar = grammar.projectGrammar(condProbs, fromMapping,
					toSubstateMapping);
			this.lexicon = lexicon.projectLexicon(condProbs, fromMapping,
					toSubstateMapping);
			this.grammar.splitRules();
			double filter = 1.0e-10;
			this.grammar.removeUnlikelyRules(filter, 1.0);
			this.lexicon.removeUnlikelyTags(filter, 1.0);
		}
		//this.grammar.logarithmMode();
		//this.lexicon.logarithmMode();
		this.labeler = new ArrayParser2(this.grammar, this.lexicon);
		this.parser = new CoarseToFineMaxRuleParser(grammar, lexicon, 1, -1,
				true, false, false, false, false, false, true);
		this.tagNumberer = Numberer.getGlobalNumberer("tags");
		this.binarization = bin;
	}

	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);
		// provide feedback on command-line arguments
		System.err.println("Calling with " + optParser.getPassedInOptions());

		String inFileName = opts.inFileName;
		Grammar grammar = null;
		SophisticatedLexicon lexicon = null;
		POSLabeler treeLabeler = null;
		ParserData pData = null;
		short[] numSubstates = null;
		if (inFileName == null) {
			System.err.println("Did not provide a grammar.");
         System.exit(1);
      }

      System.err.println("Loading grammar from " + inFileName + ".");

      pData = ParserData.Load(inFileName);
      if (pData == null) {
         System.out.println("Failed to load grammar from file" + inFileName + ".");
         System.exit(1);
      }

      grammar = pData.getGrammar();
      grammar.splitRules();
      lexicon = (SophisticatedLexicon) pData.getLexicon();

      Numberer.setNumberers(pData.getNumbs());

      int labelLevel = opts.labelLevel;
      treeLabeler = new POSLabeler(grammar, lexicon, labelLevel, pData.bin);
      numSubstates = treeLabeler.grammar.numSubStates;

		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");

		Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
		try {
			InputStreamReader inputData = (opts.inputFile == null) ? 
                  new InputStreamReader(System.in)
					 : new InputStreamReader(new FileInputStream(opts.inputFile), "UTF-8");
			PennTreeReader treeReader = new PennTreeReader(inputData);
			PrintWriter outputData = (opts.outputFile == null) ? 
                  new PrintWriter(new OutputStreamWriter(System.out))
					 : new PrintWriter(new OutputStreamWriter( new FileOutputStream(opts.outputFile), "UTF-8"), true);

			Tree<String> tree = null;
			while (treeReader.hasNext()) {
            System.out.println("Tree");
				tree = treeReader.next();
				if (tree.getChildren().size() == 0 || tree.getChildren().get(0).getLabel().equals("(") || tree.getYield().get(0).equals("")) { 
               // empty tree -> parse failure
					outputData.write("(())\n");
					continue;
				}
				if (tree.getYield().size() > opts.maxLength) continue;

				tree = TreeAnnotations.processTree(tree, pData.v_markov, pData.h_markov, pData.bin, false);
				List<String> sentence = tree.getYield();
				Tree<StateSet> stateSetTree = StateSetTreeList.stringTreeToStatesetTree(tree, numSubstates, false, tagNumberer);
				allocate(stateSetTree);
            treeLabeler.labeler.doInsideOutsideScores(stateSetTree, true, false);

            List<StateSet> preterms = stateSetTree.getPreTerminalYield();
            double sentenceProb = stateSetTree.getLabel().getIScore(0);
            for (int i=0;i<sentence.size();i++) {
            //for (StateSet ss : stateSetTree.getPreTerminalYield()) {
               StateSet ss = preterms.get(i);
               String   w  = sentence.get(i);
               StateSet[] tags = treeLabeler.labeler.preterminalStates.get(ss);
               double sum=0;
               System.out.print(w + "_" + ss + " ");
               for (StateSet tag : tags) {
                  if (tag==null) { continue; }
                  double tsum=0;
                  //System.out.print("\n\t" + tag);
                  for (int sub=0; sub<tag.numSubStates();sub++) {
                     //double posterior = ss.getIScore(sub) * ss.getOScore(sub) / sentenceProb;
                     double posterior = tag.getIScore(sub) * tag.getOScore(sub) / sentenceProb;
                     if (tag.getIScore(sub)>0) {
                        //System.out.println("iscore:" + tag +":" + sub + ":" + tag.getIScore(sub));
                     }
                     if (tag.getOScore(sub)>0) {
                        //System.out.println("oscore:" + tag +":" + sub + ":" + tag.getOScore(sub));
                     }
                     if (posterior>0) {
                        //System.out.print(sub + ":" + posterior + " ");// + ":" + ss.getOScore(sub)+":"+ss.getIScore(sub));
                     }
                     sum+=posterior;
                     tsum+=posterior;
                  }
                  //if (tsum>0.001)
                  if (tsum>0.2 && ss.getState()!=tag.getState())
                     System.out.print(" " + tsum + ":" + tag);
               }
               System.out.println();
               //System.out.print("\n\t\t" + sum);
               if (sum < 0.9) { System.out.println(" @@ " + w + " " + sum);}
            }
            System.out.println();
            if (treeLabeler!=null) continue;



				Tree<String> labeledTree = treeLabeler.label(stateSetTree, sentence, opts.scores, opts.labelOnlyPOS);
				/*if (opts.onlyConfidence) {
					double treeLL = stateSetTree.getLabel().getIScore(0);
					outputData.write(treeLL + "\n");
					outputData.flush();
					continue;
				}*/

				if (labeledTree != null && labeledTree.getChildren().size() > 0) {
						if (opts.labelOnlyPOS) { labeledTree = TreeAnnotations .debinarizeTree(labeledTree);
						}
						outputData.write("( " + labeledTree.getChildren().get(0) + ")\n");
				} else {
					if (opts.labelOnlyPOS) {
						List<Tree<String>> pos = tree.getPreTerminals();
						tree = TreeAnnotations.unAnnotateTree(tree);
						for (Tree<String> tag : pos) {
							String t = tag.getLabel();
							t = t + "-0";
							tag.setLabel(t);
						}
						outputData.write("( " + tree.getChildren().get(0)
								+ ")\n");
					} else {
						outputData.write("(())\n");
					}
				}
				outputData.flush();
			}
			outputData.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		System.exit(0);
	}

	/**
	 * @param stateSetTree
	 * @return
	 */
	private Tree<String> label(Tree<StateSet> stateSetTree,
			List<String> sentence, boolean outputScores, boolean labelOnlyPOS) {
		Tree<String> tree = labeler.getBestViterbiDerivation(stateSetTree, outputScores, labelOnlyPOS);
		// if (tree==null){ // max-rule tree had no viterbi derivation
		// tree = parser.getBestConstrainedParse(sentence, null);
		// tree = TreeAnnotations.processTree(tree,1, 0, binarization,false);
		// // System.out.println(tree);
		// stateSetTree = StateSetTreeList.stringTreeToStatesetTree(tree,
		// this.grammar.numSubStates, false, tagNumberer);
		// allocate(stateSetTree);
		// tree = labeler.getBestViterbiDerivation(stateSetTree,outputScores);
		// }
		return tree;
	}

	/*
	 * Allocate the inside and outside score arrays for the whole tree
	 */
	static void allocate(Tree<StateSet> tree) {
		tree.getLabel().allocate();
		for (Tree<StateSet> child : tree.getChildren()) {
			allocate(child);
		}
	}

}
