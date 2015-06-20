package cloudsafe.client;

import java.util.Date;

import org.apache.commons.lang3.builder.EqualsBuilder;

public class DeleteTask extends ClientTask{
	private String cloudFilePath;
	
	public DeleteTask(String cloudFilePath, Date timestamp) {
		super("delete", timestamp);
		this.cloudFilePath = cloudFilePath;
	}

	public DeleteTask(String cloudFilePath) {
		super("delete", new Date());
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
       if (!(obj instanceof DeleteTask))
            return false;
        if (obj == this)
            return true;

        DeleteTask rhs = (DeleteTask) obj;
        return new EqualsBuilder().
            appendSuper(super.equals(obj)).
            append(cloudFilePath, rhs.cloudFilePath).
            isEquals();
    }
}
