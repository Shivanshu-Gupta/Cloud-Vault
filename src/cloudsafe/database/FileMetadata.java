package cloudsafe.database;

import java.io.Serializable;
import java.sql.Timestamp;

public final class FileMetadata implements Serializable {
	private static final long serialVersionUID = 10L;
	
	String fileName;
	long fileSize;
	String cloudList;
	int minClouds;
	String timestamp;

	public FileMetadata(String fileName, long fileSize, String cloudList, int minClouds) {
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.cloudList = cloudList;
		this.minClouds = minClouds;
		this.timestamp = (new Timestamp(System.currentTimeMillis())).toString();
	}
	
	public FileMetadata(String fileName, long fileSize, String cloudList, int minClouds, String timestamp) {
		this.fileName = fileName;
		this.fileSize = fileSize;
		this.cloudList = cloudList;
		this.minClouds = minClouds;
		this.timestamp = timestamp;
	}
	
	public String fileName() {
		return fileName;
	}

	public long fileSize() {
		return fileSize;
	}
	
	public String cloudList() {
		return cloudList;
	}
	
	public int minClouds() {
		return minClouds;
	}

	public String timestamp() {
		return timestamp;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		try {
			final FileMetadata other = (FileMetadata) obj;
			
			if (this.fileName != other.fileName
					&& (this.fileName == null || !this.fileName
							.equals(other.fileName))) {
				return false;
			}
			if (this.fileSize != other.fileSize) {
				return false;
			}
			if (!this.cloudList.equals(other.cloudList)){
				return false;
			}
			if(this.minClouds != other.minClouds){
				return false;
			}
		} catch (ClassCastException ce) {
			ce.printStackTrace();
		}

		return true;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		if (this.fileName != null) {
			hash = 13 * hash + this.fileName.hashCode();
			hash = 13 * hash + (int)this.fileSize;
			hash = 13 * hash + this.cloudList.hashCode();
			hash = 13 * hash + this.minClouds;
		}
		return hash;
	}

	@Override
	public String toString() {
		return String.format("%-50s%-10d%-40s", fileName, fileSize, timestamp);
	}
}
