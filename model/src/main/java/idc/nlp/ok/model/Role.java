package idc.nlp.ok.model;

import lombok.Data;
import lombok.NonNull;

@Data
public class Role {

	public static final String MK = "חבר כנסת";

	@NonNull
	private String name;

	private String secondary;

}
