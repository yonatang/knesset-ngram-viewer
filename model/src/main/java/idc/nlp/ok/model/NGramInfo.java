package idc.nlp.ok.model;

import lombok.Data;
import lombok.NonNull;

@Data
public class NGramInfo {

	@NonNull
	private Speaker speaker;
	private int count;
}
