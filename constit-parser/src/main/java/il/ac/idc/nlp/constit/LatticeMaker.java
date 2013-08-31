package il.ac.idc.nlp.constit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.python.core.PyObject;
import org.python.google.common.base.Preconditions;
import org.python.util.PythonInterpreter;
import org.zeroturnaround.zip.ZipUtil;

public class LatticeMaker implements Parser {

	private Parser parser;

	private File tempDir;

	private static final String LEMMA_FILENAME = "py" + File.separator + "lexicon" + File.separator + "BguLex2utf8"
			+ File.separator + "data" + File.separator + "lexicon.utf8.sqlite";
	private static final String WORD_COUNT_FILENAME = "py" + File.separator + "lexicon" + File.separator
			+ "full_nodef.twcount";

	private void init(InputStream wordCount, InputStream lemlex) throws IOException {
		Preconditions.checkNotNull(wordCount, "Missing wordCount stream");
		Preconditions.checkNotNull(lemlex, "Missing lemmas sqlite stream");

		tempDir = File.createTempFile("parser", ".py.tmp");
		System.out.println(tempDir);
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				// LatticeMaker.this.cleanup();
			}
		}));
		tempDir.delete();
		tempDir.mkdir();

		ZipUtil.unpack(LatticeMaker.class.getResourceAsStream("/python.zip"), tempDir);
		File tempLexFile = new File(tempDir, LEMMA_FILENAME);
		FileUtils.copyInputStreamToFile(lemlex, tempLexFile);
		File tempWordCountFile = new File(tempDir, WORD_COUNT_FILENAME);
		FileUtils.copyInputStreamToFile(wordCount, tempWordCountFile);
		// FileUtils.copyDirectory(new
		// File(LatticeMaker.class.getResource("/py").getFile()), new
		// File(tempDir, "py"));

		PythonInterpreter pi = new PythonInterpreter();
		File parseFile = new File(tempDir, "py/parse.py");
		pi.exec("__file__ = '" + parseFile.getAbsolutePath() + "'");
		pi.execfile(parseFile.getAbsolutePath());
		PyObject po = pi.get("PyParser").__call__();
		parser = (Parser) po.__tojava__(Parser.class);

		if (SqlLiteWrapper.IN_MEMORY_DB)
			cleanup();
	}

	public LatticeMaker(File wordCount, File lemlex) throws IOException {
		try (FileInputStream wordCountIs = new FileInputStream(wordCount);
				FileInputStream lemlexIs = new FileInputStream(lemlex);) {
			init(wordCountIs, lemlexIs);
		}
	}

	public LatticeMaker(InputStream wordCount, InputStream lemlex) throws IOException {
		init(wordCount, lemlex);
		IOUtils.closeQuietly(wordCount);
		IOUtils.closeQuietly(lemlex);

	}

	@Override
	public String parse_sent(String utf8_sent, boolean tokenized) {
		return parser.parse_sent(utf8_sent, tokenized);
	}

	public void cleanup() {
		if (tempDir != null && tempDir.exists()) {
			try {
				SqlLiteWrapper.disconnectAll();
			} catch (Exception e) {
				e.printStackTrace();
			}
			FileUtils.deleteQuietly(tempDir);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		cleanup();
	}

}
