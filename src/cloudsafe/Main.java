package cloudsafe;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;

import javax.swing.JFileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.util.Pair;
import cloudsafe.cloud.Cloud;

/**
 * The entry point for the CloudVault Application.
 */
public class Main {
	private final static Logger logger = LogManager.getLogger(Main.class.getName());

	VaultClientDesktop client;
	static String vaultPath = "trials/Cloud Vault";

	static String configPath = "trials/config";

	String cloudMetadataPath = configPath + "/cloudmetadata.ser";

	static ArrayList<Cloud> clouds = new ArrayList<Cloud>();
	static ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();

	static int cloudNum = 4; // Co
	static int cloudDanger = 1; // Cd
	final static int overHead = 4; // epsilon
	
	public static void main(String[] args) {
		try {
			System.out.println("Welcome to your Cloud Vault!");
			logger.entry("Application Starting!");
			Main prog = new Main();
			prog.run();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void run() {
		try {
			String devicePath = getDevicePath();
			vaultPath = devicePath + "/Cloud Vault";
			configPath = devicePath + "/config";
			logger.info("vaultPath: " + vaultPath);
			logger.info("configPath: " + configPath);
			if (!Files.exists(Paths.get(vaultPath))) {
				logger.entry("New Setup");
				Setup cloudVaultSetup = new Setup(vaultPath, configPath);
				CloudConfig cloudSettings = new CloudConfig(cloudVaultSetup);
				cloudVaultSetup.settings.addTab("Clouds", null, cloudSettings, "Clouds");
				cloudVaultSetup.configureCloudAccess();
				logger.exit("Setup complete!");
			}
			client = new VaultClientDesktop(vaultPath, configPath);

			
			//--------Watchdir starts here--------------
	    	String targetdir = vaultPath;
	        // parse arguments
	        boolean recursive = true;
	        // register directory and process its events
	        Path dir = Paths.get(targetdir);
	        new WatchDir(dir, recursive, client).processEvents();
			
			//--------Watchdir ends here----------------
		} catch (Exception e) {
			System.out.println(e);
			e.printStackTrace();
		}
	}
	
	// temporary for testing syncing
	private String getDevicePath() {
		File yourFolder = null;
		JFileChooser fc = new JFileChooser();
		fc.setCurrentDirectory(new java.io.File(".")); // start at application
														// current directory
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnVal = fc.showSaveDialog(fc);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			yourFolder = fc.getSelectedFile();
		}
		String devicePath = Paths.get(yourFolder.getPath()).toAbsolutePath()
				.toString();
		logger.info("devicePath: " + devicePath);
		return devicePath;
	}
}
