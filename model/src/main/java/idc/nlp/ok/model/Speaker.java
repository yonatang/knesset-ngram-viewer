package idc.nlp.ok.model;

import lombok.Data;
import lombok.NonNull;

@Data
public class Speaker {
	public static final String CALL = "קריאה";

	@NonNull
	private String source;

	@NonNull
	private String name;

	private Role role;

}
