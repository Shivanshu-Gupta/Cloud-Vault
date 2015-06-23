package cloudsafe.database;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import cloudsafe.database.FileMetadata;
import cloudsafe.util.Pair;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Table {
	private HashMap<String, Pair<FileMetadata, Boolean>> table;
	long initialPosition;

	public Table() {
		table = new HashMap<String, Pair<FileMetadata, Boolean>>();
	}

	@SuppressWarnings("unchecked")
	public Table(byte[] databaseData) {
		try {
			ByteArrayInputStream fileIn = new ByteArrayInputStream(databaseData);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			table = (HashMap<String, Pair<FileMetadata, Boolean>>) in
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
			table = (HashMap<String, Pair<FileMetadata, Boolean>>) in
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

	public final int fileCount() {
		return table.size();
	}
	
	public final int hash() {
		return table.hashCode();
	}

	public final void addNewFile(String fileName, long fileSize) {
		FileMetadata meta = new FileMetadata(fileName, fileSize);
		table.put(fileName, Pair.of(meta, false));
	}

	public final void removeFile(String fileName) {
		table.remove(fileName);
	}

	public final boolean hasFile(String fileName) {
		return table.containsKey(fileName) && table.get(fileName) != null;
	}
	
	public final ArrayList<FileMetadata> getChildren(String parent) {
		Object[] fileNames = table.keySet().toArray();
		ArrayList<FileMetadata> childrenData = new ArrayList<FileMetadata>();
		Pattern p = Pattern.compile("^" + parent + "[^/]+" + "$");
		for (Object fileName : fileNames) {
			Matcher m = p.matcher((String) fileName);
			if (m.matches()) {
				childrenData.add(table.get((String) fileName).first);
			}
		}
		return childrenData;
	}

	public final boolean hasFileDated(String fileName, Date timestamp) {
		boolean contains = false;
		if (this.hasFile(fileName)) {
			FileMetadata file = table.get(fileName).first;
			if (timestamp.equals(file.timestamp())) {
				contains = true;
			}
		}
		return contains;
	}

	public final long fileSize(String fileName) {
		FileMetadata file = table.get(fileName).first;
		return file.fileSize;
	}

	public final int version(String file) {
		int version = 1;
		Object[] fileNames = table.keySet().toArray();
		if(table.containsKey(file)) {
			Pattern p = Pattern.compile("^" + file + " \\([0-9]+\\)$");
			for (Object fileName : fileNames) {
				Matcher m = p.matcher((String) fileName);
				if (m.matches()) {
					version++;
				}
			}
		} else {
			version = 0;
		}
		return version;
	}

	public final void lockFile(String fileName) {
		table.put(fileName, Pair.of(table.get(fileName).first, true));
	}

	public final void unlockFile(String fileName) {
		table.put(fileName, Pair.of(table.get(fileName).first, false));
	}

	public final void writeToFile(String databasePath) {
		try {
			FileOutputStream fileOut = new FileOutputStream(databasePath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(table);
			out.flush();
			fileOut.flush();
			out.close();
			fileOut.close();

		} catch (IOException x) {
			System.out.println("IOException: " + x);
			x.printStackTrace();
		}
	}

	public Object[] getFileList() {
		return table.keySet().toArray();
	}

	public FileMetadata getFileHistory(String fileName)
			throws FileNotFoundException {
		if (!table.containsKey(fileName) || table.get(fileName) == null) {
			throw new FileNotFoundException();
		} else {
			return table.get(fileName).first;
		}
	}

	public void printTable() {
		Object[] fileNames = table.keySet().toArray();
		for (Object fileName : fileNames) {
			System.out.println((String) fileName);
		}
	}
}
