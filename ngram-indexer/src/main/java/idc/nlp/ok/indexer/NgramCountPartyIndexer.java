package idc.nlp.ok.indexer;

import idc.nlp.ok.indexer.NgramCountPartyIndexer.QueryOptions.Type;
import idc.nlp.ok.model.Paragraph;
import idc.nlp.ok.model.Part;
import idc.nlp.ok.model.Protocol;
import idc.nlp.ok.model.Role;
import idc.nlp.ok.model.Sentence;
import idc.nlp.ok.model.Speaker;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

public class NgramCountPartyIndexer
		extends
		AbsIndexer<Map<Speaker, Multiset<String>>, NgramCountPartyIndexer.QueryOptions, NgramCountPartyIndexer.QueryItem> {
	public NgramCountPartyIndexer(String dbConnStr) {
		super(dbConnStr);
	}

	@lombok.Getter
	@lombok.EqualsAndHashCode(callSuper = true)
	public static class QueryOptions extends AbsQueryOptions {
		public QueryOptions(Type type, String ngram, Date from, Date to, boolean nonParty) {
			super(ngram);
			this.from = from;
			this.to = to;
			this.nonParty = nonParty;
			this.type = type;
		}

		public static enum Type {
			PERSON, PARTY
		}

		private Type type;
		private Date from;
		private Date to;
		private boolean nonParty;
	}

	@lombok.Getter
	@lombok.ToString(callSuper = true)
	public class QueryItem extends AbsQueryItem {
		private String party;

		public QueryItem(String ngram, double val, String party) {
			super(ngram, val);
			this.party = party;
		}
	}

	@Override
	protected String tableName() {
		return "NGRAM_PARTY";
	}

	@Override
	protected String createStatement() {
		return "ID INTEGER auto_increment, NGRAM CHAR(255) NOT NULL, NGRAM_SIZE INTEGER, PARTY CHAR(100), PERSON CHAR(100), IS_NON_PARTY BOOLEAN, NGRAM_COUNT INTEGER NOT NULL, NGRAM_DATE DATE NOT NULL, PRIMARY KEY (ID)";
	}

	@Override
	protected String[] createIndex() {
		return new String[] { "CREATE INDEX NGRAM_PARTY_NAME_IDX ON PUBLIC.NGRAM_PARTY (NGRAM)",
				"CREATE INDEX NGRAM_PARTY_NAME_PERSON_IDX ON PUBLIC.NGRAM_PARTY (NGRAM,PERSON)" };
	}

	@Override
	protected Map<Speaker, Multiset<String>> createContext() {
		return new HashMap<>();
	}

	@Override
	protected void processDocument(NGramCounter counter, Map<Speaker, Multiset<String>> context, Protocol protocol) {
		for (Part part : protocol.getParts()) {
			for (Paragraph paragraph : part.getParagraphs()) {
				for (Sentence sentence : paragraph.getSentences()) {
					if (sentence.getMorphemes() != null && !sentence.getMorphemes().isEmpty()) {
						Multiset<String> count = counter.count(sentence.getMorphemes(), NO_SUFFIX_FUNCTION);
						Speaker speaker = part.getSpeaker();
						addToIdx(context, count, speaker);
					}
				}
			}

		}
	}

	private void addToIdx(Map<Speaker, Multiset<String>> context, Multiset<String> count, Speaker speaker) {
		if (!context.containsKey(speaker)) {
			context.put(speaker, HashMultiset.<String> create());
		}
		for (Entry<String> ngram : count.entrySet()) {
			context.get(speaker).add(ngram.getElement(), ngram.getCount());
		}
	}

	@Override
	protected void flushProtocolCounter(Map<Speaker, Multiset<String>> context, Connection conn, Date date)
			throws SQLException {
		try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO " + tableName()
				+ "(NGRAM, PARTY, PERSON, NGRAM_COUNT, NGRAM_DATE, IS_NON_PARTY, NGRAM_SIZE) VALUES (?,?,?,?,?,?,?)");) {
			conn.setAutoCommit(false);
			for (Speaker speaker : context.keySet()) {
				Role party = speaker.getRole();
				if (party == null)
					party = new Role("לא ידוע");
				Multiset<String> counter = context.get(speaker);
				for (Entry<String> entry : counter.entrySet()) {
					stmt.setString(1, entry.getElement());
					String partyName;
					String speakerName = speaker.getName();
					boolean isNonParty;
					if (party.getName().equals(Role.MK)) {
						partyName = party.getSecondary();
						isNonParty = false;
					} else {
						partyName = party.getName();
						isNonParty = true;
					}
					if (StringUtils.length(partyName)>100){
						System.out.println("Party name "+partyName+" too long");
						continue;
					}
					if (StringUtils.length(speakerName)>100){
						System.out.println("Speaker name "+speakerName+" too long");
						continue;
					}
					if (StringUtils.length(entry.getElement())>255){
						System.out.println("N-Gram "+entry.getElement()+" too long");
						continue;
					}
					stmt.setString(2, partyName);
					stmt.setString(3, speakerName);
					stmt.setInt(4, entry.getCount());
					stmt.setDate(5, new java.sql.Date(date.getTime()));
					stmt.setBoolean(6, isNonParty);
					stmt.setInt(7, StringUtils.countMatches(entry.getElement(), " ") + 1);
					stmt.executeUpdate();
				}
			}
			conn.commit();
		}

	}

	@Override
	protected List<QueryItem> queryImpl(QueryOptions options) throws SQLException {
		String ngram = NO_SUFFIX_FUNCTION.apply(options.getNgram()).trim();
		Date from = options.getFrom();
		Date to = options.getTo();
		try (Connection conn = getConnection()) {
			StringBuilder qb = new StringBuilder();
			if (options.type == Type.PARTY)
				qb.append("SELECT NGRAM, PARTY, SUM(NGRAM_COUNT) FROM ");

			else
				qb.append("SELECT NGRAM, PERSON, SUM(NGRAM_COUNT) FROM ");
			qb.append(tableName()).append(" WHERE NGRAM = ?");

			if (from != null) {
				qb.append(" AND NGRAM_DATE >= ?");
			}
			if (to != null) {
				qb.append(" AND NGRAM_DATE <= ?");
			}
			if (!options.nonParty && options.type == Type.PARTY) {
				qb.append(" AND IS_NON_PARTY = FALSE");
			}
			if (options.type == Type.PARTY)
				qb.append(" GROUP BY PARTY");
			else {
				qb.append(" GROUP BY PERSON");
			}

			String query = qb.toString();
			System.out.println("Query: " + query.toString());
			try (PreparedStatement stmt = conn.prepareStatement(query)) {
				int idx = 1;
				stmt.setString(idx++, ngram);
				if (from != null)
					stmt.setDate(idx++, new java.sql.Date(from.getTime()));
				if (to != null) {
					stmt.setDate(idx++, new java.sql.Date(to.getTime()));
				}
				try (ResultSet rs = stmt.executeQuery();) {
					List<QueryItem> result = new ArrayList<>();
					while (rs.next()) {
						result.add(new QueryItem(rs.getString(1), rs.getInt(3), rs.getString(2)));
					}
					return result;
				}
			}

		}
	}

	public static void main(String... args) throws Exception {
		Set<String> skip = new HashSet<>();
		skip.add("IN");
		skip.add("CC");
		skip.add("AT");
		skip.add("H");
		skip.add("REL");
		skip.add("COM");

		NGramCounter counter = new NGramCounter(null, skip, 3);
		NgramCountPartyIndexer n = new NgramCountPartyIndexer("jdbc:h2:tcp://localhost/~/tmp");
		n.index(counter, new File("../nlp-fp-cruncher/data/plenums"));
		// n.index(counter, new File("data/indexer"));
		// System.out.println(n.query(new QueryOptions("ריקי כהן", null,null)));

	}
}
