package cloudsafe.database;

import java.io.Serializable;
import java.util.Date;

public final class FileMetadata implements Serializable {
	private static final long serialVersionUID = 10L;
	
//	String parent;
//	int parentVersion;
	String fileName;
	int version;
	long fileSize;
	Date timestamp;

	public FileMetadata(String fileName, int version, long fileSize) {
//		this.parent = "";
//		this.parentVersion =-1;
		this.fileName = fileName;
		this.version = version;
		this.fileSize = fileSize;
		this.timestamp = new Date();
	}
	
//	public FileMetadata(String fileName, int version, int parentVersion, long fileSize) {
////		this.parent = parent;
////		this.parentVersion = parentVersion;
//		this.fileName = fileName;
//		this.version = version;
//		this.fileSize = fileSize;
//		this.timestamp = new Date();
//	}
	
//	public String parent() {
//		return parent;
//	}
//	
//	public int parentVersion() {
//		return parentVersion;
//	}
	
	public String fileName() {
		return fileName;
	}

	public int version() {
		return version;
	}

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
			if (this.version != other.version) {
				return false;
			}
//			if (this.parent != other.parent
//					&& (this.parent == null || !this.parent
//							.equals(other.parent))) {
//				return false;
//			}
//			if (this.parentVersion != other.parentVersion) {
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
			hash = 13 * hash + this.version;
//			hash = 13 * hash + this.parent.hashCode();
//			hash = 13 * hash + this.parentVersion;
			hash = 13 * hash + (int)this.fileSize;
		}
		return hash;
	}

	@Override
	public String toString() {
		return String.format("%-50s%-10d%-10d%-40s", fileName, version,
				fileSize, timestamp.toString());
	}
}
