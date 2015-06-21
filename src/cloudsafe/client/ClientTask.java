package cloudsafe.client;

import java.util.Date;

import org.apache.commons.lang3.builder.EqualsBuilder;

public class ClientTask {
	private String type;
	private Date timestamp;
	
	public ClientTask(String type, Date timestamp) {
		this.type = type;
		this.timestamp = timestamp;
	}
	
	public ClientTask(String type) {
		this.type = type;
		timestamp = new Date();
	}
	
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public Date getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}
	
	public boolean cameBefore(ClientTask task){
		return timestamp.before(task.getTimestamp());
	}
	
	public boolean cameAfter(ClientTask task){
		return timestamp.after(task.getTimestamp());
	}
	
	@Override
    public boolean equals(Object obj) {
       if (!(obj instanceof ClientTask))
            return false;
        if (obj == this)
            return true;

        ClientTask rhs = (ClientTask) obj;
        return new EqualsBuilder().
            append(type, rhs.type).
            isEquals();
    }
}
