package cloudsafe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import javax.swing.JOptionPane;
//import javax.swing.JTabbedPane;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.Dropbox;
import cloudsafe.cloud.FolderCloud;
import cloudsafe.exceptions.AuthenticationException;

import com.box.boxjavalibv2.exceptions.AuthFatalFailureException;
import com.box.boxjavalibv2.exceptions.BoxServerException;
import com.box.restclientv2.exceptions.BoxRestException;

public class SetupOld {
	private final static Logger logger = LogManager.getLogger(SetupOld.class
			.getName());
	
	//removing OneDrive from the list for now
	ArrayList<String> availableClouds = new ArrayList<String>(Arrays.asList("DropBox", "GoogleDrive", "Box",
	"FolderCloud"));
	int cloudcounter = 0;
	String vaultPath = "trials/Cloud Vault";
	int userIndex = 1;
	String configPath = "trials/config";
	File cloudConfigFile = null;
	Properties cloudConfigProps;
	
	public SetupOld(String vaultPath, String configPath) {
		this.vaultPath = vaultPath;
		this.configPath = configPath;
		this.cloudConfigFile = new File(configPath + "/clouds.properties");
		
		// create the directory to store configuration data
		try {
			Files.createDirectories(Paths.get(configPath + "/uploadQueues"));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		Properties defaultProps = new Properties();
		// sets default properties
		defaultProps.setProperty("Number of clouds", "0");	
		cloudConfigProps = new Properties(defaultProps);
		try {
			if(Files.exists(Paths.get(cloudConfigFile.toString()))) {
				InputStream inputStream = new FileInputStream(cloudConfigFile);
				cloudConfigProps.load(inputStream);
				inputStream.close();
			}
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, "<html>Error loading cloud configuration: "
					+ "cloud config file not found.<br>"
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "<html>Error loading cloud configuration: "
					+ "cloud settings could not be read.<br>"
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		cloudcounter = Integer.parseInt(cloudConfigProps.getProperty("Number of clouds"));
	};

	private Proxy getProxy() {
		Proxy proxy = Proxy.NO_PROXY;
		try {
			Properties proxySettings = new Properties();
			File configFile = new File(configPath + "/config.properties");
			InputStream inputStream = new FileInputStream(configFile);
			proxySettings.load(inputStream);
			inputStream.close();
			if (proxySettings.getProperty("requireproxy").equals("yes")) {
				String host = proxySettings.getProperty("proxyhost");
				int port = Integer.parseInt(proxySettings
						.getProperty("proxyport"));
				String authUser = proxySettings.getProperty("proxyuser");
				String authPass = proxySettings.getProperty("proxypass");
				Authenticator.setDefault(new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(authUser, authPass
								.toCharArray());
					}
				});
				proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host,
						port));
			}
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			proxy = Proxy.NO_PROXY;
		} catch (IOException e1) {
			e1.printStackTrace();
			proxy = Proxy.NO_PROXY;
		}
		return proxy;
	}

	private String static_message1 = "Minimum 4 Clouds Required\n";
	private String static_message2 = "Choose Your Cloud\n";
	private String dynamic_message = "Cloud 1 : ";

	void addCloud() throws Exception {
		String info_message = "You have added " + (cloudcounter) + " clouds\n";
		Proxy proxy = getProxy();
		String code;
		
		while (true) {
			code = (String) JOptionPane.showInputDialog(null, static_message1
					+ info_message + dynamic_message + static_message2,
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
		while (!code.equals("DropBox") && !code.equals("GoogleDrive")
				&& !code.equals("OneDrive") && !code.equals("Box")
				&& !code.equals("FolderCloud")) {
			code = (String) JOptionPane.showInputDialog(null,
					"Choose Your Cloud", "Cloud " + (cloudcounter + 1),
					JOptionPane.INFORMATION_MESSAGE, null, availableClouds.toArray(),
					availableClouds.get(0));
			code.trim();
		}
		// Deciding value of choice

		Cloud cloud;
		String cloudID = "cloud" + cloudcounter;
		String meta;
		switch (code) {
		case "DropBox":
			try {
				cloud = new Dropbox(cloudID, proxy);
				meta = cloud.metadata();
			} catch (AuthenticationException e) {
				logger.error("AuthenticationException: " + e.getMessage());
				JOptionPane.showMessageDialog(null,
						"Authentication could not be completed");
				addCloud();
				return;
			}
			cloudConfigProps.setProperty(cloudID + ".type", code);
			cloudConfigProps.setProperty(cloudID + ".code", meta);
			cloudConfigProps.setProperty(cloudID + ".status", "1");
			updateDynamicMessage(cloudcounter, "DropBox");
			break;
		case "GoogleDrive":
			cloud = new GoogleDrive(cloudID, proxy, userIndex++);
			meta = cloud.metadata();
			cloudConfigProps.setProperty(cloudID + ".type", code);
			cloudConfigProps.setProperty(cloudID + ".code", meta);
			cloudConfigProps.setProperty(cloudID + ".status", "1");
			updateDynamicMessage(cloudcounter, "GoogleDrive");
			break;
		case "OneDrive":
			try {
				cloud = new FolderCloud(cloudID);
				meta = cloud.metadata();
			} catch (Exception e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : OneDrive");
				addCloud();
				return;
			}
			cloudConfigProps.setProperty(cloudID + ".type", code);
			cloudConfigProps.setProperty(cloudID + ".code", meta);
			cloudConfigProps.setProperty(cloudID + ".status", "1");
			availableClouds.remove("onedrive");
			updateDynamicMessage(cloudcounter, "OneDrive");
			break;
		case "Box":
			try {
				cloud = new Box(cloudID, proxy);
				meta = cloud.metadata();
			} catch (BoxRestException | BoxServerException
					| AuthFatalFailureException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : Box");
				addCloud();
				return;
			}
			cloudConfigProps.setProperty(cloudID + ".type", code);
			cloudConfigProps.setProperty(cloudID + ".code", meta);
			cloudConfigProps.setProperty(cloudID + ".status", "1");
			availableClouds.remove("box");
			updateDynamicMessage(cloudcounter, "Box");
			break;
		case "FolderCloud":
			try {
				cloud = new FolderCloud(cloudID);
				meta = cloud.metadata();
			} catch (Exception e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : FolderCloud");
				addCloud();
				return;
			}
			cloudConfigProps.setProperty(cloudID + ".type", code);
			cloudConfigProps.setProperty(cloudID + ".code", meta);
			cloudConfigProps.setProperty(cloudID + ".status", "1");
			updateDynamicMessage(cloudcounter, "FolderCloud");
			break;
		}
	}

	public void updateDynamicMessage(int index, String CloudName) {
		dynamic_message = dynamic_message + CloudName + "\nCloud "
				+ (index + 2) + " : ";
		cloudcounter++;
		cloudConfigProps.setProperty("Number of clouds", Integer.toString(cloudcounter));
		// cloudSettings.addEntry(CloudName);

	}

	void deleteCloud(String cloudID) {
		System.out.println(cloudID);
		cloudConfigProps.setProperty(cloudID + ".status", "-1");
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
	
	public void saveMetadata() {
		// save the meta data
		try {
			OutputStream outputStream = new FileOutputStream(cloudConfigFile);
			cloudConfigProps.store(outputStream, "host setttings");
			outputStream.close();
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, "<html>Error saving cloud configuration: "
					+ "config file not found.<br>"
					+ "</html>");
			e.printStackTrace();
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(null, 
					"Error saving properties file: " + ex.getMessage(),
					"Error",
					JOptionPane.ERROR_MESSAGE);		
		}
	}
	
	public void readMetadata(){
		System.out.println("reading meta data");
		Properties defaultProps = new Properties();
		// sets default properties
		defaultProps.setProperty("Number of clouds", "0");	
		cloudConfigProps = new Properties(defaultProps);
		try {
			if(Files.exists(Paths.get(cloudConfigFile.toString()))) {
				InputStream inputStream = new FileInputStream(cloudConfigFile);
				cloudConfigProps.load(inputStream);
				inputStream.close();
			}
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, "<html>Error loading cloud configuration: "
					+ "cloud config file not found.<br>"
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "<html>Error loading cloud configuration: "
					+ "cloud settings could not be read.<br>"
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}
		cloudcounter = Integer.parseInt(cloudConfigProps.getProperty("Number of clouds"));
		for (int i = 0; i < cloudcounter; i++) {
			String cloudID = "cloud" + i;
			System.out.println(cloudConfigProps.getProperty(cloudID + ".status"));
		}
	}
}
