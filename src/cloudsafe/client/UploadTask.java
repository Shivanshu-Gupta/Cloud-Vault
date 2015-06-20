package cloudsafe.client;

import java.util.Date;

import org.apache.commons.lang3.builder.EqualsBuilder;

public class UploadTask extends ClientTask {
	private String localFilePath;
	private String uploadPath;
	
	public UploadTask( String localFilePath, String uploadPath,
			Date timestamp) {
		super("upload", timestamp);
		this.localFilePath = localFilePath;
		this.uploadPath = uploadPath;
	}
	
	public String getLocalFilePath() {
		return localFilePath;
	}
	public void setLocalFilePath(String localFilePath) {
		this.localFilePath = localFilePath;
	}
	
	public String getUploadPath() {
		return uploadPath;
	}
	public void setUploadPath(String uploadPath) {
		this.uploadPath = uploadPath;
	}
	
	@Override
    public boolean equals(Object obj) {
       if (!(obj instanceof UploadTask))
            return false;
        if (obj == this)
            return true;

        UploadTask rhs = (UploadTask) obj;
        return new EqualsBuilder().
            appendSuper(super.equals(obj)).
            append(localFilePath, rhs.localFilePath).
            append(uploadPath, rhs.uploadPath).
            isEquals();
    }
}
