package cloudsafe.database;

import java.io.ByteArrayInputStream;
import java.io.File;
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

public class Table {
	private static HashMap<String, Pair<FileMetadata, Boolean>> table;
	static long initialPosition;

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

	// public final int addNewFile(String fileName, int parentVersion,
	// long fileSize) {
	// int version = 1;
	// FileMetadata fileVersions;
	// if (!table.containsKey(fileName) || table.get(fileName) == null) {
	// fileVersions = new FileMetadata(1);
	// } else {
	// fileVersions = table.get(fileName).first;
	// version = fileVersions.size() + 1;
	// }
	// FileMetadata meta = new FileMetadata(fileName, version, parentVersion,
	// fileSize);
	// fileVersions.add(meta);
	// table.put(fileName, Pair.of(fileVersions, false));
	// return version;
	// }

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

	// public final boolean hasFileVersion(String fileName, int version) {
	// boolean contains = false;
	// if (this.hasFile(fileName)) {
	// FileMetadata fileVersions = table.get(fileName).first;
	// }
	// return contains;
	// }

	public final ArrayList<FileMetadata> getChildren(String parent) {
		Object[] fileNames = table.keySet().toArray();
		ArrayList<FileMetadata> childrenData = new ArrayList<FileMetadata>();
		for (Object fileName : fileNames) {
			if (((String) fileName).startsWith(parent)) {
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
		int version = 0;
		Object[] fileNames = table.keySet().toArray();
		for (Object fileName : fileNames) {
			if (((String) fileName).startsWith(file + " (")) {
				version++;
			}
		}
		return version;
	}

	public final void lockFile(String fileName) {
		table.put(fileName, Pair.of(table.get(fileName).first, true));
	}

	public final void unlockFile(String fileName) {
		table.put(fileName, Pair.of(table.get(fileName).first, false));
	}

	public final int writeToFile(String databasePath) {
		int tableFileSize = 0;
		try {
			FileOutputStream fileOut = new FileOutputStream(databasePath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(table);
			out.flush();
			fileOut.flush();
			out.close();
			fileOut.close();
			File tableFile = new File(databasePath);
			tableFileSize = (int) tableFile.length();
			System.out.println("Table File Size (after writing) : " + tableFileSize);
		} catch (IOException x) {
			System.out.println("IOException: " + x);
			x.printStackTrace();
		}
		return tableFileSize;
	}

	// public void printHistory(String fileName) {
	// if (!table.containsKey(fileName) || table.get(fileName) == null) {
	// System.out.println("File not found");
	// } else {
	// FileMetadata fileVersions = table.get(fileName).first;
	// System.out.format("\t%-50s%-10s%-10s%-40s\n", "Name", "Version",
	// "Size", "Last Modified");
	// for (int i = 0; i < fileVersions.size(); i++) {
	// System.out.println((i + 1) + ".\t"
	// + fileVersions.get(i).toString());
	// }
	// }
	// }

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
