package cloudsafe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The entry point for the CloudVault Application.
 */
public class MainDesktop {
	private final static Logger logger = LogManager.getLogger(MainDesktop.class
			.getName());

	VaultClientDesktop client;
	static String vaultPath = "trials/Cloud Vault";
	static String configPath = "trials/config";
	private AtomicBoolean restart = new AtomicBoolean(false);
	
	public static void main(String[] args) {
		try {
			System.out.println("Welcome to your Cloud Vault!");
			logger.entry("Application Starting!");
			MainDesktop prog = new MainDesktop();
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
			// vaultPath = getDevicePath() + "/Cloud Vault";
			// configPath = "config";
			logger.info("vaultPath: " + vaultPath);
			logger.info("configPath: " + configPath);

			Setup cloudVaultSetup = new Setup(vaultPath, configPath);
			JTabbedPane settings = new JTabbedPane();
			ProxyConfig proxySettings = new ProxyConfig(configPath);
			settings.addTab("Proxy Settings", null, proxySettings,
					"Proxy Settings");
//			CloudConfig cloudSettings = new CloudConfig(configPath,
//					cloudVaultSetup);
//			settings.addTab("Clouds", null, cloudSettings, "Clouds");

			if (!Files.exists(Paths.get(vaultPath))) {
				logger.entry("New Setup");
				JOptionPane.showMessageDialog(null, settings, "Settings",
						JOptionPane.PLAIN_MESSAGE);
				cloudVaultSetup.configureCloudAccess();
				// create the directory to store configuration data
				try {
					Files.createDirectories(Paths.get(vaultPath));
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				logger.exit("Setup complete!");
			}
			client = new VaultClientDesktop(vaultPath, configPath);
			new TrayWindows(configPath, cloudVaultSetup, restart);

			// --------Watchdir starts here--------------
			String targetdir = vaultPath;
			boolean recursive = true;
			// register directory and process its events
			Path dir = Paths.get(targetdir);
			new WatchDir(dir, recursive, client).processEvents();

			// --------Watchdir ends here----------------
		} catch (Exception e) {
			System.out.println(e);
			e.printStackTrace();
		}
	}
	
	public void launch() {
		client = new VaultClientDesktop(vaultPath, configPath);

		// --------Watchdir starts here--------------
		String targetdir = vaultPath;
		boolean recursive = true;
		// register directory and process its events
		Path dir = Paths.get(targetdir);
		try {
			new WatchDir(dir, recursive, client).processEvents();
		} catch (IOException e) {
			logger.error("Error in WatchDir!", e);
		}

		// --------Watchdir ends here----------------
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
