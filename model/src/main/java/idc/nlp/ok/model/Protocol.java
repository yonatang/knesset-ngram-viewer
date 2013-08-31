package idc.nlp.ok.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class Protocol {

	private String version;
	
	private String apiUrl;
	
	private Date date;
	
	private final List<Part> parts = new ArrayList<>();

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Part p : parts) {
			p.toString(sb);
		}
		return sb.toString();
	}
}
