package idc.nlp.ok.protocol.speaker.parser;

import idc.nlp.ok.model.Role;
import idc.nlp.ok.model.Speaker;
import idc.nlp.ok.protocol.speaker.parser.SpeakerParser;
import idc.nlp.ok.protocol.speaker.parser.common.SpeakerParserUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class SpeakerNamesParser implements SpeakerParser {

	public static final String NAME = "NAME-DICTIONARY";

	private Set<String> names = new HashSet<>();

	public String getParseMethodName() {
		return NAME;
	}

	public void init() throws IOException {
		Gson gson = new Gson();
		System.out.println("Starting "+getClass().getSimpleName());
		try (InputStream is = getClass().getResourceAsStream("/names.json");
				Reader reader = new InputStreamReader(is, Charset.forName("UTF8"));) {
			names = gson.fromJson(reader, new TypeToken<Set<String>>() {
			}.getType());
		}
		System.out.println("Using dictionary of "+names.size()+" names");
	}

	@Override
	public Speaker parse(String line) {
		// Anonymous
		Speaker speaker = SpeakerParserUtils.parseAnonymous(line);
		if (speaker != null)
			return speaker;
		speaker = SpeakerParserUtils.parseMK(line);
		if (speaker != null)
			return speaker;
		String origin = line;
		line = StringUtils.removeEnd(line, ":");
		String[] parts = StringUtils.split(line);
		ArrayUtils.reverse(parts);
		if (parts.length <= 2)
			return new Speaker(origin, line);
		int i = 2;
		ArrayList<String> rename = new ArrayList<>();
		rename.add(parts[0]);
		rename.add(parts[1]);
		for (; i < parts.length && i < 4; i++) {
			if (!names.contains(parts[i])) {
				break;
			}
			rename.add(parts[i]);
		}
		Collections.reverse(rename);
		String speakerName = StringUtils.trim(StringUtils.join(rename, ' '));
		String speakerRule = StringUtils.trim(StringUtils.removeEnd(line, speakerName));
		Role rule = new Role(speakerRule);
		speaker = new Speaker(origin, speakerName);
		speaker.setRole(rule);
		return speaker;
	}

}
