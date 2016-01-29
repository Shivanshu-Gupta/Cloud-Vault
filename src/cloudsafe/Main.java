package cloudsafe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.exceptions.DatabaseException;
import cloudsafe.exceptions.SetupException;

/**
 * The entry point for the CloudVault Application.
 */
public class Main {
	private final static Logger logger = LogManager.getLogger(Main.class
			.getName());

	private static final String SETUP_DONE = "Setup Complete";

	VaultClient client;
	private static String vaultPath = "trials/Cloud Vault";
	private static String configPath = "trials/config";
	private WatchDir watchdir = null;
	private static CountDownLatch restart = null;

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

		String devicePath = getDevicePath();

		vaultPath = devicePath + "/Cloud Vault";
		configPath = devicePath + "/config";
		// vaultPath = getDevicePath() + "/Cloud Vault";
		// configPath = "config";
		logger.info("vaultPath: " + vaultPath);
		logger.info("configPath: " + configPath);

		Preferences vaultPrefs = Preferences.userNodeForPackage(Main.class);
		boolean setupDone = vaultPrefs.getBoolean(SETUP_DONE, false);
		if (!setupDone) {
			try {
				logger.entry("New Setup");
				Setup cloudVaultSetup = new Setup(vaultPath, configPath);
				JTabbedPane settings = new JTabbedPane();
				ProxyConfig proxySettings = new ProxyConfig();
				settings.addTab("Proxy Settings", null, proxySettings,
						"Proxy Settings");
				JOptionPane.showMessageDialog(null, settings, "Settings",
						JOptionPane.PLAIN_MESSAGE);

				// create the required directories
				try {
					cloudVaultSetup.createDirectories();
				} catch (Exception e) {
					logger.error("Unable to create directories!", e);
					System.exit(0);
				}
				cloudVaultSetup.configureCloudAccess();
				client = new VaultClient(vaultPath, configPath);
				client.setupTable();
				client.releaseLock();
				vaultPrefs.putBoolean(SETUP_DONE, true);
				setupDone = true;
				logger.exit("Setup complete!");
			} catch (SetupException.NetworkError | SetupException.DbError e) {
				JOptionPane.showMessageDialog(null,
						"Error completing the setup: " + e.getMessage()
								+ "\nRolling back changes.", "Error",
						JOptionPane.ERROR_MESSAGE);
				logger.error("Setup Failed.", e);
			} catch (SetupException.UserInterruptedSetup e) {
				logger.error("Setup Failed.", e);
			} catch (DatabaseException e) {
				logger.error("Setup Failed.", e);
			}
		}
		if (!setupDone) {
			UndoSetup undoSetup = new UndoSetup();
			undoSetup.deleteDirectory(vaultPath, true);
			undoSetup.deleteDirectory(configPath, true);
			undoSetup.clearPrefs();
			System.exit(0);
		}
		new TrayWindows(configPath, vaultPath, restart);
		while (true) {
			launch();
			try {
				restart.await();
			} catch (InterruptedException e) {
				// this thread got interrupted before the countDownLatch could
				// count down to zero
				logger.error("Main thread interrupted.",e);
				e.printStackTrace();
			}
			watchdir.shutdown();
			client.shutdown();
		}
	}

	public void launch() {
		restart = new CountDownLatch(1);
		try {
			client = new VaultClient(vaultPath, configPath);
		} catch (DatabaseException e) {
			logger.error("VaultClient could not be setup.", e);
			JOptionPane.showMessageDialog(null,
					"Error setting up VaultClient: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
			System.exit(0);
		}

		// --------Watchdir starts here--------------
		String targetdir = vaultPath;
		boolean recursive = true;
		// register directory and process its events
		Path dir = Paths.get(targetdir);
		try {
			watchdir = new WatchDir(dir, recursive, client);
			Thread t = new Thread(new Runnable() {
				// WatchDir watch = watchdir;
				@Override
				public void run() {
					watchdir.processEvents();
				}

			});
			t.start();
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
