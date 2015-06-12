package cloudsafe;

import java.awt.Dialog;
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

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.commons.lang3.ArrayUtils;

import com.box.boxjavalibv2.exceptions.AuthFatalFailureException;
import com.box.boxjavalibv2.exceptions.BoxServerException;
import com.box.restclientv2.exceptions.BoxRestException;

import cloudsafe.cloud.Cloud;
import cloudsafe.exceptions.AuthenticationException;
import cloudsafe.util.Pair;

public class Setup {
	String[] possibleValues = { "DropBox", "GoogleDrive", "OneDrive", "Box", "FolderCloud" };
	int cloudcounter = 0;
	static String vaultPath = "trials/Cloud Vault";
	static String vaultConfigPath = "trials/config";
	String cloudMetadataPath = vaultConfigPath + "/cloudmetadata.ser";
	static ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();

	public Setup(String vaultPath, String vaultConfigPath) {
		Setup.vaultPath = vaultPath;
		Setup.vaultConfigPath = vaultConfigPath;
	}

	public Setup() {
		//create the directory to store configuration data
		try {
			Files.createDirectories(Paths.get(vaultConfigPath));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
//		Settings settings = new Settings(vaultConfigPath);
//		settings.show();
		ProxyConfig proxySettings = new ProxyConfig(vaultConfigPath);
		JDialog settings = new JDialog(null, "Proxy Settings", Dialog.ModalityType.APPLICATION_MODAL);
		settings.add(proxySettings);
        settings.pack();
		settings.setVisible(true);
	};

	private Proxy getProxy() {
		Proxy proxy = Proxy.NO_PROXY;
		try {
			Properties proxySettings = new Properties();
			File configFile = new File(vaultConfigPath + "/config.properties");
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

	private void addCloud() {
		Proxy proxy = getProxy();
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		int choice = 0;
//		System.out.println("Select one amongst the following drives: ");
//		System.out.println("1. Dropbox\t" + "2. Google Drive\t"
//				+ "3. Onedrive\t" + "4. Box\t" + "5. Folder");
//		System.out.println("Enter drive number as choice: ");
//		choice = in.nextInt();
//		while (choice != 1 && choice != 1 && choice != 2 && choice != 3
//				&& choice != 4 && choice != 5) {
//			System.out
//					.println("Invalid choice! Enter drive number as choice: ");
//			choice = in.nextInt();
//		}
		
		String code;
		code = (String) JOptionPane.showInputDialog(null,
		"Choose Your Cloud", "Cloud " + cloudcounter,
		JOptionPane.INFORMATION_MESSAGE, null,
		possibleValues, possibleValues[0]);
		code.trim();
		while (!code.equals("DropBox") && !code.equals("GoogleDrive") && !code.equals("OneDrive")
				&& !code.equals("Box") && !code.equals("FolderCloud"))
		{
			code = (String) JOptionPane.showInputDialog(null,
			"Choose Your Cloud", "Cloud " + cloudcounter,
			JOptionPane.INFORMATION_MESSAGE, null,
			possibleValues, possibleValues[0]);
			code.trim();
//			JOptionPane.showMessageDialog(null, "Invalid Choice! Enter drive number as choice : " + code);
//			code = JOptionPane.showInputDialog(null,
//					"1. Dropbox\t" + "2. Google Drive\t" + "3. Onedrive\t" + "4. Box\t" + "5. Folder",
//					"Enter Drive Number as Choice", JOptionPane.QUESTION_MESSAGE);
//			code = code.trim();
		}
		//Deciding value of choice
		if(code.equals("DropBox")){
			choice = 1;
			possibleValues = ArrayUtils.removeElement(possibleValues, "DropBox");
		}
		else if(code.equals("GoogleDrive")){
			choice = 2;
			possibleValues = ArrayUtils.removeElement(possibleValues, "GoogleDrive");
		}
		else if(code.equals("OneDrive")){
			choice = 3;
			possibleValues = ArrayUtils.removeElement(possibleValues, "OneDrive");
		}
		else if(code.equals("Box")){
			choice = 4;
			possibleValues = ArrayUtils.removeElement(possibleValues, "Box");
		}
		else if(code.equals("FolderCloud")){
			choice = 5;
		}

//		choice = Integer.parseInt(code);
		Cloud cloud;
		String meta;
		try {
			switch (choice) {
			case 1:
				cloud = new Dropbox(proxy);
				meta = cloud.metadata();
				cloudMetaData.add(Pair.of("dropbox", meta));
				break;
			case 2:
				cloud = new GoogleDrive(proxy);
				meta = cloud.metadata();
				cloudMetaData.add(Pair.of("googledrive", meta));
				break;
			case 3:
				cloud = new FolderCloud();
				meta = cloud.metadata();
				cloudMetaData.add(Pair.of("folder", meta));
				break;
			case 4:
				cloud = new Box(proxy);
				meta = cloud.metadata();
				cloudMetaData.add(Pair.of("box", meta));
				break;
			case 5:
				cloud = new FolderCloud();
				meta = cloud.metadata();
				cloudMetaData.add(Pair.of("folder", meta));
				break;
			}
		} catch (AuthenticationException e) {
			System.out.println("AuthenticationException: " + e.getMessage());
		} catch (BoxRestException | BoxServerException
				| AuthFatalFailureException e) {
			e.printStackTrace();
		}
		in.close();
	}

	public void configureCloudAccess() {

		try (Scanner in = new Scanner(new CloseShieldInputStream(System.in))) {
			for (cloudcounter = 1; cloudcounter <= 4; cloudcounter++) {
				System.out.println("CLOUD " + cloudcounter);
				addCloud();
			}
//			System.out.println("Add more Clouds (Yes/No)?");
//			s = in.nextLine();
//			s = JOptionPane.showInputDialog(null,
//					"Yes/No",
//					"Add More Clouds?", JOptionPane.QUESTION_MESSAGE);
//			s = s.trim();
			
			Object[] options = { "Yes", "No" };
			  int choice = JOptionPane.showOptionDialog(null, 
			      "Do You Want to Add more Clouds?", 
			      "More Clouds?", 
			      JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, 
			      null, options, options[0]);
			  
			while ((choice == JOptionPane.YES_OPTION)) {
				addCloud();
				cloudcounter++;
//				System.out.println("Add more Clouds (Yes/No)?");
//				s = in.nextLine();
				choice = JOptionPane.showOptionDialog(null, 
				      "Do You Want to Add more Clouds?", 
				      "More Clouds?", 
				      JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, 
				      null, options, options[0]);
			}
		} catch (Exception e) {
			System.out.println("Exception: " + e);
			e.printStackTrace();
		}

		// save the meta data
		try {
			FileOutputStream fileOut = new FileOutputStream(cloudMetadataPath);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(cloudMetaData);
			out.close();
			fileOut.close();
			System.out.println("Serialized data is saved in cloudmetadata.ser");
			Files.createDirectories(Paths.get(vaultPath));
		} catch (IOException i) {
			i.printStackTrace();
		}
	}
}
