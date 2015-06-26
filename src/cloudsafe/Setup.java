package cloudsafe;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;

import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.cloud.Cloud;
import cloudsafe.exceptions.AuthenticationException;
import cloudsafe.util.Pair;

import com.box.boxjavalibv2.exceptions.AuthFatalFailureException;
import com.box.boxjavalibv2.exceptions.BoxServerException;
import com.box.restclientv2.exceptions.BoxRestException;

public class Setup {
	private final static Logger logger = LogManager.getLogger(Setup.class
			.getName());
	
	
	JTabbedPane settings = new JTabbedPane();
	
	String[] possibleValues = { "DropBox", "GoogleDrive", "OneDrive", "Box",
			"FolderCloud" };
	int cloudcounter = 0;
	String vaultPath = "trials/Cloud Vault";
	int userIndex = 1;
	String configPath = "trials/config";
	String cloudMetadataPath = configPath + "/cloudmetadata.ser";
	ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();

	public Setup(String vaultPath, String configPath) {
		this.vaultPath = vaultPath;
		this.configPath = configPath;
		this.cloudMetadataPath = configPath + "/cloudmetadata.ser";
		// create the directory to store configuration data
		try {
			Files.createDirectories(Paths.get(configPath));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		ProxyConfig proxySettings = new ProxyConfig(configPath);
		settings.addTab("Proxy Settings", null, proxySettings, "Proxy Settings");
		JOptionPane.showMessageDialog(null, settings, "Settings", JOptionPane.PLAIN_MESSAGE);
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

	String static_message = "Choose Your Cloud\n";
	String dynamic_message = "Cloud 1 : ";

	void addCloud() {
		String info_message = "You have added " + (cloudcounter - 1)
				+ " clouds\n";
		Proxy proxy = getProxy();
		// Scanner in = new Scanner(new CloseShieldInputStream(System.in));

		String code;

		while (true) {
			code = (String) JOptionPane.showInputDialog(null, info_message
					+ dynamic_message + static_message,
					"Cloud " + cloudcounter, JOptionPane.INFORMATION_MESSAGE,
					null, possibleValues, possibleValues[0]);

			if (code == null) {
				int n = JOptionPane.showConfirmDialog(null,
						"Do you really want to exit?", "Exit",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE, null);
				if (n == JOptionPane.YES_OPTION) {
					UndoSetup undoSetup = new UndoSetup();
					undoSetup.delete(vaultPath, true);
					undoSetup.delete(configPath, true);
					System.exit(0);
				} else {
					continue;
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
					"Choose Your Cloud", "Cloud " + cloudcounter,
					JOptionPane.INFORMATION_MESSAGE, null, possibleValues,
					possibleValues[0]);
			code.trim();
		}
		// Deciding value of choice

		Cloud cloud;
		String meta;
		switch (code) {
		case "DropBox":
			try {
				cloud = new Dropbox(proxy);
				meta = cloud.metadata();
				cloudMetaData.add(Pair.of("dropbox", meta));
			} catch (AuthenticationException e) {
				logger.error("AuthenticationException: " + e.getMessage());
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : Dropbox");
				addCloud();
				return;
			}
			// possibleValues = ArrayUtils.removeElement(possibleValues,
			// "DropBox");
			updateDynamicMessage(cloudcounter, "DropBox");
			break;
		case "GoogleDrive":
			cloud = new GoogleDrive(proxy, userIndex++);
			meta = cloud.metadata();
			cloudMetaData.add(Pair.of("googledrive", meta));
			// possibleValues = ArrayUtils.removeElement(possibleValues,
			// "GoogleDrive");
			updateDynamicMessage(cloudcounter, "GoogleDrive");
			break;
		case "OneDrive":
			try {
				cloud = new FolderCloud();
				meta = cloud.metadata();
				cloudMetaData.add(Pair.of("folder", meta));
			} catch (Exception e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : OneDrive");
				addCloud();
				return;
			}
			possibleValues = ArrayUtils.removeElement(possibleValues,
					"OneDrive");
			updateDynamicMessage(cloudcounter, "OneDrive");
			break;
		case "Box":
			try {
				cloud = new Box(proxy);
				meta = cloud.metadata();
				cloudMetaData.add(Pair.of("box", meta));
			} catch (BoxRestException | BoxServerException
					| AuthFatalFailureException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : Box");
				addCloud();
				return;
			}
			possibleValues = ArrayUtils.removeElement(possibleValues, "Box");
			updateDynamicMessage(cloudcounter, "Box");
			break;
		case "FolderCloud":
			try {
				cloud = new FolderCloud();
				meta = cloud.metadata();
				cloudMetaData.add(Pair.of("folder", meta));
			} catch (Exception e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(null,
						"Authentication Interrupted : FolderCloud");
				addCloud();
				return;
			}
			updateDynamicMessage(cloudcounter, "FolderCloud");
			break;
		}
	}

	// in.close();

	public void updateDynamicMessage(int index, String CloudName) {
		dynamic_message = dynamic_message + CloudName + "\nCloud "
				+ (index + 1) + " : ";
//		cloudSettings.addEntry(CloudName);
		
	}

	void deleteCloud(int index)
	{
		cloudMetaData.remove(index);
	}
	
	public void configureCloudAccess() {

		try (Scanner in = new Scanner(new CloseShieldInputStream(System.in))) {
			for (cloudcounter = 1; cloudcounter <= 4; cloudcounter++) {
				System.out.println("CLOUD " + cloudcounter);
				addCloud();
			}
			Object[] options = { "Yes", "No" };
			int choice = JOptionPane.showOptionDialog(null,
					"Do You Want to Add more Clouds?", "More Clouds?",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
					null, options, options[0]);

			while ((choice == JOptionPane.YES_OPTION)) {
				addCloud();
				cloudcounter++;
				// System.out.println("Add more Clouds (Yes/No)?");
				// s = in.nextLine();
				choice = JOptionPane
						.showOptionDialog(null,
								"Do You Want to Add more Clouds?",
								"More Clouds?", JOptionPane.YES_NO_OPTION,
								JOptionPane.QUESTION_MESSAGE, null, options,
								options[0]);
			}
		} catch (Exception e) {
			logger.error("Exception: " + e);
			e.printStackTrace();
		}

		// save the meta data
		try {
			FileOutputStream fileOut = new FileOutputStream(cloudMetadataPath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(cloudMetaData);
			out.close();
			fileOut.close();
			logger.info("Serialized data is saved in cloudmetadata.ser");
			Files.createDirectories(Paths.get(vaultPath));
		} catch (IOException i) {
			i.printStackTrace();
		}
	}
}
