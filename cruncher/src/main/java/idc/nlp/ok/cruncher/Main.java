package idc.nlp.ok.cruncher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;

public class Main {
	// hint - get plenium protocols from
	// http://oknesset.org/api/committeemeeting/7340/?format=xml
	public Main(File inputDir, File outputDir) throws FileNotFoundException, IOException {
		XmlProcessor xmlProcessor = new XmlProcessor();
		Random r = new Random();
		while (true) {
			// pick a file of the .xmls files at random, to reduce chances of race condition
			List<File> xmls = new ArrayList<>(FileUtils.listFiles(inputDir, new String[] { "xml" }, false));
			if (xmls.isEmpty())
				break;
			xmlProcessor.process(xmls.get(r.nextInt(xmls.size())), outputDir);
		}
		System.out.println("Done.");
	}

	public static void main(String... args) throws FileNotFoundException, IOException {
		if (args.length != 2) {
			System.out.println("Must have two args: in directory and out directory");
			return;
		}
		File inDir = new File(args[0]);
		File outDir = new File(args[1]);
		if (!inDir.exists() || !inDir.isDirectory()) {
			System.out.println("Directory " + inDir + " must be existing");
			return;
		}
		if (!outDir.exists()) {
			if (!outDir.mkdirs()) {
				System.out.println("Cannot create direcotory " + outDir);
				return;
			}
		}
		if (!outDir.isDirectory()) {
			System.out.println("File " + outDir + " is not a directory");
			return;
		}

		new Main(inDir, outDir);

	}

}
