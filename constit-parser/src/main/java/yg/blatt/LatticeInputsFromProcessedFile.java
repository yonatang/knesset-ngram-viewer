package yg.blatt;

import pyutils.Generator;

import java.io.*;

import edu.berkeley.nlp.util.Numberer;

public class LatticeInputsFromProcessedFile extends Generator<LatticeInput> {

	private static Numberer _tagNumberer = Numberer.getGlobalNumberer("tags");

	BufferedReader _reader;
	TypesLexicon _tlex = null;
	int id = 0;

	public LatticeInputsFromProcessedFile(File filename, TypesLexicon tlex) throws IOException {
		this._reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(filename), "utf8"));
		this._tlex = tlex;
	}

	public LatticeInputsFromProcessedFile(BufferedReader reader, TypesLexicon tlex)
			throws IOException {
		this._reader = reader;
		this._tlex = tlex;
	}

	public LatticeInputsFromProcessedFile setLexicon(TypesLexicon tlex) {
		this._tlex = tlex;
		return this;
	}

	public void func() {
		PlainLatticeInput lat = new PlainLatticeInput();
		String line;
		try {
			while ((line = this._reader.readLine()) != null) {
				if (line.trim().isEmpty()) {
					if (lat.length() > 0) {
						this.id+=1;
						lat.setProperty("line", lat.stringRep());
						lat.setProperty("id", "sent"+this.id);
						yield(lat);
					}
					lat = new PlainLatticeInput();
				} else {
					String parts[] = line.trim().split("\\s+");
					int start = Integer.parseInt(parts[0]);
					int end = Integer.parseInt(parts[1]);
					if (!(end > start))
						continue; // try to avoid some input errors
					String form = parts[2];
					String tag = parts.length > 3 ? parts[3] : null;
               double w_t_prob = parts.length > 4 ? Float.valueOf(parts[4]) : -1.0;
               //if (tag!= null && !_tlex.allPossibleTags().contains(_tagNumberer.number(tag))) {
               //   System.err.println("unknown tag [" + tag + "] in lattice, allowing all tags for word");
               //   tag = null;
               //} else {
               //   System.err.println("KNOWN:"+tag);
               //}
					if (tag == null) {
						for (short t : _tlex.possibleNumericTagsForWord(form)) {
							lat.addWordData(start, end, form, t);
						}
					} else {
						lat.addWordData(start, end, form, tag, w_t_prob);
					}
				}
			}
		} catch (IOException e) {
			System.err.println("error reading file.");
			e.printStackTrace();
		}
		if (lat.length() > 0) {
			this.id+=1;
			lat.setProperty("line", lat.stringRep());
			lat.setProperty("id", "sent"+this.id);
			yield(lat);
		}
		return;
	}
}
