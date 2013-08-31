package idc.nlp.ok.protocol.speaker.spi.nameparser;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import idc.nlp.ok.model.Role;
import idc.nlp.ok.model.Speaker;
import idc.nlp.ok.protocol.speaker.parser.SpeakerParser;
import idc.nlp.ok.protocol.speaker.parser.SpeakerParserFactory;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SpeakerParserNameTest {

	SpeakerParser sp;

	private String[][] tests = { { "היו\"ר יולי יואל אדלשטיין:", "יולי יואל אדלשטיין", "היו\"ר" },//
			{ "כבוד הנשיא שמעון פרס:", "שמעון פרס", "כבוד הנשיא" },//
			{ "אריה דרעי (ש\"ס):", "אריה דרעי", Role.MK, "ש\"ס" }, //
			{ "מנהל בית הספר ע\"ש יצחק רבין מנחם בגין:", "מנחם בגין", "מנהל בית הספר ע\"ש יצחק רבין" }, //
			{ "קריאות:", Speaker.CALL }, //
			{ "קריאה:", Speaker.CALL },

	};

	@BeforeClass
	void init() {
		sp = SpeakerParserFactory.instance(SpeakerNamesParser.NAME);
	}

	@Test
	public void shouldGetInstance() {
		Assert.assertNotNull(sp);

	}

	@Test
	public void shouldParseData() {
		for (String[] test : tests) {
			System.out.print("Parsing [" + test[0] + "]: ");
			Speaker speaker = sp.parse(test[0]);
			System.out.println(speaker);
			assertEquals(speaker.getName(), test[1]);
			if (test.length > 2) {
				assertNotNull(speaker.getRole());
				assertEquals(speaker.getRole().getName(), test[2]);
				if (test.length > 3) {
					assertEquals(speaker.getRole().getSecondary(), test[3]);
				} else {
					assertNull(speaker.getRole().getSecondary());
				}
			} else {
				assertNull(speaker.getRole());
			}

		}
	}
}
