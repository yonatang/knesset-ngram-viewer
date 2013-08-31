package idc.nlp.ok.indexer;

import idc.nlp.ok.model.Paragraph;
import idc.nlp.ok.model.Part;
import idc.nlp.ok.model.Protocol;
import idc.nlp.ok.model.Sentence;

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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

public class NgramCountDateIndexer extends
		AbsIndexer<Map<Integer, Multiset<String>>, NgramCountDateIndexer.QueryOptions, NgramCountDateIndexer.QueryItem> {

	public NgramCountDateIndexer(String dbConnectionString) {
		super(dbConnectionString);
	}

	protected String tableName() {
		return "NGRAM_COUNTER";
	}

	protected String createStatement() {
		return "ID INTEGER auto_increment, NGRAM CHAR(255) NOT NULL, NGRAM_SIZE INTEGER, NGRAM_COUNT INTEGER NOT NULL, TOTAL INTEGER NOT NULL, NGRAM_DATE DATE NOT NULL, PRIMARY KEY (ID)";
	}

	protected String[] createIndex() {
		return new String[] { "CREATE INDEX NGRAM_NAME_IDX ON PUBLIC." + tableName() + "(NGRAM)" };
	}

	public static enum QueryResolution {
		YEAR, MONTH, WEEK, DAY
	}

	@Getter
	@ToString
	public class QueryItem extends AbsQueryItem{
		public QueryItem(String ngram, double val, Date date){
			super(ngram,val);
			this.date=date;
		}
		private Date date;

	}

	@EqualsAndHashCode(callSuper=true)
	public static class QueryOptions extends AbsQueryOptions {
		public QueryOptions(String ngram, QueryResolution resolution) {
			super(ngram);
			this.resolution = resolution;
		}

		@Getter
		private QueryResolution resolution;
	}

	private DateTime normDate(DateTime dt, QueryResolution res) {
		switch (res) {
		case WEEK:
			return dt.minusDays(dt.getDayOfWeek() % 7).minusMillis(dt.getMillisOfDay());
		case MONTH:
			return dt.minusDays(dt.getDayOfMonth() - 1).minusMillis(dt.getMillisOfDay());
		case YEAR:
			return dt.minusDays(dt.getDayOfYear() - 1).minusMillis(dt.getMillisOfDay());
		default:
			return dt.minusMillis(dt.getMillisOfDay());
		}
	}

	protected List<QueryItem> queryImpl(QueryOptions options) throws SQLException {
		String ngram = NO_SUFFIX_FUNCTION.apply(options.getNgram());
		QueryResolution res = options.getResolution();
		try (Connection conn = getConnection()) {
			String query = "SELECT NGRAM, NGRAM_COUNT,TOTAL,NGRAM_DATE FROM " + tableName() + " WHERE NGRAM = ?";
			System.out.println("Executing query " + query);
			System.out.println("Params " + ngram);
			try (PreparedStatement stmt = conn.prepareStatement(query);) {
				stmt.setString(1, ngram);
				List<QueryItem> result = new ArrayList<>();
				try (ResultSet rs = stmt.executeQuery();) {
					int agrCount = 0;
					int agrTotal = 0;
					DateTime agrDate = null;
					while (rs.next()) {
						DateTime recDate = new DateTime(rs.getDate(4));
						int recCount = rs.getInt(2);
						int recTotal = rs.getInt(3);

						if (agrDate == null) {
							agrDate = normDate(recDate, res);

						}
						boolean newAgr = false;
						switch (res) {
						case WEEK:
							newAgr = recDate.getWeekOfWeekyear() != agrDate.getWeekOfWeekyear()
									|| recDate.getWeekyear() == agrDate.getWeekyear();
							break;
						case MONTH:
							newAgr = recDate.getMonthOfYear() != agrDate.getMonthOfYear()
									|| recDate.getYear() != agrDate.getYear();
							break;
						case YEAR:
							newAgr = recDate.getYear() != agrDate.getYear();
							break;
						case DAY:
							newAgr = recDate.getDayOfYear() != agrDate.getDayOfYear()
									|| recDate.getYear() != agrDate.getYear();
							break;
						}
						if (newAgr) {
							QueryItem qi = new QueryItem(ngram,
									(agrTotal != 0) ? ((double) agrCount / (double) agrTotal) : 0, agrDate.toDate());
							result.add(qi);
							agrCount = 0;
							agrTotal = 0;
							agrDate = normDate(recDate, res);
						}

						agrCount += recCount;
						agrTotal += recTotal;
					}
					if (agrDate != null) {
						QueryItem qi = new QueryItem(ngram, (agrTotal != 0) ? ((double) agrCount / (double) agrTotal)
								: 0, agrDate.toDate());
						result.add(qi);
					}
				}
				return result;
			}

		}
	}

	protected void flushProtocolCounter(Map<Integer, Multiset<String>> dateCounter, Connection conn, Date date)
			throws SQLException {
		try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO " + tableName()
				+ " (NGRAM, NGRAM_SIZE, NGRAM_COUNT,TOTAL,NGRAM_DATE) VALUES(?, ?, ?, ?, ?)");) {
			conn.setAutoCommit(false);
			for (Integer size : dateCounter.keySet()) {
				Multiset<String> counter = dateCounter.get(size);
				for (Entry<String> entry : counter.entrySet()) {
					stmt.setString(1, entry.getElement());
					stmt.setInt(2, size);
					stmt.setInt(3, entry.getCount());
					stmt.setInt(4, counter.size());
					stmt.setDate(5, new java.sql.Date(date.getTime()));
					if (StringUtils.length(entry.getElement())>255){
						System.out.println("N-Gram "+entry.getElement()+" too long");
						continue;
					}
					stmt.executeUpdate();
				}
			}
			conn.commit();
		}
	}

	private void addToIdx(Map<Integer, Multiset<String>> dateCounter, Multiset<String> count) {
		for (Entry<String> ngram : count.entrySet()) {
			int size = StringUtils.countMatches(ngram.getElement(), " ") + 1;
			if (!dateCounter.containsKey(size)) {
				dateCounter.put(size, HashMultiset.<String> create());
			}
			dateCounter.get(size).add(ngram.getElement(), ngram.getCount());
		}
	}

	protected void processDocument(NGramCounter counter, Map<Integer, Multiset<String>> dateCounter, Protocol protocol) {
		for (Part part : protocol.getParts()) {
			for (Paragraph paragraph : part.getParagraphs()) {
				for (Sentence sentence : paragraph.getSentences()) {
					if (sentence.getMorphemes() != null && !sentence.getMorphemes().isEmpty()) {
						Multiset<String> count = counter.count(sentence.getMorphemes(), NO_SUFFIX_FUNCTION);
						addToIdx(dateCounter, count);
					}
				}
			}

		}
	}

	@Override
	protected Map<Integer, Multiset<String>> createContext() {
		return new HashMap<>();
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
		NgramCountDateIndexer n = new NgramCountDateIndexer("jdbc:h2:tcp://localhost/~/tmp");
		n.index(counter, new File("../nlp-fp-cruncher/data/plenums"));

	}
}
