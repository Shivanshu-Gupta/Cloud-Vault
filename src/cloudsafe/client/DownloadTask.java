package cloudsafe.client;

import java.util.Date;

import org.apache.commons.lang3.builder.EqualsBuilder;

public class DownloadTask extends ClientTask{
	private String cloudFilePath;

	public DownloadTask(String cloudFilePath, Date timestamp) {
		super("download", timestamp);
		this.cloudFilePath = cloudFilePath;
	}

	public String getCloudFilePath() {
		return cloudFilePath;
	}

	public void setCloudFilePath(String cloudFilePath) {
		this.cloudFilePath = cloudFilePath;
	}
	
	@Override
    public boolean equals(Object obj) {
       if (!(obj instanceof DownloadTask))
            return false;
        if (obj == this)
            return true;

        DownloadTask rhs = (DownloadTask) obj;
        return new EqualsBuilder().
            appendSuper(super.equals(obj)).
            append(cloudFilePath, rhs.cloudFilePath).
            isEquals();
    }
}
