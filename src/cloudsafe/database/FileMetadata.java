package cloudsafe.database;

import java.io.Serializable;
import java.util.Date;

public final class FileMetadata implements Serializable {
	private static final long serialVersionUID = 10L;
	
	String fileName;
//	int version;
	long fileSize;
	Date timestamp;

	public FileMetadata(String fileName, long fileSize) {
		this.fileName = fileName;
//		this.version = version;
		this.fileSize = fileSize;
		this.timestamp = new Date();
	}
	
	public FileMetadata(String fileName, long fileSize, Date timestamp) {
		this.fileName = fileName;
//		this.version = version;
		this.fileSize = fileSize;
		this.timestamp = timestamp;
	}
	
	public String fileName() {
		return fileName;
	}

//	public int version() {
//		return version;
//	}

	public long fileSize() {
		return fileSize;
	}

	public Date timestamp() {
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
//			if (this.version != other.version) {
//				return false;
//			}
			if (this.fileSize != other.fileSize) {
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
//			hash = 13 * hash + this.version;
			hash = 13 * hash + (int)this.fileSize;
		}
		return hash;
	}

	@Override
	public String toString() {
		return String.format("%-50s%-10d%-40s", fileName, fileSize, timestamp.toString());
	}
}
