package idc.nlp.ok.cruncher;

import idc.nlp.ok.model.Morpheme;
import idc.nlp.ok.model.Paragraph;
import idc.nlp.ok.model.Part;
import idc.nlp.ok.model.Protocol;
import idc.nlp.ok.model.Sentence;
import idc.nlp.ok.protocol.parser.ProtocolParser;
import il.ac.idc.nlp.constit.ConstitTreeMaker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import edu.berkeley.nlp.syntax.Tree;

public class XmlProcessor {

	private final ConstitTreeMaker treeMaker;

	public XmlProcessor() throws IOException {
		InputStream wc = ConstitTreeMaker.class.getResourceAsStream("/full_nodef.twcount");
		InputStream lex = ConstitTreeMaker.class.getResourceAsStream("/lexicon.utf8.sqlite");
		treeMaker = new ConstitTreeMaker(wc, lex);
	}

	public void process(File xml, File outDir) throws FileNotFoundException, IOException {
		outDir.mkdirs();
		Path p = xml.toPath();
		Path newPath = p.resolveSibling(xml.getName() + ".process");
		try {
			Files.move(p, newPath, StandardCopyOption.ATOMIC_MOVE);
		} catch (NoSuchFileException e) {
			// file is processed by someone else
			return;
		}
		System.out.println("Reading protocol " + xml.getName());
		
		String baseName = FilenameUtils.removeExtension(FilenameUtils.getName(xml.getAbsolutePath()));
		File jsonOutput = new File(outDir, baseName + ".json");
		File processedXml = newPath.toFile();
		xml.renameTo(processedXml);
		ProtocolParser pp = new ProtocolParser();
		Protocol protocol = null;
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try (FileInputStream fis = new FileInputStream(processedXml);
				InputStreamReader isr=new InputStreamReader(fis, Charset.forName("UTF8"));) {
			try {
				protocol = pp.parse(isr);
				System.out.println("Done reading the protocol");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (protocol == null) {
			processedXml.renameTo(new File(xml.getAbsolutePath() + ".error"));
			return;
		}

		System.out.println("Analyzing " + xml.getName());
		long now = System.currentTimeMillis();
		for (Part part : protocol.getParts()) {
			for (Paragraph paragraph : part.getParagraphs()) {
				for (Sentence sentence : paragraph.getSentences()) {
					try {
						Tree<String> tree = treeMaker.parse(sentence.getValue());

						ConstitTreeMaker.cleanTree(tree);
						List<String> yield = tree.getYield();
						List<String> analysis = tree.getPreTerminalYield();
						Iterator<String> yi = yield.iterator();
						Iterator<String> ai = analysis.iterator();
						while (yi.hasNext() && ai.hasNext()) {
							Morpheme m = new Morpheme();
							m.setVal(yi.next());
							m.setAnl(ai.next());
							sentence.getMorphemes().add(m);
						}
						System.out.print('.');
					} catch (Exception e) {
						System.out.println("Exception while parsing sentence [" + sentence + "]");
					}
				}
			}
		}
		long done = System.currentTimeMillis();
		System.out.println("\nDone analyzing");
		try (FileOutputStream fos = new FileOutputStream(jsonOutput)) {
			try {
				String protocolJson = gson.toJson(protocol);
				IOUtils.write(protocolJson, fos);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		processedXml.renameTo(new File(xml.getAbsolutePath() + ".done"));
		System.out.println(String.format("Took me %.2f seconds to parse document " + xml.getName(),
				(done - now) / 1000.0));

	}
}
