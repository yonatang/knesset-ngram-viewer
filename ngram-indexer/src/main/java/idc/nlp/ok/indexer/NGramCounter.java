package idc.nlp.ok.indexer;

import idc.nlp.ok.model.Morpheme;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.RandomAccess;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public class NGramCounter {

	private Set<String> stopTags = new HashSet<>();
	private Set<String> ignoreTags = new HashSet<>();
	private int maxNGramSize;

	public NGramCounter(Set<String> stopTags, Set<String> ignoreTags, int maxNGramSize) {
		if (stopTags != null)
			this.stopTags.addAll(stopTags);
		if (ignoreTags != null)
			this.ignoreTags.addAll(ignoreTags);
		this.maxNGramSize = maxNGramSize;
	}

	protected boolean isStopTag(Morpheme m) {
		if (m.getAnl().startsWith("yy"))
			return true;
		return false;
	}

	protected boolean isSkipTag(Morpheme m) {
		return false;
	}

	public Multiset<String> count(List<Morpheme> morphemes, Function<String, String> function) {
		Multiset<String> result = HashMultiset.create();
		if (!(morphemes instanceof RandomAccess)) {
			morphemes = new ArrayList<>(morphemes);
		}

		for (int i = 0; i < morphemes.size(); i++) {
			int j = 0;
			int ngramSize = 0;
			StringBuilder sb = new StringBuilder();
			while (j + i < morphemes.size() && ngramSize < maxNGramSize) {
				Morpheme m = morphemes.get(i + j);
				j++;
				if (ignoreTags.contains(m.getAnl()) || isSkipTag(m)) {
					if (j == 1)
						break;
					continue;
				} else {
					ngramSize++;
				}
				if (stopTags.contains(m.getAnl()) || isStopTag(m)) {
					break;
				}
				if (function != null)
					sb.append(function.apply(m.getVal()));
				else if (function == null)
					sb.append(m.getVal());
				sb.append(' ');
				result.add(sb.toString().trim());
			}
		}
		return result;
	}
}
