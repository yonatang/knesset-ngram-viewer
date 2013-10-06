package idd.nlp.ok.viewer;

import idc.nlp.ok.indexer.NgramCountDateIndexer;
import idc.nlp.ok.indexer.NgramCountDateIndexer.QueryItem;
import idc.nlp.ok.indexer.NgramCountDateIndexer.QueryOptions;
import idc.nlp.ok.indexer.NgramCountDateIndexer.QueryResolution;
import idc.nlp.ok.indexer.NgramCountPartyIndexer;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.message.Message;
import org.joda.time.DateTime;

@Log4j2
public class DataBean {

	public static final String TYPE_PARAM = "type";
	public static final String RES_PARAM = "res";
	public static final String NGRAMS_PARAM = "ngrams";
	public static final String FROM_PARAM = "range-from";
	public static final String TO_PARAM = "range-to";
	public static final String NON_PARTY_PARAM = "non-party";

	public static Charset utf8 = Charset.forName("UTF-8");
	public static Charset win1255 = Charset.forName("WINDOWS-1255");
	public static Charset iso = Charset.forName("ISO-8859-1");
	
	public static enum Type {
		DATE, PARTY, PERSON
	}

	public static final DataBean instance = new DataBean();

	private final NgramCountDateIndexer ngramCountDateIndexer;
	private final NgramCountPartyIndexer ngramCountPartyIndexer;

	private DataBean() {
		String dbUrl=System.getProperty("h2.url", "tcp://localhost/~/knesset");
		log.info("Initiating, with URL {0} ",dbUrl);
		ngramCountDateIndexer = new NgramCountDateIndexer("jdbc:h2:"+dbUrl);
		ngramCountPartyIndexer = new NgramCountPartyIndexer("jdbc:h2:"+dbUrl);
	}

	private class Encoder {
		Charset encode;

		private Encoder(HttpServletRequest req) {
			String charset = req.getCharacterEncoding();
			if (charset == null)
				charset = "ISO-8859-1";
			System.out.println("Charset is "+charset);
			encode = Charset.forName(charset);
		}

		public String encode(String in) {
			System.out.println("Encoding raw input "+in+" [or "+URLEncoder.encode(in)+"] using "+encode+" to "+utf8);
			return utf8.decode(encode.encode(in)).toString();
		}
	}

	public String getDataJson(HttpServletRequest req) throws SQLException {
		try {
			Encoder encoder = new Encoder(req);
			String typeStr = req.getParameter(TYPE_PARAM);
			if (typeStr == null)
				return "[]";
			Type type = Type.valueOf(typeStr.toUpperCase());
			if (type == null)
				return "[]";

			String[] ngrams = StringUtils.split(req.getParameter(NGRAMS_PARAM), ',');
			if (ngrams == null)
				ngrams = new String[0];
			for (int i = 0; i < ngrams.length; i++) {
				ngrams[i] = encoder.encode(ngrams[i]);
			}
			String fromDate = req.getParameter(FROM_PARAM);
			String toDate = req.getParameter(TO_PARAM);
			boolean nonParty = "on".equals(req.getParameter(NON_PARTY_PARAM));
			String resStr = req.getParameter(RES_PARAM);
			System.out.println("Parameters: fromDate " + fromDate + ", toDate " + toDate + ", ngarms "
					+ Arrays.toString(ngrams));
			switch (type) {
			case DATE:
				if (resStr == null)
					return "[]";
				QueryResolution res = QueryResolution.valueOf(resStr.toUpperCase());
				if (res == null)
					return "[]";
				return getDateJson(ngrams, res);
			case PARTY:
				return getPartyJson(ngrams, fromDate, toDate, nonParty);
			case PERSON:
				return getPersonJson(ngrams, fromDate, toDate);
			default:
				return "[]";
			}
		} catch (SQLException e) {
			e.printStackTrace();
			throw e;
		}
	}

	// https://code.google.com/apis/ajax/playground/?type=visualization#annotated_time_line
	private String getDateJson(String[] ngrams, QueryResolution res) throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("[['Date'");
		for (String ngram : ngrams) {
			sb.append(",'").append(StringEscapeUtils.escapeEcmaScript(ngram.trim())).append('\'');
		}
		sb.append("]");

		List<Map<Date, QueryItem>> results = new ArrayList<>();
		SortedSet<Date> dates = new TreeSet<>();
		Map<Date, List<Double>> output = new HashMap<>();
		for (String ngram : ngrams) {
			List<QueryItem> result = ngramCountDateIndexer.query(new QueryOptions(ngram.trim(), res));
			Map<Date, QueryItem> m = new HashMap<>();
			for (QueryItem qi : result) {
				m.put(qi.getDate(), qi);
				dates.add(qi.getDate());
				if (!output.containsKey(qi.getDate())) {
					output.put(qi.getDate(), new ArrayList<Double>());
				}
			}
			results.add(m);
		}

