package idc.nlp.ok.protocol.speaker.parser;

import idc.nlp.ok.model.Speaker;

public interface SpeakerParser {

	Speaker parse(String line);

	void init() throws Exception;

	String getParseMethodName();
}
