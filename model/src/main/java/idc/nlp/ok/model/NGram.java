package idc.nlp.ok.model;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;

@Data
public class NGram {

	private String ngram;
	private int size;
	private Set<NGramInfo> info = new HashSet<>();
}
