package cloudsafe;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;

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

	static String localConfigPath = "trials/config";

	String cloudMetadataPath = localConfigPath + "/cloudmetadata.ser";

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
			if (!Files.exists(Paths.get(vaultPath))) {
				System.out
						.println("It seems this is the first time you are using Cloud Vault on this device.");
				System.out
						.println("We will now setup access to your Cloud Vault.");
				logger.entry("New Setup");
				Setup cloudVaultSetup = new Setup();
				cloudVaultSetup.configureCloudAccess();
				logger.exit("Setup complete!");
			}
			client = new VaultClientDesktop(vaultPath);

			
			//--------My work starts here--------------
	    	String targetdir = vaultPath;
	        // parse arguments
	        boolean recursive = true;
	        // register directory and process its events
	        Path dir = Paths.get(targetdir);
	        new WatchDir(dir, recursive, client).processEvents();
			
			//--------My work ends here----------------
		} catch (Exception e) {
			System.out.println(e);
			e.printStackTrace();
		}
	}
}
