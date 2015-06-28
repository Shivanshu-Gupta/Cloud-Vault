package cloudsafe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The entry point for the CloudVault Application.
 */
public class Main {
	private final static Logger logger = LogManager.getLogger(Main.class
			.getName());

	VaultClientDesktop client;
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
		try {
			String devicePath = getDevicePath();

			vaultPath = devicePath + "/Cloud Vault";
			configPath = devicePath + "/config";
			// vaultPath = getDevicePath() + "/Cloud Vault";
			// configPath = "config";
			logger.info("vaultPath: " + vaultPath);
			logger.info("configPath: " + configPath);
			while(true) {
				Setup cloudVaultSetup = new Setup(vaultPath, configPath);
				JTabbedPane settings = new JTabbedPane();
				ProxyConfig proxySettings = new ProxyConfig(configPath);
				settings.addTab("Proxy Settings", null, proxySettings,
						"Proxy Settings");
	
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
				
				launch();
				new TrayWindows(configPath, cloudVaultSetup, restart);
				restart.await();
				watchdir.shutdown();
				client.shutdown();
			}
		} catch (Exception e) {
			System.out.println(e);
			e.printStackTrace();
		}
	}
	
	public void launch() {
		restart = new CountDownLatch(1);
		client = new VaultClientDesktop(vaultPath, configPath);

		// --------Watchdir starts here--------------
		String targetdir = vaultPath;
		boolean recursive = true;
		// register directory and process its events
		Path dir = Paths.get(targetdir);
		try {
			watchdir = new WatchDir(dir, recursive, client);
			Thread t = new Thread(new Runnable(){
//				WatchDir watch = watchdir;
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
