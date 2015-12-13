package cloudsafe;

import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.CloudMeta;
import cloudsafe.cloud.Dropbox;
import cloudsafe.cloud.FolderCloud;
import cloudsafe.exceptions.AuthenticationException;
import cloudsafe.util.UserProxy;

import com.box.boxjavalibv2.exceptions.AuthFatalFailureException;
import com.box.boxjavalibv2.exceptions.BoxServerException;
import com.box.restclientv2.exceptions.BoxRestException;
import com.google.gson.Gson;

public class Setup {
	private final static Logger logger = LogManager.getLogger(Setup.class
			.getName());
	
	public static final String CLOUDS_META = "CloudsMetaData";
	public static final String CLOUDCOUNT = "Number of Clouds Configured";
	public static final String NEXTID = "Next Unique ID Available";

	private static final String minCloudMessage = "Minimum 4 Clouds Required\n";
	private static final String chooseCloudMessage = "Choose Your Cloud\n";
	private String dynamic_message = "Cloud 1 : ";
	
	//removing OneDrive from the list for now
	ArrayList<String> availableClouds = new ArrayList<String>(Arrays.asList(FolderCloud.NAME, Dropbox.NAME, GoogleDrive.NAME, Box.NAME));
	ArrayList<CloudMeta> cloudMetas;
	int cloudcounter = 0;
	int nextID = 0;
	String vaultPath = "trials/Cloud Vault";
	String configPath = "trials/config";
	//TODO : find out if it's correct to initialize userIndex with 1. 
	int userIndex = 1;
	Preferences cloudConfigPrefs = Preferences.userNodeForPackage(Setup.class);
	
	public Setup(String vaultPath, String configPath) {
		this.vaultPath = vaultPath;
		this.configPath = configPath;
		
		cloudcounter = cloudConfigPrefs.getInt(CLOUDCOUNT, 0);
		nextID = cloudConfigPrefs.getInt(NEXTID, 0);
		
		cloudMetas = new ArrayList<>();
		String cloudsMetaString = cloudConfigPrefs.get(CLOUDS_META, null);
		Gson gson = new Gson();
		if(cloudsMetaString == null) {
			cloudConfigPrefs.put(CLOUDS_META, gson.toJson(cloudMetas));
			try {
				cloudConfigPrefs.flush();
			} catch (BackingStoreException e) {
				e.printStackTrace();
			}
		} else {
			CloudMeta[] cloudArray = gson.fromJson(cloudsMetaString,
					CloudMeta[].class);
			cloudMetas = new ArrayList<>(Arrays.asList(cloudArray));
		}
	};

