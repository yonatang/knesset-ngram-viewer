package il.ac.idc.nlp.constit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.python.modules.synchronize;

import edu.berkeley.nlp.PCFGLA.BerkeleyLatticeParser;
import edu.berkeley.nlp.PCFGLA.TreeAnnotations;
import edu.berkeley.nlp.syntax.Tree;

public class ConstitTreeMaker {
	private BerkeleyLatticeParser blp;
	private LatticeMaker ml;

	/**
	 * Using default grammar file and faster decoding
	 * 
	 * @param wordCount
	 *            (i.e. full_nodef.twcount)
	 * @param lemLex
	 *            (i.e. lexicon.utf8.sqlite)
	 * @throws IOException
	 */
	public ConstitTreeMaker(InputStream wordCount, InputStream lemLex) throws IOException {
		blp = new BerkeleyLatticeParser();
		ml = new LatticeMaker(wordCount, lemLex);
	}

	/**
	 * Using faster decoding
	 * 
	 * @param wordCount
	 *            (i.e. full_nodef.twcount)
	 * @param lemLex
	 *            (i.e. lexicon.utf8.sqlite)
	 * @param grammar
	 *            (i.e. heblex2_5_smoothing.gr)
	 * @throws IOException
	 */
	public ConstitTreeMaker(InputStream wordCount, InputStream lemLex, InputStream grammar) throws IOException {
		blp = new BerkeleyLatticeParser(grammar, false);
		ml = new LatticeMaker(wordCount, lemLex);
	}

	/**
	 * 
	 * @param wordCount
	 *            (i.e. full_nodef.twcount)
	 * @param lemLex
	 *            (i.e. lexicon.utf8.sqlite)
	 * @param grammar
	 *            (i.e. heblex2_5_smoothing.gr)
	 * @param accurate
	 *            if true, perform a slower but more accurate decoding
	 * @throws IOException
	 */
	public ConstitTreeMaker(InputStream wordCount, InputStream lemLex, InputStream grammar, boolean accurate)
			throws IOException {
		blp = new BerkeleyLatticeParser(grammar, accurate);
		ml = new LatticeMaker(wordCount, lemLex);
	}

	/**
	 * Using default grammar file and faster decoding
	 * 
	 * @param wordCount
	 *            (i.e. full_nodef.twcount)
	 * @param lemlex
	 *            (i.e. lexicon.utf8.sqlite)
	 * @throws IOException
	 */
	public ConstitTreeMaker(File wordCount, File lemlex) throws IOException {
		blp = new BerkeleyLatticeParser();
		ml = new LatticeMaker(wordCount, lemlex);
	}

	/**
	 * Using faster decoding
	 * 
	 * @param wordCount
	 *            (i.e. full_nodef.twcount)
	 * @param lemlex
	 *            (i.e. lexicon.utf8.sqlite)
	 * @param grammar
	 *            (i.e. heblex2_5_smoothing.gr)
	 * @throws IOException
	 */
	public ConstitTreeMaker(File wordCount, File lemlex, File grammar) throws IOException {
		blp = new BerkeleyLatticeParser(grammar, false);
		ml = new LatticeMaker(wordCount, lemlex);
	}

	/**
	 * 
	 * @param wordCount
	 *            (i.e. full_nodef.twcount)
	 * @param lemlex
	 *            (i.e. lexicon.utf8.sqlite)
	 * @param grammar
	 *            (i.e. heblex2_5_smoothing.gr)
	 * @param accurate
	 *            if true, perform a slower but more accurate decoding
	 * @throws IOException
	 */
	public ConstitTreeMaker(File wordCount, File lemlex, File grammar, boolean accurate) throws IOException {
		blp = new BerkeleyLatticeParser(grammar, accurate);
		ml = new LatticeMaker(wordCount, lemlex);
	}

