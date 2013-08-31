package idc.nlp.ok.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import lombok.Data;

@Data
public class Paragraph {
	List<Sentence> sentences = new ArrayList<>();
	
	public void toString(StringBuilder sb) {
		Iterator<Sentence> i=sentences.iterator();
		while (i.hasNext()){
			sb.append(i.next().getValue());
			if (i.hasNext())
				sb.append(' ');
		}
	}
}
