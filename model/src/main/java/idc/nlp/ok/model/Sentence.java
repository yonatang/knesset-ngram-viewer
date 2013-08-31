package idc.nlp.ok.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class Sentence {

	private String value;
	private List<Morpheme> morphemes = new ArrayList<>();
}
