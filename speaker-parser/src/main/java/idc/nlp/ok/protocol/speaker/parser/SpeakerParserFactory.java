package idc.nlp.ok.protocol.speaker.parser;

import java.util.Iterator;
import java.util.ServiceLoader;

public class SpeakerParserFactory {

	public static SpeakerParser instance() {
		ServiceLoader<SpeakerParser> loader = ServiceLoader.load(SpeakerParser.class);
		Iterator<SpeakerParser> it = loader.iterator();
		while (it.hasNext()) {
			SpeakerParser next = it.next();
			try {
				next.init();
				return next;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public static SpeakerParser instance(String name) {
		ServiceLoader<SpeakerParser> loader = ServiceLoader.load(SpeakerParser.class);
		Iterator<SpeakerParser> it = loader.iterator();
		while (it.hasNext()) {
			SpeakerParser next = it.next();
			if (!next.getParseMethodName().equals(name)) {
				continue;
			}
			try {
				next.init();
				return next;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}
