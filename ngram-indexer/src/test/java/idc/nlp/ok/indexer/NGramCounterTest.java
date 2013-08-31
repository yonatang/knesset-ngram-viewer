package idc.nlp.ok.indexer;

import static org.testng.Assert.assertEquals;
import idc.nlp.ok.model.Morpheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.testng.annotations.Test;

import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

@Test
public class NGramCounterTest {

	public void shouldBasicWork() {
		List<Morpheme> sentence = new ArrayList<>();
		sentence.add(new Morpheme("a", "TAG1"));
		sentence.add(new Morpheme("a", "TAG1"));
		sentence.add(new Morpheme("b", "TAG2"));
		sentence.add(new Morpheme("c", "TAG3"));
		sentence.add(new Morpheme("d", "TAG4"));

		test(3, null, null, sentence,//
				Arrays.asList(new ExpectResult("a", 2),//
						new ExpectResult("a a", 1),//
						new ExpectResult("a a b", 1),//
						new ExpectResult("a a b c", 0),//
						new ExpectResult("a a b c d", 0),//
						new ExpectResult("a b", 1),//
						new ExpectResult("a b c", 1),//
						new ExpectResult("a b c d", 0),//
						new ExpectResult("a b d", 0),//
						new ExpectResult("b", 1),//
						new ExpectResult("b c", 1),//
						new ExpectResult("b c d", 1),//
						new ExpectResult("c", 1),//
						new ExpectResult("c d", 1),//
						new ExpectResult("d", 1)));
	}

	public void shouldWorkWithStop() {
		List<Morpheme> sentence = new ArrayList<>();
		sentence.add(new Morpheme("a", "TAG1"));
		sentence.add(new Morpheme("b", "TAG2"));
		sentence.add(new Morpheme("c", "TAG3"));
		sentence.add(new Morpheme("d", "TAG4"));

		test(3, Sets.newHashSet("TAG3"), null, sentence,//
				Arrays.asList(new ExpectResult("a", 1), //
						new ExpectResult("a b", 1),//
						new ExpectResult("a b c", 0),//
						new ExpectResult("a b c d", 0),//
						new ExpectResult("a b d", 0),//
						new ExpectResult("b", 1),//
						new ExpectResult("b c", 0),//
						new ExpectResult("b c d", 0),//
						new ExpectResult("b d", 0),//
						new ExpectResult("c", 0),//
						new ExpectResult("c d", 0),//
						new ExpectResult("d", 1)));
	}

	public void shouldWorkWithSkip() {
		List<Morpheme> sentence = new ArrayList<>();
		sentence.add(new Morpheme("a", "TAG1"));
		sentence.add(new Morpheme("b", "TAG2"));
		sentence.add(new Morpheme("c", "TAG3"));
		sentence.add(new Morpheme("d", "TAG4"));
		sentence.add(new Morpheme("e", "TAG5"));

		test(3, null, Sets.newHashSet("TAG3"), sentence,//
				Arrays.asList(new ExpectResult("a", 1), //
						new ExpectResult("a b", 1),//
						new ExpectResult("a b c", 0),//
						new ExpectResult("a b c d", 0),//
						new ExpectResult("a b c d e", 0),//
						new ExpectResult("a b d", 1),//
						new ExpectResult("a b d e", 0),//
						new ExpectResult("b", 1),//
						new ExpectResult("b c", 0),//
						new ExpectResult("b c d", 0),//
						new ExpectResult("b c d e", 0),//
						new ExpectResult("b d", 1),//
						new ExpectResult("b d e", 1),//
						new ExpectResult("c", 0),//
						new ExpectResult("c d", 0),//
						new ExpectResult("c d e", 0),//
						new ExpectResult("d", 1),//
						new ExpectResult("d e", 1),//
						new ExpectResult("e", 1)));//

	}

	private void test(int ngramSize, Set<String> stop, Set<String> skip, List<Morpheme> sentence,
			List<ExpectResult> tests) {
		NGramCounter counter = new NGramCounter(stop, skip, ngramSize);
		Multiset<String> result = counter.count(sentence, null);
		System.out.println(result);
		int expCount = 0;
		for (ExpectResult test : tests) {
			assertEquals(result.count(test.ngram), test.count, "Count for " + test.ngram);
			System.out.println("Testing " + test.ngram + " " + test.count);
			expCount += test.count;
		}
		assertEquals(result.size(), expCount, "Total size of result set");

	}

	private class ExpectResult {
		public ExpectResult(String ngram, int count) {
			this.ngram = ngram;
			this.count = count;
		}

		String ngram;
		int count;
	}

}
