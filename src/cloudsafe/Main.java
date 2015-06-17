package cloudsafe;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.Setup;
import cloudsafe.VaultClient;

public class Main extends JFrame {
	private static final long serialVersionUID = 1L;
	private final static Logger logger = LogManager.getLogger(Main.class
			.getName());

	VaultClient client;
	String vaultPath = "trials/Cloud Vault";
	String configPath = "trials/config";

	private JScrollPane sp = null;
	private JTree vaultDirTree;
	private JLabel selectedLabel;

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
		String devicePath = getDevicePath();
		vaultPath = devicePath + "/Cloud Vault";
		configPath = devicePath + "/config";
		logger.info("vaultPath: " + vaultPath);
		logger.info("configPath: " + configPath);
		if (!Files.exists(Paths.get(vaultPath))) {
			Setup cloudVaultSetup = new Setup(vaultPath, configPath);
			cloudVaultSetup.configureCloudAccess();
		}
		client = new VaultClient(vaultPath, configPath);

		selectedLabel = new JLabel();
		add(selectedLabel, BorderLayout.SOUTH);
		vaultDirTree = getDirBrowser();
		
		sp = new JScrollPane(vaultDirTree);
		add(sp);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setTitle("Cloud Vault");
		this.setSize(700, 700);
		this.setVisible(true);
	}

	class FilePopupMenu extends JPopupMenu {
		/**
		 * appears on right clicking a file
		 */
		private static final long serialVersionUID = 1L;

		public FilePopupMenu(boolean isFile, String path) {

			JMenuItem upload = new JMenuItem("Upload");
			JMenuItem download = new JMenuItem("Download");
			JMenuItem delete = new JMenuItem("Delete");
			JMenuItem sync = new JMenuItem("Sync");
			upload.addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					System.out.println("upload clicked");
					File yourFolder = null;
					JFileChooser fc = new JFileChooser();
					fc.setCurrentDirectory(new java.io.File("."));
					fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					int returnVal = fc.showSaveDialog(fc);
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						yourFolder = fc.getSelectedFile();
					}
					String filePath = Paths.get(yourFolder.getPath())
							.toAbsolutePath().toString();
					client.upload(filePath, path);
					client.sync();
					vaultDirTree = getDirBrowser();
					sp.setViewportView(vaultDirTree);
				}
			});
			download.addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					System.out.println("Download clicked on: " + path);
					try {
						client.download(path);
					} catch (FileNotFoundException e1) {
						logger.error("FileNotFoundException: " + e1);
					}
				}
			});
			delete.addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					System.out.println("Delete clicked on: " + path);
					try {
						client.delete(path);
					} catch (FileNotFoundException e1) {
						logger.error("FileNotFoundException: " + e1);
					}
					client.sync();
					vaultDirTree = getDirBrowser();
					sp.setViewportView(vaultDirTree);
				}
			});
			sync.addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					System.out.println("Sync clicked");
					client.sync();
					vaultDirTree = getDirBrowser();
					sp.setViewportView(vaultDirTree);
				}
			});
			if (!isFile) {
				this.add(upload);
			}
			this.add(download);
			this.add(delete);
			this.add(sync);
		}
	}

	// temporary for testing syncing
	private String getDevicePath() {
		File yourFolder = null;
		JFileChooser fc = new JFileChooser();
		fc.setCurrentDirectory(new java.io.File(".")); // start at application
														// current directory
		fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		int returnVal = fc.showSaveDialog(fc);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			yourFolder = fc.getSelectedFile();
		}
		String devicePath = Paths.get(yourFolder.getPath()).toAbsolutePath()
				.toString();
		logger.info("devicePath: " + devicePath);
		return devicePath;
	}
	private JTree getDirBrowser(){
		JTree vaultDirTree = new JTree(parseFileTree(client.getFileList()));

		vaultDirTree.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e)) {
					TreePath selectedPath = vaultDirTree.getSelectionPath();
					boolean isFile = false;
					String path = "";
					if (selectedPath != null) {
						DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPath
								.getLastPathComponent();
						//TODO take care of empty directories
						isFile = node.isLeaf();
						path = selectedLabel.getText();
					}
					FilePopupMenu menu = new FilePopupMenu(isFile,
							path);
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
		vaultDirTree.setShowsRootHandles(true);
		vaultDirTree.setRootVisible(false);
		vaultDirTree.getSelectionModel().addTreeSelectionListener(
				new TreeSelectionListener() {
					@Override
					public void valueChanged(TreeSelectionEvent e) {
						TreePath selectedPath = vaultDirTree.getSelectionPath();
						System.out.println(selectedPath.toString());
						String path = selectedPath.toString()
								.replaceAll("\\]| |\\[|", "")
								.replaceAll(",", "/");
						int index = path.indexOf("/");
						path = path.substring(index + 1);
						selectedLabel.setText(path);
					}
				});
		return vaultDirTree;
	}
	private DefaultMutableTreeNode parseFileTree(Object[] paths) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Cloud Vault");
		for (Object path : paths) {
			System.out.println((String) path);
			String[] levels = path.toString().split("/");
			DefaultMutableTreeNode currentNode = root;
			for (String level : levels) {
				DefaultMutableTreeNode node = new DefaultMutableTreeNode(level);
				boolean isChild = false;
				int i;
				for (i = 0; i < currentNode.getChildCount(); i++) {
					if (level.equals(currentNode.getChildAt(i).toString())) {
						isChild = true;
						break;
					}
				}
				if (isChild) {
					currentNode = (DefaultMutableTreeNode) currentNode
							.getChildAt(i);
				} else {
					currentNode.add(node);
					currentNode = node;
				}
			}
		}
		return root;
	}
}