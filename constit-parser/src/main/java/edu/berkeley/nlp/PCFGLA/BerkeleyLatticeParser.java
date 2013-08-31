package edu.berkeley.nlp.PCFGLA;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Iterator;

import org.python.modules.synchronize;

import yg.blatt.BerkeleyTypesLexicon;
import yg.blatt.LatticeInput;
import yg.blatt.LatticeInputsFromProcessedFile;
import yg.blatt.TypesLexicon;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;

//lhuang

/**
 * Reads in the Penn Treebank and generates N_GRAMMARS different grammars.
 * 
 * @author Slav Petrov
 */
public class BerkeleyLatticeParser {
	private CoarseToFineMaxRuleLatticeParser parser;
	private boolean accurate;
	private static final int maxLength = 200;
	private ParserData pData;

	public static void main(String... args) throws Exception {
		BerkeleyLatticeParser blp = new BerkeleyLatticeParser();
		blp.parse("לאחר שצלצל ושינה ושילש , באה השוערת .");
	}

	// private MultiThreadedParserWrapper parser;
	private TypesLexicon typesLexicon = null;

	public synchronized Tree<String> parse(String input) throws IOException {
		// double threshold = 1.0;
		// CoarseToFineMaxRuleLatticeParser parser = new
		// CoarseToFineMaxRuleLatticeParser(pData.getGrammar(),
		// (ExtLexicon) pData.getLexicon(), threshold, -1,
		// /* viterbi */false, false, false, accurate,
		// /* variational */false, false, true);

		// can support K options as well:
		// parser = new CoarseToFineNBestLatticeParser(grammar, lexicon,
		// opts.kbest, threshold, -1, opts.viterbi,
		// opts.substates, opts.scores, opts.accurate, opts.variational, false,
		// true, opts);

		LatticeInput sent = null; // every input is a lattice. The only
		// difference is how it gets built.
		Iterator<LatticeInput> sentences = null;

		BufferedReader inputData = new BufferedReader(new StringReader(input));

		PrintWriter outputData = new PrintWriter(new OutputStreamWriter(System.out));

		sentences = new LatticeInputsFromProcessedFile(inputData, typesLexicon).iterator();

		while (sentences.hasNext()) {
			sent = sentences.next();

			// @YG NOTE: length for lattice is number of states, not number
			// of words!
			if (sent.length() > maxLength) {
				outputData.write("(())\n");
				// if (opts.kbest > 1) {
				// outputData.write("\n");
				// }
				System.err.println("Skipping sentence with " + sent.length() + " words/states since it is too long.");
				continue;
			}

			Tree<String> parsedTree = parser.getBestConstrainedParse(sent, null);
			return parsedTree;

		}
		return null;

	}

	public BerkeleyLatticeParser() {
		this(BerkeleyLatticeParser.class.getResourceAsStream("/heblex2_5_smoothing.gr"), false);
	}

	public BerkeleyLatticeParser(File grammar, boolean accurate) throws FileNotFoundException, IOException {
		try (FileInputStream fis = new FileInputStream(grammar)) {
			init(fis, accurate);
		}
	}

	private void init(InputStream grammarInputStream, boolean accurate) {
		this.accurate = accurate;
		pData = ParserData.load(grammarInputStream);
		Grammar grammar = pData.getGrammar();
		ExtLexicon lexicon = (ExtLexicon) pData.getLexicon();
		typesLexicon = new BerkeleyTypesLexicon((ExtLexicon) pData.getLexicon());
		Numberer.setNumberers(pData.getNumbs());
		double threshold = 1.0;
		parser = new CoarseToFineMaxRuleLatticeParser(pData.getGrammar(), (ExtLexicon) pData.getLexicon(), threshold,
				-1,
				/* viterbi */false, false, false, accurate,
				/* variational */false, false, true);
		parser.binarization = pData.getBinarization();

	}

	public BerkeleyLatticeParser(InputStream grammarInputStream, boolean accurate) {
		init(grammarInputStream, accurate);
	}

	public static class Options {

		@Option(name = "-gr", required = false, usage = "Grammarfile (Required)\n")
		public String grFileName;

		@Option(name = "-tokenize", usage = "[Ignored] Tokenize input first. (Default: false=text is already tokenized)")
		public boolean tokenize = false;

		@Option(name = "-viterbi", usage = "Compute viterbi derivation instead of max-rule tree (Default: max-rule)")
		public boolean viterbi;

		@Option(name = "-binarize", usage = "Output binarized trees. (Default: false)")
		public boolean binarize;

		@Option(name = "-scores", usage = "Output inside scores (only for binarized viterbi trees). (Default: false)")
		public boolean scores;

		@Option(name = "-substates", usage = "Output subcategories (only for binarized viterbi trees). (Default: false)")
		public boolean substates;

		@Option(name = "-accurate", usage = "Set thresholds for accuracy. (Default: set thresholds for efficiency)")
		public boolean accurate;

		@Option(name = "-modelScore", usage = "Output effective model score (max rule score for max rule parser) (Default: false)")
		public boolean modelScore;

		@Option(name = "-confidence", usage = "Output confidence measure, i.e. likelihood of tree given words: P(T|w) (Default: false)")
		public boolean confidence;

		@Option(name = "-sentence_likelihood", usage = "Output sentence likelihood, i.e. summing out all parse trees: P(w) (Default: false)")
		public boolean sentence_likelihood;

		@Option(name = "-tree_likelihood", usage = "Output joint likelihood of tree and words: P(t,w) (Default: false)")
		public boolean tree_likelihood;

		@Option(name = "-variational", usage = "Use variational rule score approximation instead of max-rule (Default: false)")
		public boolean variational;

		@Option(name = "-render", usage = "Write rendered tree to image file. (Default: false)")
		public boolean render;

		@Option(name = "-chinese", usage = "Enable some Chinese specific features in the lexicon.")
		public boolean chinese;

		@Option(name = "-inputFile", usage = "Read input from this file instead of reading it from STDIN.")
		public String inputFile;

		@Option(name = "-maxLength", usage = "Maximum sentence length (Default = 200).")
		public int maxLength = 200;

		@Option(name = "-nThreads", usage = "[not supported] Parse in parallel using n threads (Default: 1).")
		public int nThreads = 1;

		@Option(name = "-kbest", usage = "Output the k best parse max-rule trees (Default: 1).")
		public int kbest = 1;

		@Option(name = "-outputFile", usage = "Store output in this file instead of printing it to STDOUT.")
		public String outputFile;

		@Option(name = "-useGoldPOS", usage = "[not supported/irellevant] Read data in CoNLL format, including gold part of speech tags.")
		public boolean goldPOS;

		@Option(name = "-dumpPosteriors", usage = "Dump max-rule posteriors to disk.")
		public boolean dumpPosteriors;

		@Option(name = "-ec_format", usage = "Use Eugene Charniak's input and output format.")
		public boolean ec_format;

		@Option(name = "-nGrammars", usage = "[not supported] Use a product model based on that many grammars")
		public int nGrammars = 1;

		// lhuang
		/*
		 * @Option(name = "-forest", usage = "Output packed forest (lhuang).")
		 * public boolean forest = false;
		 */

		// yg
		@Option(name = "-lattice", usage = "Use lattice as input (yoavg).")
		public boolean lattice = true;

		@Option(name = "-kbest-pruning", usage = "(almost) compatability with how the non-lattice parser prunes k-best lists.")
		public boolean kbest_pruning = false;
	}
}
