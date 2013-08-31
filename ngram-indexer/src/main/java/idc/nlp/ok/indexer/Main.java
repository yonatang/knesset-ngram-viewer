package idc.nlp.ok.indexer;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

public class Main {

	public static void main(String... args) throws Exception {
		Options options = new Options();
		@SuppressWarnings("static-access")
		Option ngramSize = OptionBuilder.hasArg().isRequired().withType(Number.class).withArgName("n")
				.withDescription("N-Grams size").create('n');
		@SuppressWarnings("static-access")
		Option dbUrlOption = OptionBuilder.hasArg().withArgName("db-url")
				.withDescription("Optional db url (default value: tcp://localhost/~/knesset)").create("dbUrl");
		@SuppressWarnings("static-access")
		Option dirOption = OptionBuilder.hasArg().withArgName("source-dir")
				.withDescription("Directory of parsed jsons").create("f");
		options.addOption(ngramSize);
		options.addOption(dbUrlOption);
		options.addOption(dirOption);

		CommandLineParser cliParser = new PosixParser();
		CommandLine cl;
		try {
			cl = cliParser.parse(options, args);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("runner", options);
			System.out.println();
			return;
		}

		int n;
		String dbUrl;
		File src;
		try {
			long nn = (long) cl.getParsedOptionValue("n");
			n = (int) nn;
			dbUrl = cl.getOptionValue("db-url", "tcp://localhost/~/knesset");
			src = new File(cl.getOptionValue("f"));
			if (!src.isDirectory()) {
				System.out.println(src + " is not a directory");
				return;
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			return;
		}

		Set<String> skip = new HashSet<>();
		skip.add("IN");
		skip.add("CC");
		skip.add("AT");
		skip.add("H");
		skip.add("REL");
		skip.add("COM");

		NGramCounter counter = new NGramCounter(null, skip, n);
		NgramCountPartyIndexer idxParty = new NgramCountPartyIndexer("jdbc:h2:" + dbUrl);
		NgramCountDateIndexer idxDate = new NgramCountDateIndexer("jdbc:h2:" + dbUrl);
		System.out.println("Party indexing...");
		idxParty.index(counter, src);
		System.out.println("Date indexing...");
		idxDate.index(counter, src);

	}
}