		for (Map<Date, QueryItem> result : results) {
			for (Date date : dates) {
				if (result.containsKey(date)) {
					output.get(date).add(result.get(date).getVal());
				} else {
					output.get(date).add(0d);
				}
			}
		}

		for (Date date : dates) {
			DateTime dt=new DateTime(date);
			sb.append(",[ new Date(").append(dt.getYear()).append(',').append(dt.getMonthOfYear()-1).append(',').append(dt.getDayOfMonth()).append(')');
			for (Double val : output.get(date)) {
				sb.append(',').append(val);
			}
			sb.append("]");
		}
		sb.append("]");
		return sb.toString();

	}

	public String getPartyJson(String[] ngrams, String fromDateStr, String toDateStr, boolean nonParty)
			throws SQLException {
		Date fromDate = null;
		Date toDate = null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		try {
			fromDate = sdf.parse(fromDateStr);
		} catch (Exception e) {
		}
		try {
			toDate = sdf.parse(toDateStr);
		} catch (Exception e) {
		}
		StringBuilder sb = new StringBuilder();

		List<Map<String, NgramCountPartyIndexer.QueryItem>> results = new ArrayList<>();
		SortedSet<String> parties = new TreeSet<>();
		Map<String, List<Double>> output = new HashMap<>();
		sb.append("[['מפלגה'");
		for (String ngram : ngrams) {
			sb.append(",'").append(StringEscapeUtils.escapeEcmaScript(ngram.trim())).append('\'');

			List<NgramCountPartyIndexer.QueryItem> result = ngramCountPartyIndexer
					.query(new NgramCountPartyIndexer.QueryOptions(NgramCountPartyIndexer.QueryOptions.Type.PARTY,
							ngram, fromDate, toDate, nonParty));
			Map<String, NgramCountPartyIndexer.QueryItem> m = new HashMap<>();
			for (NgramCountPartyIndexer.QueryItem qi : result) {
				m.put(qi.getParty(), qi);
				parties.add(qi.getParty());
				if (!output.containsKey(qi.getParty())) {
					output.put(qi.getParty(), new ArrayList<Double>());
				}
			}
			results.add(m);
		}
		sb.append("]");

		for (Map<String, NgramCountPartyIndexer.QueryItem> result : results) {
			for (String party : parties) {
				if (result.containsKey(party)) {
					output.get(party).add(result.get(party).getVal());
				} else {
					output.get(party).add(0d);
				}
			}
		}

		for (String party : parties) {
			sb.append(",['").append(StringEscapeUtils.escapeEcmaScript(party)).append('\'');
			for (Double val : output.get(party)) {
				sb.append(',').append(val.intValue());
			}
			sb.append("]");
		}
		sb.append("]");
		return sb.toString();
	}

	public String getPersonJson(String[] ngrams, String fromDateStr, String toDateStr) throws SQLException {
		Date fromDate = null;
		Date toDate = null;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		try {
			fromDate = sdf.parse(fromDateStr);
		} catch (Exception e) {
		}
		try {
			toDate = sdf.parse(toDateStr);
		} catch (Exception e) {
		}
		StringBuilder sb = new StringBuilder();

		List<Map<String, NgramCountPartyIndexer.QueryItem>> results = new ArrayList<>();
		SortedSet<String> parties = new TreeSet<>();
		Map<String, List<Double>> output = new HashMap<>();
		sb.append("[['אדם'");
		for (String ngram : ngrams) {
			sb.append(",'").append(StringEscapeUtils.escapeEcmaScript(ngram.trim())).append('\'');

			List<NgramCountPartyIndexer.QueryItem> result = ngramCountPartyIndexer
					.query(new NgramCountPartyIndexer.QueryOptions(NgramCountPartyIndexer.QueryOptions.Type.PERSON,
							ngram, fromDate, toDate, false));
			Map<String, NgramCountPartyIndexer.QueryItem> m = new HashMap<>();
			for (NgramCountPartyIndexer.QueryItem qi : result) {
				m.put(qi.getParty(), qi);
				parties.add(qi.getParty());
				if (!output.containsKey(qi.getParty())) {
					output.put(qi.getParty(), new ArrayList<Double>());
				}
			}
			results.add(m);
		}
		sb.append("]");

		for (Map<String, NgramCountPartyIndexer.QueryItem> result : results) {
			for (String party : parties) {
				if (result.containsKey(party)) {
					output.get(party).add(result.get(party).getVal());
				} else {
					output.get(party).add(0d);
				}
			}
		}

		for (String party : parties) {
			sb.append(",['").append(StringEscapeUtils.escapeEcmaScript(party)).append('\'');
			for (Double val : output.get(party)) {
				sb.append(',').append(val.intValue());
			}
			sb.append("]");
		}
		sb.append("]");
		return sb.toString();
	}

}
