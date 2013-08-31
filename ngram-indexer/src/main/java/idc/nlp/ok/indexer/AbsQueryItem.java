package idc.nlp.ok.indexer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class AbsQueryItem {
	protected final String ngram;
	protected final double val;
}
