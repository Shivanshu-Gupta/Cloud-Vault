package cloudsafe.database;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import cloudsafe.database.FileMetadata;
import cloudsafe.util.Pair;

import java.util.Date;

public class Table {
	private static HashMap<String, Pair<ArrayList<FileMetadata>, Boolean>> table;
	private final static String tablePath = "trials/table.ser";
	private static int tableFileSize;

	public Table() {
		try {
			table = new HashMap<String, Pair<ArrayList<FileMetadata>, Boolean>>();
			FileOutputStream fileOut = new FileOutputStream(tablePath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(table);
			out.flush();
			out.close();
			fileOut.flush();
			fileOut.close();
			File tableFile = new File(tablePath);
			tableFileSize = (int) tableFile.length();
		} catch (IOException x) {
			System.out.println("IOException: " + x);
			x.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public Table(byte[] databaseData) {
		try {
			ByteArrayInputStream fileIn = new ByteArrayInputStream(databaseData);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			table = (HashMap<String, Pair<ArrayList<FileMetadata>, Boolean>>) in
					.readObject();
			in.close();
			fileIn.close();
		} catch (IOException x) {
			System.out.println("IOException: " + x);
			x.printStackTrace();
		} catch (ClassNotFoundException cfe) {
			System.out.println("ClassNotFoundException: " + cfe);
			cfe.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public Table(String databasePath) {
		try {
			FileInputStream fileIn = new FileInputStream(databasePath);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			table = (HashMap<String, Pair<ArrayList<FileMetadata>, Boolean>>) in
					.readObject();
			in.close();
			fileIn.close();
		} catch (IOException x) {
			System.out.println("IOException: " + x);
			x.printStackTrace();
		} catch (ClassNotFoundException cfe) {
			System.out.println("ClassNotFoundException: " + cfe);
			cfe.printStackTrace();
		}
	}

	public final int tableFileSize() {
		return tableFileSize;
	}

	public final String tableFilePath() {
		return tablePath;
	}

	public final int fileCount() {
		return table.size();
	}

	public final int addNewFile(String fileName, Integer fileSize) {
		int version = 1;
		ArrayList<FileMetadata> fileVersions;
		if (!table.containsKey(fileName) || table.get(fileName) == null) {
			fileVersions = new ArrayList<FileMetadata>(1);
		} else {
			fileVersions = table.get(fileName).first;
			version = fileVersions.size() + 1;
		}
		FileMetadata meta = new FileMetadata(fileName, version, fileSize);
		fileVersions.add(meta);
		table.put(fileName, Pair.of(fileVersions, false));
		return version;
	}

	public final boolean hasFile(String fileName) {
		return table.containsKey(fileName) && table.get(fileName) != null;
	}
	
	public final boolean hasFileVersion(String fileName, int version){
		boolean contains = false;
		if(this.hasFile(fileName))
		{
			ArrayList<FileMetadata> fileVersions = table.get(fileName).first;
			contains = 0< version && version <= fileVersions.size(); 
		}
		return contains;
	}
	
	public final boolean hasFileDated(String fileName, Date timestamp){
		boolean contains = false;
		if(this.hasFile(fileName))
		{
			ArrayList<FileMetadata> fileVersions = table.get(fileName).first;
			for (int i = 0; i < fileVersions.size(); i++) {
				if (timestamp.equals(fileVersions.get(i).timestamp())) {
					contains = true;
					break;
				}
			} 
		}
		return contains;
	}

	public final Integer fileSize(String fileName, Date timestamp) {
		int fileSize = 0;
		ArrayList<FileMetadata> fileVersions = table.get(fileName).first;
		for (int i = 0; i < fileVersions.size(); i++) {
			if (timestamp.equals(fileVersions.get(i).timestamp())) {
				fileSize = fileVersions.get(i).fileSize();
				break;
			}
		}
		return fileSize;
	}

	public final Integer fileSize(String fileName, int version) {
		int fileSize = 0;
		ArrayList<FileMetadata> fileVersions = table.get(fileName).first;
		fileSize = fileVersions.get(version - 1).fileSize();
		System.out.println(fileSize);
		return fileSize;
	}

	public final void lockFile(String fileName) {
		table.put(fileName, Pair.of(table.get(fileName).first, true));
	}

	public final void unlockFile(String fileName) {
		table.put(fileName, Pair.of(table.get(fileName).first, false));
	}

	public final int updateTable(String databasePath) {
		int tableFileSize = 0;
		try {
			FileOutputStream fileOut = new FileOutputStream(databasePath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(table);
			out.close();
			fileOut.close();
			File tableFile = new File(databasePath);
			tableFileSize = (int) tableFile.length();
		} catch (IOException x) {
			System.out.println("IOException: " + x);
			x.printStackTrace();
		}
		return tableFileSize;
	}

	public void printHistory(String fileName) {
		if (!table.containsKey(fileName) || table.get(fileName) == null) {
			System.out.println("File not found");
		} else {
			ArrayList<FileMetadata> fileVersions = table.get(fileName).first;
			System.out.format("\t%-50s%-10s%-10s%-40s\n", "Name", "Version",
					"Size", "Last Modified");
			for (int i = 0; i < fileVersions.size(); i++) {
				System.out.println((i + 1) + ".\t"
						+ fileVersions.get(i).toString());
			}
		}
	}

	public void printTable() {
		Object[] fileNames = table.keySet().toArray();
		for (Object fileName : fileNames) {
			System.out.println((String) fileName);
		}
	}
}