	private static final String WORD_REGEX="([א-ת0-9,.\"a-zA-Z()\\[\\[]+)";
	private static final Pattern DASH_REMOVER=Pattern.compile(WORD_REGEX+"-"+WORD_REGEX);
	protected String preprocess(String sentence) {
		String[] parts = StringUtils.split(sentence);
		List<String> processed = new ArrayList<>();
		for (String part : parts) {
			part=StringUtils.replace(part, "–", "-"); //standard dash
			part=DASH_REMOVER.matcher(part).replaceAll("$1 $2"); // transform AI-AMWN -> AI AMWN
			while (StringUtils.contains(part, "  ")){ //remove double spaces
				part=StringUtils.replace(part,"  "," ");
			}
			if (StringUtils.countMatches(part, "\"") > 1) {
				if (StringUtils.countMatches(part, "-") > 1) {
					String[] dashes = StringUtils.split(part, "-");
					processed.add(preprocess(StringUtils.join(dashes, " - ")));
				} else {
					processed.add(StringUtils.replaceChars(part, '"', '\''));
				}
			} else {
				processed.add(part);
			}
		}
		return StringUtils.join(processed, " ");
	}

	/**
	 * Create a constitute tree out of UTF8 hebrew sentence.
	 * 
	 * @param sentence
	 * @return
	 * @throws IOException
	 */
	public Tree<String> parse(String sentence) throws IOException {
		sentence = preprocess(sentence);
		String mid = ml.parse_sent(sentence, false);
		return TreeAnnotations.unAnnotateTree(blp.parse(mid));
	}

	/**
	 * Clean the tree from the @!@ annotations
	 * 
	 * @param tree
	 */
	public static void cleanTree(Tree<String> tree) {
		tree.setLabel(StringUtils.substringBefore(tree.getLabel(), "@!@"));
		for (Tree<String> subtree : tree.getChildren()) {
			cleanTree(subtree);
		}
	}

