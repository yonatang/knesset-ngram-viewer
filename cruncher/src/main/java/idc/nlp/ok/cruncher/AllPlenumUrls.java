package idc.nlp.ok.cruncher;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.commons.lang3.StringUtils;

public class AllPlenumUrls {

	public static void main(String... args) throws Exception {
		Set<String> ids = new HashSet<>();
		int i = 1;
		Pattern p = Pattern.compile("<a href=\"/plenum/[0-9]+/\">");
		while (true) {
			URL url = new URL("http://oknesset.org/plenum/all_meetings/?&page=" + i);
			System.out.println("Scanning " + url);
			try (InputStream is = (InputStream) url.getContent();) {
				String content = IOUtils.toString(is);
				// System.out.println(content);
				Matcher m = p.matcher(content);
				System.out.println("  Found plenum ids: ");
				while (m.find()) {
					String partialUrl = m.group();
					String id = StringUtils
							.removeEnd(StringUtils.removeStart(partialUrl, "<a href=\"/plenum/"), "/\">");
					System.out.print(id + ", ");
					ids.add(id);
					// urls.add(String.format("http://oknesset.org/api/committeemeeting/%s/?format=xml",
					// id));
				}
				System.out.println();

			} catch (FileNotFoundException e) {
				break;
			}
			i++;
		}

		for (String id : ids) {
			URL url = new URL(String.format("http://oknesset.org/api/committeemeeting/%s/?format=xml", id));
			System.out.print("Fetching " + url + "... ");
			new File("data/pxmls").mkdirs();
			try (InputStream is = (InputStream) url.getContent();
					FileWriterWithEncoding fw = new FileWriterWithEncoding(new File("data/pxmls/" + id + ".xml"),
							Charset.forName("UTF-8"));) {
				IOUtils.copy(is, fw);
			}
			System.out.println("Done");
		}

	}
}
