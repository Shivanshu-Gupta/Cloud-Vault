package cloudsafe;

import java.awt.Dialog;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;

import javax.swing.JDialog;

import org.apache.commons.io.input.CloseShieldInputStream;

import cloudsafe.util.Pair;
import cloudsafe.VaultClient;
import cloudsafe.cloud.Cloud;
import cloudsafe.database.FileMetadata;

/**
 * The entry point for the CloudVault Application.
 */
public class Main {
	VaultClient client;
	static String vaultPath = "trials/Cloud Vault";
	static String vaultConfigPath = "trials/config";

	String cloudMetadataPath = vaultConfigPath + "/cloudmetadata.ser";
	static ArrayList<Cloud> clouds = new ArrayList<Cloud>();
	static ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();

	static int cloudNum = 4; // Co
	static int cloudDanger = 1; // Cd
	final static int overHead = 4; // epsilon

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
		System.out.println("Enter the name of the file to download");
		String fileName;
		fileName = in.nextLine();
		System.out.println("Enter the version to download");
		int version;
		version = in.nextInt();
		in.close();
		try{
			client.download(fileName, version);
		} catch (FileNotFoundException e) {
			System.out.println("File Not Found.");
//			e.printStackTrace();
		}
	}

	private void sync() {

	}

	private static int showMenu() {
		System.out.println("1. Upload File");
		System.out.println("2. Download File");
		System.out.println("3. Sync with Vault");
		System.out.println("4. Show Files in Vault");
		System.out.println("5. Show File History");
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
			Main prog = new Main();
			prog.run();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void run() {
		Scanner in = new Scanner(System.in);
		String s;
		try {
			if (!Files.exists(Paths.get(vaultPath))) {
				System.out
						.println("It seems this is the first time you are using Cloud Vault on this device.");
				System.out
						.println("We will now setup access to your Cloud Vault.");
				
				Setup cloudVaultSetup = new Setup();
				cloudVaultSetup.configureCloudAccess();
			}
			client = new VaultClient(vaultPath);

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
					sync();
					break;
				case 4:
					Object[] fileNames = client.getFileList();
					for (Object fileName : fileNames) {
						System.out.println((String) fileName);
					}
					break;
				case 5:
					System.out.println("Enter the name of the file: ");
					s = in.nextLine();
					try{
						ArrayList<FileMetadata> fileVersions = client.getFileHistory(s);
						System.out.format("\t%-50s%-10s%-10s%-40s\n", "Name", "Version",
								"Size", "Last Modified");
						for (int i = 0; i < fileVersions.size(); i++) {
							System.out.println((i + 1) + ".\t"
									+ fileVersions.get(i).toString());
						}
					} catch(FileNotFoundException e) {
						System.out.println("File Not Found");
					}
					break;
				case 6:
					Settings proxySettings = new Settings(vaultConfigPath);
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
	}
}
