package idc.nlp.ok.protocol.speaker.parser;

import lombok.Synchronized;

public class SpeakerParserFactory {

	private static SpeakerParser sp;

	@Synchronized
	public static SpeakerParser instance() {
		if (sp == null) {
			try {
				sp = new SpeakerNamesParser();
				sp.init();
			} catch (Exception e) {
				sp = null;
				throw new RuntimeException("Cannot create SpeakerNamesParser", e);
			}
		}
		return sp;
	}
}