	public static void main(String... args) throws Exception {
		//Weird - the sentence "אני לא יודע איך לתרגם." is causing exception
		long now = System.currentTimeMillis();
		File wc = new File(ConstitTreeMaker.class.getResource("/full_nodef.twcount").getFile());
		File lex = new File(ConstitTreeMaker.class.getResource("/lexicon.utf8.sqlite").getFile());
		final ConstitTreeMaker ctm = new ConstitTreeMaker(wc, lex);
		System.out.println(String.format("init Took me %.2fs", (System.currentTimeMillis() - now) / 1000d));
		String[] inputs = {
				"\u05d4\u05d9\u05d5\"\u05e8 \u05d9\u05d5\u05dc\u05d9 \u05d9\u05d5\u05d0\u05dc \u05d0\u05d3\u05dc\u05e9\u05d8\u05d9\u05d9\u05df",
				"הצעות סיעות העבודה, יהדות התורה - מרצ, ש\"ס ורע\"ם-תע\"ל-מד\"ע-חד\"ש-בל\"ד להביע אי-אמון בממשלה בשל, חיפש את האתונות.",
				"היו\"ר יולי יואל אדלשטיין", "היו'ר יולי יואל אדלשטיין", "גנן גידל דגן בגן", "דגן גדול גדל בגן",
				"ישבתי בגן כלשהו", "ועדת החוץ והביטחון התכנסה אתמול בערב.", "מזכירת הכנסת ירדנה מלר-הורוביץ",
				"סגן שר האוצר מיקי לוי", "מנהל בית הספר ע\"ש יצחק רבין עופר שלח",
				"סגן שר המלחמה כרמל שאמה הכהן, חיפש את האתונות.",
				"הצעות סיעות העבודה, יהדות התורה - מרצ, ש\"ס ורע\"ם - תע\"ל - מד\"ע - חד\"ש - בל\"ד להביע אי-אמון בממשלה בשל" };
		String[] outputs = {
				"(INTJ (NP (NP (H \u05D4) (NN \u05D9\u05D5\"\u05E8)) (NP (NNPG (NNP \u05D9\u05D5\u05DC\u05D9) (NNPG (NNP \u05D9\u05D5\u05D0\u05DC) (NNP \u05D0\u05D3\u05DC\u05E9\u05D8\u05D9\u05D9\u05DF))))))",
				"(S (NPSBJ (NP (NNT \u05D4\u05E6\u05E2\u05D5\u05EA) (NP (NNT \u05E1\u05D9\u05E2\u05D5\u05EA) (NP (H \u05D4) (NN \u05E2\u05D1\u05D5\u05D3\u05D4)))) (yyCM ,) (NP (NP (NNT \u05D9\u05D4\u05D3\u05D5\u05EA) (NP (H \u05D4) (NX (NN \u05EA\u05D5\u05E8\u05D4) (yyDASH -) (NN \u05DE\u05E8\u05E6)))) (yyCM ,) (NP (NNPG (NNP \u05E9\"\u05E1) (NNPG (NNP \u05D5\u05E8\u05E2\"\u05DD) (yyDASH -) (NNP \u05EA\u05E2\"\u05DC) (yyDASH -) (NNP \u05DE\u05D3\"\u05E2) (yyDASH -) (NNP \u05D7\u05D3\"\u05E9) (yyDASH -) (NNP \u05D1\u05DC\"\u05D3))))) (VP (VBINF \u05DC\u05D4\u05D1\u05D9\u05E2) (NP (NN \u05D0\u05D9-\u05D0\u05DE\u05D5\u05DF)) (PP (IN \u05D1) (NP (NP (NN \u05DE\u05DE\u05E9\u05DC\u05D4)) (ADJP (JJ \u05D1\u05E9\u05DC)))))) (yyCM ,) (VP (VB \u05D7\u05D9\u05E4\u05E9)) (NP (AT \u05D0\u05EA) (NP (H \u05D4) (NN \u05D0\u05EA\u05D5\u05E0\u05D5\u05EA))) (yyDOT .))",
				"(INTJ (NP (NP (H \u05D4) (NN \u05D9\u05D5\"\u05E8)) (NP (NNPG (NNP \u05D9\u05D5\u05DC\u05D9) (NNPG (NNP \u05D9\u05D5\u05D0\u05DC) (NNP \u05D0\u05D3\u05DC\u05E9\u05D8\u05D9\u05D9\u05DF))))))",
				"(FRAGQ (PP (IN \u05D4\u05D9\u05D5'\u05E8) (NP (NNPG (NNP \u05D9\u05D5\u05DC\u05D9) (NNPG (NNP \u05D9\u05D5\u05D0\u05DC) (NNP \u05D0\u05D3\u05DC\u05E9\u05D8\u05D9\u05D9\u05DF))))))",
				"(S (ADVP (RB \u05D2\u05E0\u05DF)) (VP (VB \u05D2\u05D9\u05D3\u05DC)) (NPSBJ (NNP \u05D3\u05D2\u05DF)) (PP (IN \u05D1) (NP (H \u05D4) (NN \u05D2\u05DF))))",
				"(S (NPSBJ (NNPG (NNP \u05D3\u05D2\u05DF) (NNP \u05D2\u05D3\u05D5\u05DC))) (VP (VB \u05D2\u05D3\u05DC)) (PP (IN \u05D1) (NP (H \u05D4) (NN \u05D2\u05DF))))",
				"(S (VP (VB \u05D9\u05E9\u05D1\u05EA\u05D9)) (PP (IN \u05D1) (NP (NN \u05D2\u05DF))) (S (ADJP (JJ \u05DB\u05DC\u05E9\u05D4\u05D5))))",
				"(S (NPSBJ (NNT \u05D5\u05E2\u05D3\u05EA) (NP (NP (H \u05D4) (NN \u05D7\u05D5\u05E5)) (CC \u05D5) (NP (H \u05D4) (NN \u05D1\u05D9\u05D8\u05D7\u05D5\u05DF)))) (VP (VB \u05D4\u05EA\u05DB\u05E0\u05E1\u05D4)) (ADVP (RB \u05D0\u05EA\u05DE\u05D5\u05DC)) (PP (IN \u05D1) (NP (H \u05D4) (NN \u05E2\u05E8\u05D1))) (yyDOT .))",
				"(INTJ (NP (NP (NNT \u05DE\u05D6\u05DB\u05D9\u05E8\u05EA) (NP (H \u05D4) (NN \u05DB\u05E0\u05E1\u05EA))) (NP (NNPG (NNP \u05D9\u05E8\u05D3\u05E0\u05D4) (NNP \u05DE\u05DC\u05E8-\u05D4\u05D5\u05E8\u05D5\u05D1\u05D9\u05E5)))))",
				"(INTJ (NP (NP (NNT \u05E1\u05D2\u05DF) (NP (NNT \u05E9\u05E8) (NP (H \u05D4) (NN \u05D0\u05D5\u05E6\u05E8)))) (NP (NNPG (NNP \u05DE\u05D9\u05E7\u05D9) (NNP \u05DC\u05D5\u05D9)))))",
				"(S (VP (VB \u05DE\u05E0\u05D4\u05DC)) (NPSBJ (NP (NNT \u05D1\u05D9\u05EA) (NP (H \u05D4) (NN \u05E1\u05E4\u05E8))) (PP (IN \u05E2\"\u05E9) (NP (NNP \u05D9\u05E6\u05D7\u05E7))) (NP (NP (NN \u05E8\u05D1\u05D9\u05DF)) (NP (NNPG (NNP \u05E2\u05D5\u05E4\u05E8) (NNP \u05E9\u05DC\u05D7))))))",
				"(S (NPSBJ (NNT \u05E1\u05D2\u05DF) (NP (NNT \u05E9\u05E8) (NP (H \u05D4) (NN \u05DE\u05DC\u05D7\u05DE\u05D4)))) (VP (VB \u05DB\u05E8\u05DE\u05DC)) (SBAR (COM \u05E9) (S (NPSBJ (NNT \u05D0\u05DE\u05D4) (NP (NNP \u05D4\u05DB\u05D4\u05DF))) (yyCM ,) (VP (VB \u05D7\u05D9\u05E4\u05E9)) (NP (AT \u05D0\u05EA) (NP (H \u05D4) (NN \u05D0\u05EA\u05D5\u05E0\u05D5\u05EA))))) (yyDOT .))",
				"(S (VP (VB \u05D4\u05E6\u05E2\u05D5\u05EA)) (NPSBJ (NP (NP (NNT \u05E1\u05D9\u05E2\u05D5\u05EA) (NP (H \u05D4) (NN \u05E2\u05D1\u05D5\u05D3\u05D4))) (yyCM ,) (NP (NP (NNT \u05D9\u05D4\u05D3\u05D5\u05EA) (NP (H \u05D4) (NX (NN \u05EA\u05D5\u05E8\u05D4) (yyDASH -) (NN \u05DE\u05E8\u05E6)))) (yyCM ,) (NP (NNPG (NNP \u05E9\"\u05E1) (NNPG (NNP \u05D5\u05E8\u05E2\"\u05DD) (yyDASH -) (NNP \u05EA\u05E2\"\u05DC) (yyDASH -) (NNP \u05DE\u05D3\"\u05E2) (yyDASH -) (NNP \u05D7\u05D3\"\u05E9) (yyDASH -) (NNP \u05D1\u05DC\"\u05D3)))))) (VP (VBINF \u05DC\u05D4\u05D1\u05D9\u05E2) (NP (NN \u05D0\u05D9-\u05D0\u05DE\u05D5\u05DF)) (PP (IN \u05D1) (NP (NP (H \u05D4) (NN \u05DE\u05DE\u05E9\u05DC\u05D4)) (MOD \u05D1\u05E9\u05DC))))))" };
		long now2 = System.currentTimeMillis();
		// System.out.println("String[] outputs = {");
		ExecutorService es = Executors.newFixedThreadPool(inputs.length);
		for (int i = 0; i < inputs.length; i++) {
			final String input = inputs[i];
			final String output = outputs[i];
			final int _i = i;
			es.execute(new Runnable() {
				@Override
				public void run() {
					Tree<String> tree;
					try {
						System.out.println("Starting " + _i);
						tree = ctm.parse(input);

						tree = TreeAnnotations.unAnnotateTree(tree);
						cleanTree(tree);
						String result = tree.getChildren().get(0).toString();
						System.out.println("Sentence " + _i + " " + result.equals(output) + "\n\tinput: " + input + "\n\tparsed: "
								+ result + "\n\texpected: " + output);

						System.out.println(" ");
					} catch (IOException e) {
						System.out.println("Sentence " + _i + " " + e.getMessage());
					}
				}
			});
		}
		es.shutdown();
		es.awaitTermination(4, TimeUnit.MINUTES);
		// for (String input : inputs) {
		// Tree<String> tree = ctm.parse(input);
		// tree = TreeAnnotations.unAnnotateTree(tree);
		// cleanTree(tree);
		// // System.out.println('"' +
		// StringEscapeUtils.escapeJava(tree.getChildren().get(0).toString()) +
		// "\",");
		// System.out.println("Assert that true? "+tree.getChildren().get(0).toString().equals(outputs[i]));
		// i++;
		// }
		// System.out.println("};");
		System.out.println(String.format("Decoding took me %.2fs", (System.currentTimeMillis() - now2) / 1000d));
		System.out.println(String.format("Took me %.2fs", (System.currentTimeMillis() - now) / 1000d));

	}

}
