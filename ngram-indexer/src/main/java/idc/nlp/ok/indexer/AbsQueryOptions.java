package idc.nlp.ok.indexer;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@AllArgsConstructor
@EqualsAndHashCode
public abstract class AbsQueryOptions {

	@Getter
	private String ngram;
	
	
}
