package cloudsafe;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;

//import static java.nio.file.StandardOpenOption.*; //for READ, WRITE etc






import org.apache.commons.io.input.CloseShieldInputStream;

import cloudsafe.util.Pair;
import cloudsafe.VaultClient;
import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.CloudType;
import cloudsafe.database.FileMetadata;;


/**
 * The entry point for the CloudSafe Application.
 */
public class Main {
	VaultClient client;
	static String vaultPath;
	final static String cloudMetadataPath = "temp/cloudmetadata.ser";

	static ArrayList<Cloud> clouds = new ArrayList<Cloud>();
	static ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();

	static int cloudNum = 4; // Co
	static int cloudDanger = 1; // Cd
	final static int overHead = 4; // epsilon
	

	private void addCloud() {
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		int choice = 0;
		System.out.println("Select one amongst the following drives: ");
		System.out.println("1. Dropbox\t" + "2. Google Drive\t"
				+ "3. Onedrive\t" + "4. Box\t" + "5. Folder");
		System.out.println("Enter drive number as choice: ");
		choice = in.nextInt();
		while (choice != 1 && choice != 1 && choice != 2 && choice != 3 && choice != 4 &&choice != 5) {
			System.out
					.println("Invalid choice! Enter drive number as choice: ");
			choice = in.nextInt();
		}
		switch (choice) {
		case 1:
			client.addCloud(CloudType.DROPBOX);
			break;
		case 2:
			client.addCloud(CloudType.GOOGLEDRIVE);
			break;
		case 3:
			client.addCloud(CloudType.ONEDRIVE);
			break;
		case 4:
			client.addCloud(CloudType.BOX);
			break;
		case 5:
			client.addCloud(CloudType.FOLDER);
			break;
		}
		in.close();
	}

	private void newSetup() {
		String s;
		try (Scanner in = new Scanner(new CloseShieldInputStream(System.in))) {
			for (int i = 0; i < 4; i++) {
				System.out.println("CLOUD " + (i + 1));
				addCloud();
			}
			System.out.println("Add more Clouds (Yes/No)?");
			s = in.nextLine();
			while ((s.equals("Yes"))) {
				addCloud();
				System.out.println("Add more Clouds (Yes/No)?");
				s = in.nextLine();
			}
		} catch (Exception e) {
			System.out.println("Exception: " + e);
			e.printStackTrace();
		}
		
		// save the meta data
		try {
			FileOutputStream fileOut = new FileOutputStream(
					cloudMetadataPath.toString());
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(cloudMetaData);
			out.close();
			fileOut.close();
			System.out.println("Serialized data is saved in cloudmetadata.ser");
		} catch (IOException i) {
			i.printStackTrace();
		}

		boolean newUser = true;
		for (int i = 0; i < clouds.size(); i++) {
			if (clouds.get(i).searchFile("table.ser(table)")) {
				System.out.println("Found table.ser");
				newUser = false;
				break;
			}
		}
		if (newUser)
			client.createNewTable();
		else
			client.downloadTable();
	}

	private void handleUpload() {
		System.out.println("Enter the path of the file to upload");
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		String filePath = in.nextLine();
		in.close();
		if (!Files.exists(Paths.get(filePath))) {
			System.out.println("File not found");
			return;
		}
		client.downloadTable();
		client.uploadFile(filePath);
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
			client.downloadFile(fileName, version);
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
		System.out.println("6. Exit");
		System.out.println("What do you want to do? ");

		System.out.println("Enter the number corresponding to your choice: ");
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		int choice = in.nextInt();
		if (choice < 1 || 5 < choice) {
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
			// CloseShieldInputStream shieldedIn = new
			// CloseShieldInputStream(System.in);
			// System.setIn(shieldedIn);
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
			if (Files.exists(Paths.get(cloudMetadataPath))) {
				client = new VaultClient(4, 1, cloudMetadataPath);
			} else {
				System.out
						.println("It seems this is the first time you are using Cloud Vault on this device.");
				System.out
						.println("We will now setup access to your Cloud Vault.");
				client = new VaultClient(4, 1);
				newSetup();
			}

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