	void addCloud() throws Exception {
		String cloudCountMessage = "You have added " + cloudcounter + " clouds\n";
		Proxy proxy = UserProxy.getProxy();
		String code;
		
		while (true) {
			code = (String) JOptionPane.showInputDialog(null, minCloudMessage
					+ cloudCountMessage + dynamic_message + chooseCloudMessage,
					"Cloud " + (cloudcounter + 1),
					JOptionPane.INFORMATION_MESSAGE, null, availableClouds.toArray(),
					availableClouds.get(0));
			
			if (code == null) {
				if (cloudcounter < 4) {
					int n = JOptionPane.showConfirmDialog(null,
							"Do you really want to exit?", "Exit",
							JOptionPane.YES_NO_OPTION,
							JOptionPane.QUESTION_MESSAGE, null);
					if (n == JOptionPane.YES_OPTION) {
						UndoSetup undoSetup = new UndoSetup();
						undoSetup.delete(vaultPath, true);
						undoSetup.delete(configPath, true);
						System.exit(0);
					}
				} else {
					throw new Exception("No Clouds Added");
				}
			} else {
				break;
			}
		}
		code.trim();
		while (!code.equals(Dropbox.NAME) && !code.equals(GoogleDrive.NAME)
				&& !code.equals("ONEDRIVE") && !code.equals(Box.NAME)
				&& !code.equals(FolderCloud.NAME)) {
			code = (String) JOptionPane.showInputDialog(null,
					"Choose Your Cloud", "Cloud " + (cloudcounter + 1),
					JOptionPane.INFORMATION_MESSAGE, null, availableClouds.toArray(),
					availableClouds.get(0));
			code.trim();
		}
		// Deciding value of choice
		
		Cloud cloud;
		ConcurrentHashMap<String, String> meta = new ConcurrentHashMap<>();
		CloudMeta cloudMeta = null;
		switch (code) {
		case Dropbox.NAME:
			try {
				cloud = new Dropbox(proxy);
				meta = cloud.getMetaData();
			} catch (AuthenticationException e) {
				logger.error("AuthenticationException: " + e.getMessage());
				JOptionPane.showMessageDialog(null,
						"Authentication could not be completed");
				addCloud();
				return;
			}
			cloudMeta = new CloudMeta(nextID, Dropbox.NAME, meta);
			updateDynamicMessage(cloudcounter, "DropBox");
			break;
		case GoogleDrive.NAME:
			cloud = new GoogleDrive(proxy, userIndex++);
			meta = cloud.getMetaData();
			cloudMeta = new CloudMeta(nextID, GoogleDrive.NAME, meta);
			updateDynamicMessage(cloudcounter, "GoogleDrive");
			break;
		case "ONEDRIVE":
			try {
				cloud = new FolderCloud();
				meta = cloud.getMetaData();
			} catch (Exception e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : FolderCloud");
				addCloud();
				return;
			}
			availableClouds.remove("onedrive");
			cloudMeta = new CloudMeta(nextID, FolderCloud.NAME, meta);
			updateDynamicMessage(cloudcounter, "FolderCloud");
			break;
		case Box.NAME:
			try {
				cloud = new Box(proxy);
				meta = cloud.getMetaData();
			} catch (BoxRestException | BoxServerException
					| AuthFatalFailureException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : Box");
				addCloud();
				return;
			}
			availableClouds.remove("box");
			cloudMeta = new CloudMeta(nextID, FolderCloud.NAME, meta);
			updateDynamicMessage(cloudcounter, "Box");
			break;
		case FolderCloud.NAME:
			try {
				cloud = new FolderCloud();
				meta = cloud.getMetaData();
			} catch (Exception e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : FolderCloud");
				addCloud();
				return;
			}
			cloudMeta = new CloudMeta(nextID, FolderCloud.NAME, meta);
			updateDynamicMessage(cloudcounter, "FolderCloud");
		}
		
		if(cloudMeta != null) {
			cloudMetas.add(cloudMeta);
		}		
		Gson gson = new Gson();
		cloudConfigPrefs.put(CLOUDS_META, gson .toJson(cloudMetas));
		cloudConfigPrefs.flush();
	}

	public void updateDynamicMessage(int index, String CloudName) {
		dynamic_message = dynamic_message + CloudName + "\nCloud "
				+ (index + 2) + " : ";
		cloudcounter++;
		cloudConfigPrefs.put(CLOUDCOUNT, Integer.toString(cloudcounter));
	}

	void deleteCloud(int removeIndex) {
		System.out.println(removeIndex);
		cloudMetas.remove(removeIndex);
		Gson gson = new Gson();
		cloudConfigPrefs.put(CLOUDS_META, gson.toJson(cloudMetas));
		cloudcounter--;
		cloudConfigPrefs.put(CLOUDCOUNT, Integer.toString(cloudcounter));
	}

	public void configureCloudAccess() {
		try{
			for (int i = (cloudcounter + 1); i <= 4; i++) {
				addCloud();
			}
			Object[] options = { "Yes", "No" };
			int choice = JOptionPane.showOptionDialog(null,
					"Do You Want to Add more Clouds?", "More Clouds?",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
					null, options, options[0]);

			while ((choice == JOptionPane.YES_OPTION)) {
				addCloud();
				choice = JOptionPane
						.showOptionDialog(null,
								"Do You Want to Add more Clouds?",
								"More Clouds?", JOptionPane.YES_NO_OPTION,
								JOptionPane.QUESTION_MESSAGE, null, options,
								options[0]);
			}
		} catch (Exception e) {
			logger.error("Exception: ", e);
		}
		saveMetadata();
	}
	
	public void createDirectories() throws IOException {
		Files.createDirectories(Paths.get(vaultPath));
		Files.createDirectories(Paths.get(configPath));
	}
	
	public void saveMetadata() {
		// save the meta data
		try {
			cloudConfigPrefs.flush();
		} catch (BackingStoreException e) {
			JOptionPane.showMessageDialog(null, 
					"Error saving preferences: " + e.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE);	
			e.printStackTrace();
		}
	}
}
