package cloudsafe.cloud;

import java.io.IOException;

import cloudsafe.cloud.WriteMode;

public interface Cloud {
	
//	private void setupNewAccess() throws IOException;
//	
//	public void setupAccess() throws IOException;
	public String metadata();
	
	public boolean isAvailable();
	
	public void uploadFile(byte [] data, String fileID, WriteMode mode) throws IOException;
	
	public void uploadFile(String path, String fileID, WriteMode mode) throws IOException;

	public byte [] downloadFile(String fileID) throws IOException;
	
	public void downloadFile(String path, String fileID) throws IOException;
	
	public void deleteFile(String path);
	
	public boolean searchFile(String fileID);
	
	public String getID();
	
	public void setID(String ID);

}