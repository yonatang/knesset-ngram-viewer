package idc.nlp.ok.protocol.parser;

import idc.nlp.ok.model.Paragraph;
import idc.nlp.ok.model.Part;
import idc.nlp.ok.model.Protocol;
import idc.nlp.ok.model.Sentence;
import idc.nlp.ok.model.Speaker;
import idc.nlp.ok.protocol.speaker.parser.SpeakerParser;
import idc.nlp.ok.protocol.speaker.parser.SpeakerParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.AbstractFilter;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.util.IteratorIterable;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ProtocolParser {

	private SpeakerParser sp;

	public ProtocolParser() {
		sp = SpeakerParserFactory.instance();
	}

	@SuppressWarnings("serial")
	private class SpeakerFilter extends AbstractFilter<Element> {

		@Override
		public Element filter(Object content) {
			if (content instanceof Element) {
				Element element = (Element) content;
				if (!element.getName().equals("emphasis"))
					return null;
				if (element.getTextTrim() == null)
					return null;
				if (!element.getTextTrim().endsWith(":"))
					return null;

				Attribute roleAttr = element.getAttribute("role");
				if (roleAttr == null)
					return null;

				if (!"bold".equals(roleAttr.getValue()))
					return null;
				return element;
			}
			return null;
		}

	}

	private static final String PROTOCOL_TEXT_ELEM_NAME = "protocol_text";

	private Protocol parseXml(String doc) throws JDOMException, IOException {
		SAXBuilder sb = new SAXBuilder();

		sb.setXMLReaderFactory(XMLReaders.NONVALIDATING);
		sb.setFeature("http://xml.org/sax/features/validation", false);
		sb.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		sb.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

		// System.out.println("Starting to parse doc");
		Document actualDoc = sb.build(new StringReader(doc));
		// System.out.println("Doc parsed");
		SpeakerFilter sf = new SpeakerFilter();
		// System.out.println("Filtering");
		IteratorIterable<Element> speakers = actualDoc.getDescendants(sf);
		// System.out.println("Filtered");

		Protocol protocol = new Protocol();
		protocol.setVersion("1.0");

		PeekingIterator<Element> pi = Iterators.peekingIterator(speakers);
		while (pi.hasNext()) {
			Element speakerElem = pi.next();
			Element nextSpeakerElem = null;
			if (pi.hasNext()) {
				nextSpeakerElem = pi.peek();
			}
			Speaker speaker = sp.parse(speakerElem.getText());
			Part part = new Part();
			protocol.getParts().add(part);

			part.setSpeaker(speaker);

			// System.out.println(speaker);
			List<Element> sisters = speakerElem.getParentElement().getParentElement().getChildren();
			Iterator<Element> sistersI = sisters.iterator();
			boolean foundSpeaker = false;
			while (sistersI.hasNext()) {
				Element sister = sistersI.next();
				Element firstChild = sister.getChild("emphasis");

				if (firstChild != null && firstChild.equals(speakerElem)) {
					foundSpeaker = true;
					continue;
				}
				if (foundSpeaker) {
					if (firstChild != null && firstChild.equals(nextSpeakerElem)) {
						foundSpeaker = false;
						continue;
					}
					if (!sister.getTextTrim().isEmpty()) {
						// System.out.println("Text: " + sister.getTextTrim());
						Paragraph paragraph = new Paragraph();
						for (String sentenceStr : parseSentences(sister.getTextTrim())) {
							Sentence sentence = new Sentence();
							sentence.setValue(sentenceStr);
							paragraph.getSentences().add(sentence);
						}
						part.getParagraphs().add(paragraph);
					}

				}

			}
		}
		return protocol;
	}

	private Iterable<String> parseSentences(String paragraph) {
		paragraph = StringUtils.replace(paragraph, ".", ".\n");
		paragraph = StringUtils.replace(paragraph, "!", "!\n");
		paragraph = StringUtils.replace(paragraph, "?", "?\n");
		paragraph = StringUtils.replace(paragraph, ";", ";\n"); // TODO ask Reut
		return Splitter.on('\n').trimResults().omitEmptyStrings().split(paragraph);
	}

	public Protocol parse(InputStream is) throws Exception {
		return parse(new InputStreamReader(is, Charset.forName("UTF8")));
	}

	public Protocol parse(Reader is) throws Exception {

		SAXBuilder sb = new SAXBuilder();

		sb.setXMLReaderFactory(XMLReaders.NONVALIDATING);
		sb.setFeature("http://xml.org/sax/features/validation", false);
		sb.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
		sb.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		Document d = sb.build(is);
		ElementFilter ef = new ElementFilter(PROTOCOL_TEXT_ELEM_NAME);
		IteratorIterable<Element> els = d.getDescendants(ef);
		for (Element e : els) {
			String doc = e.getText();
			if (doc.startsWith("<?xml")) {
				Protocol protocol = parseXml(doc);
				try {
					SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
					Element dateChild = d.getRootElement().getChild("date");
					if (dateChild == null)
						throw new NullPointerException("Cannot find <date> element");
					protocol.setDate(dateFormatter.parse(dateChild.getTextTrim()));
				} catch (Exception ex) {
					System.out.println("Cannot parse date for protocol: " + ex);
				}
				try {
					Element urlChild = d.getRootElement().getChild("url");
					if (urlChild == null)
						throw new NullPointerException("Cannot find <url> element");
					protocol.setApiUrl(urlChild.getTextTrim());
				} catch (Exception ex) {
					System.out.println("Cannot parse url for protocol: " + ex);
				}
				return protocol;
			}
		}
		return null;
	}

	public static void main(String... args) throws Exception {
		for (String key : System.getProperties().stringPropertyNames()) {
			System.out.println(key + "=" + System.getProperty(key));
		}
		ProtocolParser pp = new ProtocolParser();
		Protocol p = pp.parse(new FileInputStream("7208.xml"));
		System.out.println(p.getParts().size());
		System.out.println(p.getParts().get(0));
		FileWriter fw = new FileWriter(new File("7208.json"));
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		gson.toJson(p, fw);
		fw.flush();
		fw.close();
		System.out.println("DONE");
	}
}
