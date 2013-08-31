package yg.blatt;

import pyutils.Generator;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import edu.berkeley.nlp.io.PTBLineLexer;

public class LatticeInputsFromRawSentences extends Generator<LatticeInput> {

	BufferedReader _reader;
	TypesLexicon _tlex = null;
	boolean ec_format = false;
	private boolean goldPOS;
	private boolean tokenize;
	private PTBLineLexer tokenizer;
	

	//public LatticeInputsFromRawSentences(File filename) throws IOException {
	//	this._reader = new BufferedReader(new InputStreamReader(
	//			new FileInputStream(filename), "utf8"));
	//}

	//public LatticeInputsFromRawSentences(BufferedReader reader)
	//		throws IOException {
	//	this._reader = reader;
	//}

	
	
	public LatticeInputsFromRawSentences(BufferedReader reader, boolean ec_format, boolean goldPos, boolean tokenize, PTBLineLexer tokenizer, TypesLexicon typesLexicon) 	throws IOException {
		this._reader = reader;
		this.ec_format = ec_format;
		this.goldPOS = goldPos;
		this.tokenize = tokenize;
		this.tokenizer = tokenizer;
		this._tlex = typesLexicon;
	}

	public LatticeInputsFromRawSentences setLexicon(TypesLexicon tlex) {
		this._tlex = tlex;
		return this;
	}

	public void func() {
		String line = null;
		String sentenceID = null;
		String origLine = null;
		try {
			while((line=_reader.readLine()) != null){
			    line = line.trim();
			    origLine = line;
			    if (ec_format && line.equals("")) continue;  
				List<String> sentence = null;
				List<String> posTags = null;

				if (goldPOS){
					sentence = new ArrayList<String>();
					posTags = new ArrayList<String>();
					List<String> tmp = Arrays.asList(line.split("\t"));
					if (tmp.size()==0) continue;
					//  				System.out.println(line+tmp);
					sentence.add(tmp.get(0));
					String[] tags = tmp.get(1).split("-");
					posTags.add(tags[0]);
					while(!(line=_reader.readLine()).equals("")){
						tmp = Arrays.asList(line.split("\t"));
						if (tmp.size()==0) break;
						//    				System.out.println(line+tmp);
						sentence.add(tmp.get(0));
						tags = tmp.get(1).split("-");
						posTags.add(tags[0]);
					}
				} else {
					if (ec_format){
						int breakIndex = line.indexOf(">");
						sentenceID = line.substring(3,breakIndex-1);
						line = line.substring(breakIndex+2, line.length()-5);
					}
					if (!tokenize) sentence = Arrays.asList(line.split("\\s+"));
					else {
						sentence = tokenizer.tokenizeLine(line);
					}
				}
				LatticeInput sentLat = this.fromWords(sentence, posTags);
				sentLat.setProperty("id", sentenceID);
				sentLat.setProperty("line", origLine);
				yield(sentLat);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println("error reading file.");
			e.printStackTrace();
		}

		return;
	}
	
	
	protected LatticeInput fromWords(List<String> words) { return this.fromWords(words,null); }
	
	protected LatticeInput fromWords(List<String> words, List<String> posTags) {
		PlainLatticeInput lat = new PlainLatticeInput();
		if (posTags != null && posTags.size()==0) { posTags = null; }
		Iterator<String> wit = words.iterator();
		Iterator<String> pit = posTags == null ? null : posTags.iterator();
		int start=0;
		while (wit.hasNext()) {
			String form = wit.next();
			String tag  = pit == null ? null : pit.next();
			int end = start + 1;
			
			if (tag == null) {
				//System.out.println("tlex is:" + _tlex);
				for (short t : _tlex.possibleNumericTagsForWord(form)) {
					lat.addWordData(start, end, form, t);
				}
			} else {
				lat.addWordData(start, end, form, tag);
			}
			start+=1;
			
		}
		return lat;
	}

}
