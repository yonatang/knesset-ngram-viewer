package idc.nlp.ok.protocol.speaker.parser.common;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import idc.nlp.ok.model.Role;
import idc.nlp.ok.model.Speaker;

import org.testng.annotations.Test;

public class SpeakerParserUtilsTest {
	private String[][] testsMk = { { "אריה דרעי (ש\"ס):", "אריה דרעי", Role.MK, "ש\"ס" }, //
			{ "יחיאל (חיליק) כהן (עבודה):", "יחיאל (חיליק) כהן", Role.MK, "עבודה" }, //
	};
	private String[][] testsCalls = { { "קריאות:", Speaker.CALL }, //
			{ "קריאה:", Speaker.CALL }, };
	
	private String[][] testsOther = {{"ציפי חוטובלי (בשם ועדת החוקה, חוק ומשפט):", "ציפי חוטובלי", "ועדת החוקה, חוק ומשפט" },// 
			{"יריב לוין (יו\"ר ועדת הכנסת):", "יריב לוין", "יו\"ר ועדת הכנסת" }, //
	};

	@Test
	public void shouldParseMKData() {
		for (String[] test : testsMk) {
			Speaker speaker = SpeakerParserUtils.parseMK(test[0]);
			System.out.println(speaker);
			assertNotNull(speaker);
			assertEquals(speaker.getName(), test[1]);
			assertNotNull(speaker.getRole());
			assertEquals(speaker.getRole().getName(), test[2]);
			assertEquals(speaker.getRole().getSecondary(), test[3]);
		}
	}

	@Test
	public void shouldParseCallsData() {
		for (String[] test : testsCalls) {
			Speaker speaker = SpeakerParserUtils.parseAnonymous(test[0]);
			assertNotNull(speaker);
			assertEquals(speaker.getName(), test[1]);
			assertNull(speaker.getRole());
		}
	}
	
	@Test
	public void shouldParseOtherData() {
		for (String[] test : testsOther) {
			Speaker speaker = SpeakerParserUtils.parseMK(test[0]);
			assertNotNull(speaker);
			assertEquals(speaker.getName(), test[1]);
			assertNotNull(speaker.getRole());
			assertEquals(speaker.getRole().getName(), test[2]);
			assertNull(speaker.getRole().getSecondary());
		}
	}

}
