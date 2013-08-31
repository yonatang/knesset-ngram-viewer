package idc.nlp.ok.indexer;

import idc.nlp.ok.model.Protocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Function;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;

public abstract class AbsIndexer<CTX, O extends AbsQueryOptions, QR extends AbsQueryItem> {

	private final Cache<O,List<QR>> CACHE=CacheBuilder.newBuilder().build();
	
	private String dbConnectionString;

	public AbsIndexer(String dbConnStr) {
		this.dbConnectionString = dbConnStr;
		System.out.println("Connection string is "+dbConnectionString);
	}

	public String getConnStr() {
		return dbConnectionString;
	}

	protected abstract String tableName();

	protected abstract String createStatement();

	protected abstract String[] createIndex();

	protected static final Function<String, String> NO_SUFFIX_FUNCTION = new Function<String, String>() {
		@Override
		public String apply(String input) {
			return StringUtils.replaceChars(input, "ךםןףץ", "כמנפצ");
		}
	};

	protected Connection getConnection() throws SQLException {
		return DriverManager.getConnection(dbConnectionString);
	}

	protected void initiateDb(Connection conn) throws SQLException {
		try (ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, tableName(), null);) {
			if (rs.next()) {
				System.out.println("Droping " + tableName());
				try (Statement stmt = conn.createStatement();) {
					stmt.execute("DROP TABLE PUBLIC." + tableName());
				}
			}
		}
		System.out.println("Creating " + tableName());
		try (Statement stmt = conn.createStatement();) {
			stmt.execute("CREATE TABLE PUBLIC." + tableName() + " (" + createStatement() + ")");
		}
	}

	protected abstract CTX createContext();

	public void index(NGramCounter counter, File dirToIndex) throws SQLException {
		long now = System.currentTimeMillis();
		try (Connection conn = DriverManager.getConnection(dbConnectionString);) {
			initiateDb(conn);
			Iterator<Protocol> protocols = getProtocolFiles(dirToIndex);
			while (protocols.hasNext()) {
				CTX context = createContext();
				// Map<Integer, Multiset<String>> dateCounter = new HashMap<>();
				Protocol protocol = protocols.next();
				processDocument(counter, context, protocol);
				flushProtocolCounter(context, conn, protocol.getDate());
			}
			if (createIndex() != null && createIndex().length > 0) {
				System.out.println("Creating index for " + tableName());
				for (String idxCreate : createIndex()) {
					try (Statement stmt = conn.createStatement()) {
						stmt.execute(idxCreate);
					}
				}
			}
		}
		long duration = System.currentTimeMillis() - now;
		System.out.println("Took " + TimeUnit.MILLISECONDS.toMinutes(duration) + " minutes to process the directory");
	}

	protected abstract void processDocument(NGramCounter counter, CTX context, Protocol protocol);

	protected abstract void flushProtocolCounter(CTX context, Connection conn, Date date) throws SQLException;

	public List<QR> query(O options) throws SQLException {
		List<QR> results=CACHE.getIfPresent(options);
		if (results==null){
			results=queryImpl(options);
			CACHE.put(options, results);
		}
		return results;
	}
	
	protected abstract List<QR> queryImpl(O options) throws SQLException;

	protected Iterator<Protocol> getProtocolFiles(File procotolsDir) {
		final Collection<File> protocolFiles = FileUtils.listFiles(procotolsDir, new String[] { "json" }, false);
		final Iterator<File> fileIterator = protocolFiles.iterator();
		final Gson gson = new Gson();
		Iterator<Protocol> iterator = new Iterator<Protocol>() {
			@Override
			public void remove() {
				fileIterator.remove();
			}

			@Override
			public Protocol next() {
				File file = fileIterator.next();
				System.out.println("Processing file " + file);
				try (FileInputStream fis=new FileInputStream(file);
						InputStreamReader fr=new InputStreamReader(fis,Charset.forName("UTF8"));) {
					return gson.fromJson(fr, Protocol.class);
				} catch (Exception e) {
					System.out.println("File " + file + " have a problem - " + e.getMessage());
					return new Protocol();
				}
			}

			@Override
			public boolean hasNext() {
				return fileIterator.hasNext();
			}
		};
		return iterator;
	}
}
