package cloudsafe.database;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.Setup;

//a singleton class to ensure a single connection to the files database.
public class TempDatabase {
	private final static Logger logger = LogManager.getLogger(Setup.class
			.getName());
	private static Connection c = null;
	private static TempDatabase db = null;
	
	public static final String DBNAME = "vault.db";
	public static final String FILENAME = "cloudFileName";
	public static final String SIZE = "size";
	public static final String CLOUDLIST = "cloudList";
	public static final String MINCLOUDS = "minClouds";
	public static final String TIMESTAMP = "timeStamp";
	public static final String FILES_TABLE = "fileInfo";
	
	private static String dbPath = null;
	
	private TempDatabase() {
		try {
			Class.forName("org.sqlite.JDBC");

			// temporary location for the files database
			// need to create a path independent version after
			// the application has been made installable.
			String DBPath = "trials/config/" + DBNAME;
			c = DriverManager.getConnection("jdbc:sqlite:" + DBPath);
			c.setAutoCommit(false);
			logger.info("Opened Files Database Successfully");
			Statement stmt = c.createStatement();
			// TODO : ensure that the table in android version is the same format
			String createTable = "CREATE TABLE IF NOT EXISTS " + FILES_TABLE
					+ " (" + FILENAME + " TEXT NOT NULL, " + SIZE
					+ " BIGINT NOT NULL, " + CLOUDLIST + " TEXT NOT NULL, " + MINCLOUDS
					+ " INT NOT NULL, " + TIMESTAMP + " TIMESTAMP)";
			stmt.executeUpdate(createTable);
			stmt.close();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// synchronized to ensure that only one thread may call getInstance() at a
	// time
	public synchronized static TempDatabase getInstance() {
		try {
			if (db == null) {
				db = new TempDatabase();
			} else if(c.isClosed()) {
				String DBPath = "trials/config/" + DBNAME;
				c = DriverManager.getConnection("jdbc:sqlite:" + DBPath);
				c.setAutoCommit(false);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return db;
	}

	// Only for debugging purposes
	// required for multiple installations
	private TempDatabase(String dbPath) {
		try {
			Class.forName("org.sqlite.JDBC");
//			String DBPath = configPath + "/" + DBNAME;
			c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
			c.setAutoCommit(false);
			logger.info("Opened Files Database Successfully");
			Statement stmt = c.createStatement();
			// TODO : ensure that the table in android version is the same format
			String createTable = "CREATE TABLE IF NOT EXISTS " + FILES_TABLE
					+ " (" + FILENAME + " TEXT PRIMARY KEY NOT NULL, " + SIZE
					+ " BIGINT NOT NULL, " + CLOUDLIST + " TEXT NOT NULL, " + MINCLOUDS
					+ " INT NOT NULL, " + TIMESTAMP + " TIMESTAMP)";
			stmt.executeUpdate(createTable);
			stmt.close();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	// synchronized to ensure that only one thread may call getInstance() at a
	// time
	public synchronized static TempDatabase getInstance(String dbPath) throws SQLException {
		dbPath = Paths.get(System.getProperty("user.dir")).relativize(Paths.get(dbPath)).toString();
		TempDatabase.dbPath = dbPath;
		logger.debug("Connecting to database: " + dbPath);
		if (db == null) {
			db = new TempDatabase(dbPath);
		} else if(c.isClosed()) {
			c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
			c.setAutoCommit(false);
		}
		return db;
	}
	
	public void insertFileRecord(String fileName, long fileSize,
			String cloudList, int minClouds, String timestamp) throws SQLException {
		FileMetadata file = new FileMetadata(fileName, fileSize, cloudList, minClouds, timestamp);
		insertFileRecord(file);
	}
	
	public void insertFileRecord(FileMetadata file) throws SQLException {
		String insertStmt = "INSERT INTO " + FILES_TABLE + " VALUES (?,?,?,?,?);";
		PreparedStatement prep = null;
		try {
			c.setAutoCommit(false);
			prep = c.prepareStatement(insertStmt);
			prep.setString(1, file.fileName);
			prep.setLong(2, file.fileSize);
			prep.setString(3, file.cloudList);
			prep.setInt(4, file.minClouds);
			prep.setString(5, file.timestamp);
			prep.executeUpdate();
			c.commit();
			prep.close();
		} catch (SQLException e) {
			logger.error("Could not insert record into the Files Database : " + file.toString(), e);
			e.printStackTrace();
			c.rollback();
		} finally {
			if(prep != null) {
				prep.close();
			}
		}
	}

	
	public void updateFileRecord(FileMetadata file) throws SQLException {		
		String updateStmt = "UPDATE " + FILES_TABLE + "SET " + SIZE + "=? "
				+ CLOUDLIST + "=? " + MINCLOUDS + "=? " + TIMESTAMP + "=? "
				+ "WHERE " + FILENAME + "=?;";
		PreparedStatement prep = null;
		try {
			c.setAutoCommit(false);
			prep = c.prepareStatement(updateStmt);
			prep.setString(5, file.fileName);
			prep.setLong(1, file.fileSize);
			prep.setString(2, file.cloudList);
			prep.setInt(3, file.minClouds);
			prep.setString(4, file.timestamp);
			prep.executeUpdate();
			c.commit();
			prep.close();
		} catch (SQLException e) {
			logger.error("Could not update record in the Files Database : " + file.toString(), e);
			e.printStackTrace();
			c.rollback();
		} finally {
			if(prep != null) {
				prep.close();
			}
		}
	}
	
	public void insertFileRecords(String tableName, ArrayList<FileMetadata> files) throws SQLException {
		String insertStmt = "INSERT INTO " + FILES_TABLE + " VALUES (?,?,?,?,?);";
		PreparedStatement prep = null;
		try {
			prep = c.prepareStatement(insertStmt);
		} catch (SQLException e) {
			logger.error("Could not insert records into the Files Database", e);
//			e.printStackTrace();
			return;
		}
		c.setAutoCommit(false);
		for(FileMetadata file : files) {
			try {
				prep.setString(1, file.fileName);
				prep.setLong(2, file.fileSize);
				prep.setString(3, file.cloudList);
				prep.setInt(4, file.minClouds);
				prep.setString(5, file.timestamp);
				prep.executeUpdate();
				c.commit();
			} catch (SQLException e) {
				logger.error("Could not insert record into the Files Database : " + file.toString(), e);
				e.printStackTrace();
				c.rollback();
			}
		}
		if(prep != null) {
			prep.close();
		} 
	}

	public void removeFileRecord(String fileName) throws SQLException {
		String deleteStmt = "DELETE FROM " + FILES_TABLE + " WHERE " + FILENAME + " = ?;";
		PreparedStatement prep = null;
		try {
			c.setAutoCommit(false);
			prep = c.prepareStatement(deleteStmt);
			prep.setString(1, fileName);
			prep.executeUpdate();
			c.commit();
			
		} catch (Exception e) {
			logger.error("Could not delete file from Files Database : " + fileName, e);
			e.printStackTrace();
			c.rollback();
		} finally {
			if(prep != null) {
				prep.close();
			}
		}
	}
	
	public HashSet<FileMetadata> getFileRecords(String fileName) throws SQLException {
		String selectStmt = "SELECT * FROM " + FILES_TABLE + " WHERE "
				+ FILENAME + " = '" + fileName + "';";
//		logger.debug("query: " + selectStmt);
		Statement stmt = c.createStatement();
		ResultSet rs = stmt.executeQuery(selectStmt);
		HashSet<FileMetadata> files = new HashSet<FileMetadata>();
		while (rs.next()) {
			files.add(new FileMetadata(rs.getString(FILENAME),
					rs.getLong(SIZE), rs.getString(CLOUDLIST), rs
							.getInt(MINCLOUDS), rs.getString(TIMESTAMP)));
		}
		stmt.close();
		return files;
	}

	public HashSet<FileMetadata> getTableContents(String tableName) throws SQLException {
		Statement stmt = c.createStatement();
		ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + ";");
		HashSet<FileMetadata> files = new HashSet<FileMetadata>();
		while (rs.next()) {
			files.add(new FileMetadata(rs.getString(FILENAME),
					rs.getLong(SIZE), rs.getString(CLOUDLIST), rs
							.getInt(MINCLOUDS), rs.getString(TIMESTAMP)));
		}
		stmt.close();
		return files;
	}
	
	public void close() {
		try {
			// commit any changes that may not have been committed yet
			c.commit();
			c.close();
//			Files.delete(Paths.get(dbPath));
		} catch (SQLException e) {
			e.printStackTrace();
		}
//		catch (IOException e) {
//			e.printStackTrace();
//		}
	}
}
