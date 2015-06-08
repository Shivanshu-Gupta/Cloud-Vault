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
	private static HashMap<String, Pair<ArrayList<FileMetadata>, Boolean>> table;
	static long initialPosition; 
	public Table() {
		table = new HashMap<String, Pair<ArrayList<FileMetadata>, Boolean>>();
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

	public final int fileCount() {
		return table.size();
	}

//	public final int addNewFile(String fileName, int parentVersion,
//			long fileSize) {
//		int version = 1;
//		ArrayList<FileMetadata> fileVersions;
//		if (!table.containsKey(fileName) || table.get(fileName) == null) {
//			fileVersions = new ArrayList<FileMetadata>(1);
//		} else {
//			fileVersions = table.get(fileName).first;
//			version = fileVersions.size() + 1;
//		}
//		FileMetadata meta = new FileMetadata(fileName, version, parentVersion,
//				fileSize);
//		fileVersions.add(meta);
//		table.put(fileName, Pair.of(fileVersions, false));
//		return version;
//	}

	public final int addNewFile(String fileName, long fileSize) {
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
	
	public final void removeFile(String fileName, int version){
		ArrayList<FileMetadata> fileVersions = table.get(fileName).first;
		fileVersions.remove(version - 1);
		if(fileVersions.isEmpty()){
			table.remove(fileName);
		}
			
	}
	
	public final boolean hasFile(String fileName) {
		return table.containsKey(fileName) && table.get(fileName) != null;
	}

	public final boolean hasFileVersion(String fileName, int version) {
		boolean contains = false;
		if (this.hasFile(fileName)) {
			ArrayList<FileMetadata> fileVersions = table.get(fileName).first;
			contains = 0 < version && version <= fileVersions.size();
		}
		return contains;
	}

	public final ArrayList<FileMetadata> getChildren(String parent) {
		Object[] fileNames = table.keySet().toArray();
		ArrayList<FileMetadata> childrenData = new ArrayList<FileMetadata>();
		for (Object fileName : fileNames) {
			if (((String) fileName).startsWith(parent)) {
				childrenData.addAll(table.get((String) fileName).first);
			}
		}
		return childrenData;
	}

	public final boolean hasFileDated(String fileName, Date timestamp) {
		boolean contains = false;
		if (this.hasFile(fileName)) {
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

	public final long fileSize(String fileName, Date timestamp) {
		long fileSize = 0;
		ArrayList<FileMetadata> fileVersions = table.get(fileName).first;
		for (int i = 0; i < fileVersions.size(); i++) {
			if (timestamp.equals(fileVersions.get(i).timestamp())) {
				fileSize = fileVersions.get(i).fileSize();
				break;
			}
		}
		return fileSize;
	}

	public final int version(String fileName) {
		int version = -1;
		if (table.containsKey(fileName) && table.get(fileName) != null) {
			version = table.get(fileName).first.size();
		}
		return version;
	}

	public final long fileSize(String fileName, int version) {
		long fileSize = 0;
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

	public Object[] getFileList() {
		return table.keySet().toArray();
	}

	public ArrayList<FileMetadata> getFileHistory(String fileName)
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
