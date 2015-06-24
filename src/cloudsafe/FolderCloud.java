//package net.fec.openrq;
package cloudsafe;

import java.io.File;
import java.io.IOException;

import static java.nio.file.StandardOpenOption.*;		//for READ, WRITE etc

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import javax.swing.JFileChooser;






import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.WriteMode;
import cloudsafe.exceptions.AuthenticationException;

// import net.fec.openrq.cloud.Cloud;

public final class FolderCloud implements Cloud{

	private final static Logger logger = LogManager
			.getLogger(FolderCloud.class.getName());
	
	Path cloudPath;

	public FolderCloud(String cloudPath)
	{
		this.cloudPath = Paths.get(cloudPath);
	}
	
	public FolderCloud() throws Exception
	{
    	File yourFolder = null;
    	JFileChooser fc = new JFileChooser();
    	fc.setCurrentDirectory(new java.io.File(".")); // start at application current directory
    	fc.setDialogTitle("Locate Your Folder");	
    	fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    	int returnVal = fc.showSaveDialog(fc);
    	if(returnVal == JFileChooser.CANCEL_OPTION) {
    		throw new AuthenticationException(
					"User cancelled Authentication");
    	}
    	if(returnVal == JFileChooser.APPROVE_OPTION) {
    	    yourFolder = fc.getSelectedFile();
    	}
		
    	this.cloudPath = Paths.get(yourFolder.getPath()).toAbsolutePath();
  	}
	
	@Override
	public String metadata()
	{
		return cloudPath.toString();
	}


//	@Override
//	public void setupNewAccess() {
//		return;		
//	}
//	
//	@Override
//	public void setupAccess() {
//		return;		
//	}
	
	@Override
	public boolean isAvailable()
	{	
		return Files.exists(cloudPath);
	}
	
	@Override
	public void uploadFile(byte [] data, String fileID, WriteMode mode)
	{
		try {
			Path filePath = Paths.get(cloudPath.toString() + '/' + fileID);
			Files.createDirectories(filePath.getParent());
			Files.write(filePath, data, CREATE, WRITE, TRUNCATE_EXISTING);
		} catch (IOException x) {
		    logger.error("IOException in uploadFile: %s%n", x);
		}
	}

	@Override
	public void uploadFile(String name, String fileID, WriteMode mode) {
		try {
			Path path = Paths.get(name);
			byte[] data = Files.readAllBytes(path);
			Path filePath = Paths.get(cloudPath.toString() + '/' + fileID);
			Files.createDirectories(filePath.getParent());
			Files.write(filePath, data, CREATE, WRITE, TRUNCATE_EXISTING);
		} catch (IOException x) {
		    logger.error("IOException in uploadFile: %s%n", x);
		}
	}
	
	@Override
	public byte [] downloadFile(String fileID)
	{
		byte [] data = {};
		try {
			Path filePath = Paths.get(cloudPath.toString() + "/" + fileID);
			data = Files.readAllBytes(filePath);
		} catch (IOException x) {
		    logger.error("IOException: %s%n", x);
		}
		return data;
	}


	@Override
	public void downloadFile(String path, String fileID){
		byte [] data = {};
		try {
			Path filePath = Paths.get(cloudPath.toString() + "/" + fileID);
			data = Files.readAllBytes(filePath);
			Files.write(Paths.get(path), data, CREATE, TRUNCATE_EXISTING);
		} catch (IOException x) {
		    logger.error("IOException: %s%n", x);
		}
	}

	@Override
	public boolean searchFile(String fileID) {
		Path filePath = Paths.get(cloudPath.toString() + "/" + fileID);
		return Files.exists(filePath);
	}

	@Override
	public void deleteFile(String path) {
		Path filePath = Paths.get(cloudPath.toString() + "/" + path);
		try {
			Files.delete(filePath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}