package idc.nlp.ok.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

import org.apache.commons.io.IOUtils;

@Data
public class Part {

	private Speaker speaker;

	private final List<Paragraph> paragraphs = new ArrayList<>();

	public void toString(StringBuilder sb) {
		sb.append(speaker.toString()).append(IOUtils.LINE_SEPARATOR);
		for (Paragraph paragraph : paragraphs) {
			paragraph.toString(sb);
			sb.append(IOUtils.LINE_SEPARATOR);
		}
	}
}
