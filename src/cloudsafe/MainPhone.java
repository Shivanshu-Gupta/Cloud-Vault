package cloudsafe;

import java.awt.Dialog;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.Scanner;

import javax.swing.JDialog;
import javax.swing.JFileChooser;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The entry point for the CloudVault Application.
 */
public class MainPhone {
	private final static Logger logger = LogManager.getLogger(Main.class.getName());

	VaultClient client;
	String vaultPath = "trials/Cloud Vault";
	String configPath = "trials/config";

	private void handleUpload() {
		System.out.println("Enter the path of the file/folder to upload");
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		String filePath = in.nextLine();
		System.out.println("Enter the path to upload to");
		String parentPath = in.nextLine();
		in.close();
		if (!Files.exists(Paths.get(filePath))) {
			System.out.println("File/Folder not found");
			return;
		}
		client.upload(filePath, parentPath);
	}

	private void handleDownload() {
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		System.out.println("Enter the name of the file/folder to download");
		String fileName;
		fileName = in.nextLine();
		in.close();
		try{
			client.download(fileName);
		} catch (FileNotFoundException e) {
			System.out.println("File Not Found.");
//			e.printStackTrace();
		}
	}
	
	private void handleDelete() {
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		System.out.println("Enter the name of the file/folder to delete");
		String fileName;
		fileName = in.nextLine();
		in.close();
		try{
			client.delete(fileName);
		} catch (FileNotFoundException e) {
			System.out.println("File Not Found.");
//			e.printStackTrace();
		}
	}

	private static int showMenu() {
		System.out.println("1. Upload File");
		System.out.println("2. Download File");
		System.out.println("3. Delete File");
		System.out.println("4. Sync with Vault");
		System.out.println("5. Show Files in Vault");
//		System.out.println("6. Show File History");
		System.out.println("6. Changes Settings");
		System.out.println("7. Exit");
		System.out.println("What do you want to do? ");

		System.out.println("Enter the number corresponding to your choice: ");
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		int choice = in.nextInt();
		if (choice < 1 || 7 < choice) {
			System.out.println("Invalid choice.");
			System.out.println("You have the following options: ");
			choice = showMenu();
		}
		in.close();
		return choice;
	}

	public static void main(String[] args) {
		try {
			System.out.println("Welcome to your Cloud Vault!");
			MainPhone prog = new MainPhone();
			prog.run();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void run() {
		Scanner in = new Scanner(System.in);
		String s;
		String devicePath = getDevicePath();
		vaultPath = devicePath + "/Cloud Vault";
		configPath = devicePath + "/config";
		logger.info("vaultPath: " + vaultPath);
		logger.info("configPath: " + configPath);
		try {
			if (!Files.exists(Paths.get(vaultPath))) {
				System.out
						.println("It seems this is the first time you are using Cloud Vault on this device.");
				System.out
						.println("We will now setup access to your Cloud Vault.");
				
				Setup cloudVaultSetup = new Setup(vaultPath, configPath);
				cloudVaultSetup.configureCloudAccess();
			}
			client = new VaultClient(vaultPath, configPath);
			
			int choice;
			do {
				choice = showMenu();
				switch (choice) {
				case 1:
					handleUpload();
					break;
				case 2:
					handleDownload();
					break;
				case 3:
					handleDelete();
					break;
				case 4:
					client.sync();
					break;
				case 5:
					Object[] fileNames = client.getFileList();
					for (Object fileName : fileNames) {
						System.out.println((String) fileName);
					}
					break;
				case 6:
					ProxyConfig proxySettings = new ProxyConfig(configPath);
					JDialog settings = new JDialog(null, "Proxy Settings", Dialog.ModalityType.APPLICATION_MODAL);
					settings.add(proxySettings);
			        settings.pack();
					settings.setVisible(true);
					break;
				case 7:
					System.exit(0);
				}
				System.out.println("Continue (Yes/No)? ");
				s = in.nextLine();
			} while (s.equals("Yes") || s.equals("yes"));

		} catch (Exception e) {
			System.out.println(e);
			e.printStackTrace();
		} finally {
			in.close();
		}
		System.exit(0);
	}
	
	//temporary for testing syncing
	private String getDevicePath() {
		File yourFolder = null;
    	JFileChooser fc = new JFileChooser();
    	fc.setCurrentDirectory(new java.io.File(".")); // start at application current directory
    	fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    	int returnVal = fc.showSaveDialog(fc);
    	if(returnVal == JFileChooser.APPROVE_OPTION) {
    	    yourFolder = fc.getSelectedFile();
    	}
    	String devicePath =Paths.get(yourFolder.getPath()).toAbsolutePath().toString();
    	logger.info("devicePath: " + devicePath);
		return devicePath;
	}
}
