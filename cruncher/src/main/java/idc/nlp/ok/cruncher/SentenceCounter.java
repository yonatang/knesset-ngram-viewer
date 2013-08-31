package idc.nlp.ok.cruncher;

import idc.nlp.ok.model.Paragraph;
import idc.nlp.ok.model.Part;
import idc.nlp.ok.model.Protocol;
import idc.nlp.ok.model.Sentence;

import java.io.File;
import java.io.FileReader;
import java.util.Collection;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;

public class SentenceCounter {

	public static void main(String... args) throws Exception{
		Collection<File> files=FileUtils.listFiles(new File("data/analyzed"), new String[]{"json"}, false);
		Gson g=new Gson();
		int count=0;
		int failCount=0;
		for (File f:files){
			try(FileReader fr=new FileReader(f);){
				Protocol p =g.fromJson(fr, Protocol.class);
				int protocolSent=0;
				for (Part part:p.getParts()){
					for (Paragraph par:part.getParagraphs()){
						for (Sentence sent:par.getSentences()){
							count++;
							protocolSent++;
							if (sent.getMorphemes()==null||sent.getMorphemes().isEmpty()){
								failCount++;
								System.out.println(p.getApiUrl()+": "+sent.getValue());
							}
						}
					}
				}
				System.out.println("Protocol "+p.getApiUrl()+" has "+protocolSent);
			}
			
		}
		System.out.println("Total sentences: "+count);
		System.out.println("Failed sentences: "+failCount);
	}
}
