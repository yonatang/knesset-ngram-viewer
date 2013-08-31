package idc.nlp.ok.protocol.speaker.parser.common;

import idc.nlp.ok.model.Role;
import idc.nlp.ok.model.Speaker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpeakerParserUtils {

	private static final Pattern MK_PATTERN = Pattern.compile("(?<name>.*) \\((?<party>.*)\\)\\:");
	private static final String CALL = "קריאה:";
	private static final String CALLS = "קריאות:";
	private static final String BEHALF_OF = "בשם ";
	private static final String HEAD_OF = "יו\"ר ";

	public static Speaker parseMK(String speakerLine) {
		Matcher m = MK_PATTERN.matcher(speakerLine);
		if (!m.matches()) {
			return null;
		}
		String name = m.group("name");
		String party = m.group("party");

		Role role;
		if (party.startsWith(BEHALF_OF)) {
			role = new Role(party.substring(BEHALF_OF.length()));
		} else if (party.startsWith(HEAD_OF)) {
			role = new Role(party);
		} else {
			role = new Role(Role.MK);
			role.setSecondary(party);
		}
		Speaker speaker = new Speaker(speakerLine, name);
		speaker.setSource(speakerLine);
		speaker.setRole(role);
		return speaker;
	}

	public static Speaker parseAnonymous(String speakerLine) {
		if (speakerLine.equals(CALL) || speakerLine.equals(CALLS)) {
			return new Speaker(speakerLine, Speaker.CALL);
		}
		return null;
	}

}
