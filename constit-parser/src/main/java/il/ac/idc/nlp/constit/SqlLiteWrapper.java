package il.ac.idc.nlp.constit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.ISqlJetIndexDef;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

public class SqlLiteWrapper {

	public static boolean IN_MEMORY_DB = false; // new
												// Boolean(System.getProperty("in_memory",
												// "true"));

	private final Object[] _lock = new Object[0];

	private SqlJetDb db;

	private SqlLiteWrapper(SqlJetDb db) {
		this.db = db;
		instances.add(this);
	}

	private static final Set<SqlLiteWrapper> instances = Collections.synchronizedSet(new HashSet<SqlLiteWrapper>());

	private static SqlJetDb cacheDb(String filename) throws SqlJetException {
		if (!IN_MEMORY_DB)
			return SqlJetDb.open(new File(filename), false);
		System.err.println("Caching lex db");
		SqlJetDb src = SqlJetDb.open(new File(filename), false);
		SqlJetDb inMemory = SqlJetDb.open(SqlJetDb.IN_MEMORY, true);
		src.beginTransaction(SqlJetTransactionMode.READ_ONLY);
		for (String tableName : src.getSchema().getTableNames()) {
			ISqlJetTable table = src.getTable(tableName);
			inMemory.createTable(table.getDefinition().toSQL());
			for (ISqlJetIndexDef idx : table.getIndexesDefs()) {
				inMemory.createIndex(idx.toSQL());
			}

			ISqlJetCursor cur = table.open();
			ISqlJetTable dstTable = inMemory.getTable(tableName);
			inMemory.beginTransaction(SqlJetTransactionMode.WRITE);
			if (!cur.eof()) {
				do {
					Object[] values = new Object[cur.getFieldsCount()];
					for (int i = 0; i < cur.getFieldsCount(); i++) {
						values[i] = cur.getValue(i);
					}
					dstTable.insert(values);
				} while (cur.next());
			}
			inMemory.commit();
		}
		src.commit();
		src.close();
		System.err.println("Done caching");
		return inMemory;
	}

	public static SqlLiteWrapper connect(String filename) throws SqlJetException {
		return new SqlLiteWrapper(cacheDb(filename));
	}

	public void disconnect() throws SqlJetException {
		db.close();
		db = null;
	}

	public static void disconnectAll() throws SqlJetException {
		for (SqlLiteWrapper w : instances) {
			w.disconnect();
		}
	}

	public String[] searchLemma(String word, String anals) throws SqlJetException {
		synchronized (_lock) {

			db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			try {
				ISqlJetTable table = db.getTable("lemlex");

				ISqlJetCursor cursor = table.lookup("lemlex_idx", word);
				List<String> res = new ArrayList<>();
				if (!cursor.eof()) {
					do {
						String cAnals = cursor.getString("anal");
						if (cAnals.equals(anals))
							res.add(cursor.getString("lemma"));
					} while (cursor.next());
				}
				return res.toArray(new String[0]);
			} finally {
				db.commit();
			}
		}
	}

	public String[] searchAnals(String word) throws SqlJetException {
		// select anal from lemlex where word=?'
		synchronized (_lock) {
			db.beginTransaction(SqlJetTransactionMode.READ_ONLY);
			try {
				ISqlJetTable table = db.getTable("lemlex");

				ISqlJetCursor cursor = table.lookup("lemlex_idx", word);
				List<String> res = new ArrayList<>();
				if (!cursor.eof()) {
					do {
						res.add(cursor.getString("anal"));
					}

					while (cursor.next());
				}
				return res.toArray(new String[0]);
			} finally {
				db.commit();
			}
		}
	}

}
