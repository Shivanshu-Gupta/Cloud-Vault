package cloudsafe;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

//import static java.nio.file.StandardOpenOption.*;		//for READ, WRITE etc

import java.net.Proxy;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import javax.swing.JOptionPane;

import org.apache.http.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.box.boxjavalibv2.exceptions.AuthFatalFailureException;
import com.box.boxjavalibv2.exceptions.BoxServerException;
import com.box.restclientv2.exceptions.BoxRestException;

import net.fec.openrq.OpenRQ;
import net.fec.openrq.ArrayDataDecoder;
import net.fec.openrq.ArrayDataEncoder;
import net.fec.openrq.EncodingPacket;
import net.fec.openrq.parameters.FECParameters;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.decoder.SourceBlockDecoder;
import cloudsafe.util.Pair;
import cloudsafe.util.PathManip;
import cloudsafe.FolderCloud;
import cloudsafe.Dropbox;
import cloudsafe.Box;
import cloudsafe.GoogleDrive;
import cloudsafe.cloud.Cloud;
//import cloudsafe.cloud.CloudType;
import cloudsafe.cloud.WriteMode;
import cloudsafe.database.*;
import cloudsafe.exceptions.LockNotAcquiredException;

//import cloudsafe.exceptions.AuthenticationException;

/**
 * The entry point for the CloudVault Application.
 */
public class VaultClientDesktop {
	private final static Logger logger = LogManager
			.getLogger(VaultClientDesktop.class.getName());

	String vaultPath = Paths.get("trials/Cloud Vault").toAbsolutePath()
			.toString();
	String configPath = "trials/config";

	Proxy proxy = Proxy.NO_PROXY;
	int cloudNum = 4;
	int cloudDanger = 1; // Cd
	CloudsHandler cloudsHandler;

	Table table;
	long databaseSize;
	int databaseHash;
	String databasePath = configPath + "/table.ser";
	String databaseMetaPath = configPath + "/tablemeta.txt";

	ArrayList<String> currentFiles = new ArrayList<String>();

	public VaultClientDesktop(String vaultPath, String configPath) {
		logger.entry("Setting up VaultClient");
		this.vaultPath = vaultPath;
		this.configPath = configPath;
		this.databasePath = configPath + "/table.ser";
		this.databaseMetaPath = configPath + "/tablemeta.txt";
		logger.info("databasePath: " + databasePath);
		logger.info("databaseMetaPath: " + databaseMetaPath);
		proxy = getProxy();
		int index = 1;
		String cloudConfigPath = configPath + "/clouds.properties";
		Properties defaultProps = new Properties();
		// sets default properties
		defaultProps.setProperty("Number of clouds", "0");	
		Properties cloudConfigProps = new Properties(defaultProps);
		try {
			if(Files.exists(Paths.get(cloudConfigPath))) {
				InputStream inputStream = new FileInputStream(cloudConfigPath);
				cloudConfigProps.load(inputStream);
				inputStream.close();
			}
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(null, "<html>Error loading cloud configuration: "
					+ "cloud config file not found.<br>"
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "<html>Error loading cloud configuration: "
					+ "cloud settings could not be read.<br>"
					+ "</html>", "Error",
					JOptionPane.ERROR_MESSAGE);
			e.printStackTrace();
		}

		ArrayList<Cloud> clouds = new ArrayList<Cloud>();
		int cloudcounter = Integer.parseInt(cloudConfigProps.getProperty("Number of clouds"));
		String cloudID, type, status, code;
		for (int i=0; i < cloudcounter; i++) {
			cloudID = "cloud" + i;
			type = cloudConfigProps.getProperty(cloudID + ".type");
			code = cloudConfigProps.getProperty(cloudID + ".code");
			status = cloudConfigProps.getProperty(cloudID + ".status");
			if(Integer.parseInt(status) == 1) {
				logger.info("adding " + cloudID + " of type: " + type);
				try {
					switch (type) {
					case "Dropbox":
						clouds.add(new Dropbox(cloudID, code, proxy));
						break;
					case "GoogleDrive":
						clouds.add(new GoogleDrive(cloudID, proxy, index++));
						break;
					case "OneDrive":
						clouds.add(new FolderCloud(cloudID, code));
						break;
					case "Box":
						try {
							clouds.add(new Box(cloudID, proxy));
						} catch (BoxRestException | BoxServerException
								| AuthFatalFailureException e) {
							// e.printStackTrace();
						}
						break;
					case "FolderCloud":
						clouds.add(new FolderCloud(cloudID, code));
						break;
					}
				} catch (Exception e) {
					logger.error("couldn't add " + cloudID + " of type: " + type);
				}
			}
		}
		
		cloudsHandler = new CloudsHandler(clouds, configPath);
		cloudNum = clouds.size();
		boolean newUser = cloudsHandler.checkIfNewUser();
		if (newUser)
			createNewTable();
		else {
			if (Files.exists(Paths.get(databasePath))) {
				logger.info("Found a local copy of the database");
				table = new Table(databasePath);
				try (DataInputStream in = new DataInputStream(
						new FileInputStream(databaseMetaPath))) {
					databaseHash = in.readInt();
					databaseSize = in.readLong();
				} catch (FileNotFoundException e) {
					logger.error("tablemeta.txt not found", e);
				} catch (IOException e) {
					logger.error("tablemeta.txt could not be loaded ", e);
					e.printStackTrace();
				}
			} else {
				logger.info("Could not find a local copy of the database. Setting up a new detabase");
				table = new Table();
			}
			downloadTable();
		}
		logger.exit("VaultClient Setup");
	}

	private Proxy getProxy() {
		logger.entry("getting proxy");
		Proxy proxy = Proxy.NO_PROXY;
		try {
			Properties proxySettings = new Properties();
			File configFile = new File(configPath + "/config.properties");
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
		} catch (FileNotFoundException e) {
			logger.error("Proxy Configuration File Not Found", e);
		} catch (IOException e) {
			logger.error("Error while reading config.properties. ",  e);
		}
		logger.exit("proxy configured.");
		return proxy;
	}

	private Pair<FECParameters, Integer> getParams(long fileSize) {
		// epsilon
		int overHead = 4;
		int symSize = (int) Math.round(Math.sqrt((float) fileSize * 8
				/ (float) overHead)); // symbol header length = 8, T =
										// sqrt(D * delta / epsilon)
//		int blockCount = 1;
		int blockCount = (int) Math.ceil((float)fileSize / (float)30000000);
		FECParameters fecParams = FECParameters.newParameters(fileSize,
				symSize, blockCount);

		int blockSize = (int) Math.ceil((float)fileSize / (float)fecParams.numberOfSourceBlocks()); 
		int k = (int) Math.ceil((float) blockSize / (float) symSize);
		// System.out.println("k = " + k);

		// double k_cloud = (int) Math.ceil( (float)k / (float)cloudNum );
		// System.out.println("source symbols/packets per cloud = " +
		// k_cloud);
		float gamma = (float) cloudDanger / (float) cloudNum;
		// System.out.println("Cloud Burst CoefficientL: " + gamma);

		int r = (int) Math.ceil((gamma * k + overHead) / (1 - gamma));
		// System.out.println("r = " + r);

		return Pair.of(fecParams, r);
	}

	public void upload(ArrayList<String> localFilePaths)
			throws LockNotAcquiredException {
		try {
			acquireLock();
		} catch (LockNotAcquiredException e) {
			logger.warn("Could Not acquire lock");
			throw e;
		}
		ArrayList<String> cloudFilePaths = new ArrayList<String>();
		ArrayList<Long> fileSizes = new ArrayList<Long>();
		for (int i = 0; i < localFilePaths.size(); i++) {
			String localFilePath = localFilePaths.get(i);
			Path path = Paths.get(localFilePath).normalize();
			Path temp = Paths.get(vaultPath).relativize(path).getParent();
			String uploadPath = "";
			if (temp != null) {
				uploadPath = temp.toString();
			}
			try {
				long fileSize = -1;
				if (!Files.isDirectory(path)) {
					BasicFileAttributes attrs = null;
					attrs = Files.readAttributes(path,
							BasicFileAttributes.class);
					fileSize = attrs.size();
				}

				String localFileName = path.getFileName().toString();
				String cloudFilePath = null;

				if (uploadPath.length() > 0) {
					cloudFilePath = uploadPath + "/" + localFileName;
				} else {
					cloudFilePath = localFileName;
				}

				currentFiles.add(cloudFilePath);
				cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
				cloudFilePaths.add(cloudFilePath);
				fileSizes.add(fileSize);
			} catch (Exception e) {
				logger.error("Exception uploading " + localFilePath,  e);
				localFilePaths.remove(i);
			}
		}

		downloadTable();
		for (int i = 0; i < currentFiles.size(); i++) {
			table.addNewFile(currentFiles.get(i), fileSizes.get(i));
		}
		uploadTable();
		currentFiles.clear();
		releaseLock();

		for (int i = 0; i < localFilePaths.size(); i++) {
			String localFilePath = localFilePaths.get(i);
			String cloudFilePath = cloudFilePaths.get(i);
			long fileSize = fileSizes.get(i);
			if (!Files.isDirectory(Paths.get(localFilePath))) {
				if (fileSize > 50) {
					uploadFile(localFilePath, cloudFilePath);
				} else {
					uploadTinyFile(localFilePath, cloudFilePath);
				}
			}
		}
	}

	private void acquireLock() throws LockNotAcquiredException {
		logger.trace("Acquiring lock");
		if (!cloudsHandler.acquireLock()) {
			throw new LockNotAcquiredException("lock file not found");
		}
		logger.trace("Lock Acquired");
	}

	private void releaseLock() {
		logger.trace("Releasing Lock");
		cloudsHandler.releaseLock();
		logger.trace("Lock Released");
	}

	public void uploadTinyFile(String localFilePath, String cloudFilePath) {
		try {
			cloudsHandler.uploadFile(localFilePath, cloudFilePath,
					WriteMode.OVERWRITE);
		} catch (IOException e) {
			logger.error("Exception while uploading tiny file " + cloudFilePath, e);
		}
	}

	public void uploadFile(String localFilePath, String cloudFilePath) {
		// logger.entry();
		try {
			Path path = Paths.get(localFilePath);
			byte[] data = Files.readAllBytes(path);
			long fileSize = data.length;
			logger.info("Uploading: " + localFilePath);
			Pair<FECParameters, Integer> params = getParams(fileSize);
			FECParameters fecParams = params.first;
			int symSize = fecParams.symbolSize();
			int blockSize = (int) Math.ceil((float)fileSize / (float)fecParams.numberOfSourceBlocks()); 
			int k = (int) Math.ceil((float) blockSize / (float) symSize);
			int r = params.second;

			ArrayDataEncoder dataEncoder = OpenRQ.newEncoder(data, fecParams);

			int packetID = 0, blockID = 0;
			byte[] packetdata;
			Iterable<SourceBlockEncoder> srcBlkEncoders = dataEncoder
					.sourceBlockIterable();

			for (SourceBlockEncoder srcBlkEnc : srcBlkEncoders) {
				ArrayList<ByteArrayBuffer> dataArrays = new ArrayList<ByteArrayBuffer>(
						cloudNum);
				for (int i = 0; i < cloudNum; i++) {
					int blockDataLength = (k + r) * (symSize + 8);
					dataArrays.add(new ByteArrayBuffer(blockDataLength));
				}

				// using only repair packets and no source packets
				Iterable<EncodingPacket> repPackets = srcBlkEnc
						.repairPacketsIterable(k + r);
				for (EncodingPacket repPack : repPackets) {
					packetdata = repPack.asArray();
					int cloudID = packetID % cloudNum;
					ByteArrayBuffer dataArray = dataArrays.get(cloudID);
					dataArray.append(packetdata, 0, packetdata.length);
					dataArrays.set(cloudID, dataArray);
					packetID++;
				}
				cloudsHandler.uploadFile(dataArrays, cloudFilePath + "_"
						+ blockID, WriteMode.OVERWRITE);
				blockID++;
			}
		} catch (Exception e) {
			logger.error("Exception in uploading File " + cloudFilePath, e);
			// e.printStackTrace();
		}
		// logger.exit();
	}

	public void download(String cloudFilePath) throws FileNotFoundException {
		String writePath = null;
		long fileSize = 0;
		downloadTable();
		if (table.hasFile(cloudFilePath)) {
			writePath = vaultPath + "/" + cloudFilePath;
			fileSize = table.fileSize(cloudFilePath);
			if (fileSize < 0) {
				throw new FileNotFoundException();
			} else {
				try {
					if (Paths.get(writePath).getParent() != null) {
						Files.createDirectories(Paths.get(writePath)
								.getParent());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
				if (fileSize > 50) {
					downloadFile(cloudFilePath, writePath, fileSize);
				} else {
					downloadTinyFile(cloudFilePath, writePath, fileSize);
				}
			}

		} else {
			throw new FileNotFoundException();
		}
	}

	public void downloadTinyFile(String cloudFileName, String writePath,
			long fileSize) {
		try {
			cloudsHandler.downloadFile(writePath, cloudFileName);
		} catch (IOException e) {
			logger.error("error while downloading tiny file to" + writePath,  e);
			// e.printStackTrace();
		}
	}

	public void downloadFile(String cloudFileName, String writePath,
			long fileSize) {
		logger.entry("DownloadFile");
		logger.info("Downloading: " + cloudFileName);
		Pair<FECParameters, Integer> params = getParams(fileSize);
		FECParameters fecParams = params.first;
		int symSize = fecParams.symbolSize();
		int blockID = 0, blockCount = fecParams.numberOfSourceBlocks();
		int packetID = 0, packetCount = 0;
		int packetlength = symSize + 8;
		String blockFileName = null;
		try {
			ArrayList<byte[]> blockdatas;
			byte[] packetdata;
			ArrayDataDecoder dataDecoder = OpenRQ.newDecoder(fecParams, 3);

			// reading in all the packets into a byte[][]
			List<byte[]> packetList = new ArrayList<byte[]>();
			while (blockID < blockCount) {
				blockFileName = cloudFileName + "_" + blockID;
				blockdatas = cloudsHandler.downloadFile(blockFileName);
				for (byte[] blockdata : blockdatas) {
					packetCount = blockdata.length / packetlength;
					for (int j = 0; j < packetCount; j++) {
						packetdata = Arrays.copyOfRange(blockdata, j
								* packetlength, (j + 1) * packetlength);
						packetList.add(packetdata);
					}
				}
				blockID++;
			}
			logger.trace("packets have been downloaded!");
			packetID = 0;
			packetCount = packetList.size();
			while (!dataDecoder.isDataDecoded() && packetID < packetList.size()) {
				byte[] packet = packetList.get(packetID);
				EncodingPacket encPack = dataDecoder.parsePacket(packet, true)
						.value();
				int sbn = encPack.sourceBlockNumber();
				SourceBlockDecoder srcBlkDec = dataDecoder.sourceBlock(sbn);
				srcBlkDec.putEncodingPacket(encPack);
				packetID++;
			}

			byte dataNew[] = dataDecoder.dataArray();
			Path pathNew = Paths.get(writePath);
			Files.write(pathNew, dataNew);
		} catch (Exception e) {
			logger.error("Exception in downloading to " + writePath, e);
			e.printStackTrace();
		}
		logger.exit("DownloadFile");
	}

	public void delete(ArrayList<String> vaultFolderAbsolutePaths)
			throws LockNotAcquiredException {

		logger.entry("delete");
		try {
			acquireLock();
		} catch (LockNotAcquiredException e) {
			logger.warn("Could Not acquire lock");
			throw e;
		}
		// downloadTable();

		ArrayList<String> cloudFilePaths = new ArrayList<String>();
		ArrayList<Long> fileSizes = new ArrayList<Long>();
		for (int i = 0; i < vaultFolderAbsolutePaths.size(); i++) {
			String vaultFolderAbsolutePath = vaultFolderAbsolutePaths.get(i);
			Path path = Paths.get(vaultFolderAbsolutePath).normalize();
			logger.info("Deleting: " + path.toString());
			Path temp = Paths.get(vaultPath).relativize(path).getParent();
			String deletePath = "";
			if (temp != null) {
				deletePath = temp.toString();
			}
			String localFileName = path.getFileName().toString();
			String cloudFilePath = null;
			if (deletePath.length() > 0) {
				cloudFilePath = deletePath + "/" + localFileName;
			} else {
				cloudFilePath = localFileName;
			}

			logger.info("CloudFilePath : " + cloudFilePath.toString());
			if (table.hasFile(cloudFilePath)) {
				currentFiles.add(cloudFilePath);
				fileSizes.add(table.fileSize(cloudFilePath));
				cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
				cloudFilePaths.add(cloudFilePath);

				// cloudFilePaths.add(cloudFilePath);
				// fileSizes.add(table.fileSize(cloudFilePath));
				// currentFiles.add(cloudFilePath);
				// table.removeFile(cloudFilePath);

			} else {
				logger.error("file" + cloudFilePath
						+ "to be deleted not found in database hence skipping");
				vaultFolderAbsolutePaths.remove(i);
			}
		}

		downloadTable();
		for (int i = 0; i < currentFiles.size(); i++) {
			table.removeFile(currentFiles.get(i));
		}

		uploadTable();
		currentFiles.clear();
		releaseLock();

		for (int i = 0; i < cloudFilePaths.size(); i++) {
			long fileSize = fileSizes.get(i);
			String cloudFilePath = cloudFilePaths.get(i);
			if (fileSize < 0) {
				// throw new FileNotFoundException();
			} else if (fileSize > 50) {
				// cloudFilePath = (new
				// PathManip(cloudFilePath)).toCloudFormat();
				Pair<FECParameters, Integer> params = getParams(fileSize);
				FECParameters fecParams = params.first;
				int blockID = 0, blockCount = fecParams.numberOfSourceBlocks();
				String blockFileName = null;
				while (blockID < blockCount) {
					blockFileName = cloudFilePath + "_" + blockID;
					logger.info("Deleteing block file" + blockFileName);
					cloudsHandler.deleteFile(blockFileName);
					blockID++;
				}
			} else {
				cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
				logger.info("Deleteing file" + cloudFilePath);
				cloudsHandler.deleteFile(cloudFilePath);
			}
		}
		logger.exit("delete");
	}

	public void createNewTable() {
		logger.entry("createNewtable");
		try {
			table = new Table();
			table.writeToFile(databasePath);
			byte[] tableBytes = Files.readAllBytes(Paths.get(databasePath));
			databaseSize = tableBytes.length;
			databaseHash = table.hash();
			logger.info("Uploading new Table: Size=" + databaseSize + " Hash="
					+ databaseHash);

			updateTableMetaFile(databaseSize, databaseHash);
			// upload(cloudDatabaseMetaPath);
			uploadTinyFile(databaseMetaPath, "tablemeta.txt");

			// Files.write(Paths.get(cloudDatabasePath), tableBytes, CREATE,
			// WRITE, TRUNCATE_EXISTING);
			// upload(cloudDatabasePath);
			uploadFile(databasePath, "table.ser");
		} catch (Exception x) {
			logger.error("Error while creating new table.", x);
		}
		releaseLock();
		logger.exit("createNewTable");
	}

	public void uploadTable() {
		logger.entry("UploadTable");
		try {
			table.writeToFile(databasePath);
			byte[] tableBytes = Files.readAllBytes(Paths.get(databasePath));
			databaseSize = tableBytes.length;
			databaseHash = table.hash();
			logger.info("Uploading Table: Size=" + databaseSize + " Hash="
					+ databaseHash);

			// this will write to local config folder then copy to local vault
			// no need to upload as watchDir will do that.
			updateTableMetaFile(databaseSize, databaseHash);
			// upload(cloudDatabaseMetaPath);
			uploadTinyFile(databaseMetaPath, "tablemeta.txt");

			// write the table to local vault too;
			// no need to upload as watchDir will do that.
			// Files.write(Paths.get(cloudDatabasePath), tableBytes, CREATE,
			// WRITE, TRUNCATE_EXISTING);
			// upload(cloudDatabasePath);
			uploadFile(databasePath, "table.ser");

		} catch (IOException e) {
			logger.error("Exception while uploading database.", e);
		}
		logger.exit("UploadTable");
	}

	public void downloadTable() {
		logger.entry("DownloadTable");
		boolean databaseChanged = false;
		downloadTinyFile("tablemeta.txt", databaseMetaPath, 12);
		try (DataInputStream in = new DataInputStream(new FileInputStream(
				databaseMetaPath))) {
			int tableHash = in.readInt();
			if (databaseHash != tableHash) {
				databaseChanged = true;
			}
			long tableSize = in.readLong();
			logger.info("Local Table: Size=" + databaseSize + " Hash="
					+ databaseHash);
			logger.info("Table on cloud: Size=" + tableSize + " Hash="
					+ tableHash);
			if (databaseChanged) {
				logger.trace("table hash mismatch: downloading database");
				downloadFile("table.ser", databasePath, tableSize);
				Table newTable = new Table(databasePath);
				sync(newTable);
			}
		} catch (IOException e) {
			logger.error("IOException while downloading table. ", e);
		}
		logger.exit("DownloadTable");
	}

	private void updateTableMetaFile(long tableSize, int tableHash) {
		logger.entry("Update Table Meta");
		logger.info("Table Size:" + tableSize + " Hash:" + tableHash);
		try {
			FileOutputStream fileOut = new FileOutputStream(databaseMetaPath);
			DataOutputStream out = new DataOutputStream(fileOut);
			out.writeInt(tableHash);
			out.writeLong(tableSize);
			out.flush();
			fileOut.flush();

			out.close();
			fileOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		logger.exit("Update Table Meta");
	}

	// public boolean checkIfNewUser() {
	// boolean newUser = true;
	// for (int i = 0; i < clouds.size(); i++) {
	// if (clouds.get(i).searchFile("table.ser_0")) {
	// logger.trace("Found table.ser");
	// newUser = false;
	// break;
	// }
	// }
	// return newUser;
	// }

	public Pair<ArrayList<String>, ArrayList<String>> sync(Table newTable) {
		logger.entry("syncing local and downloaded table.");
		logger.trace("Syncing with new table");
		Object[] localFiles = table.getFileList();
		Object[] filesInVault = newTable.getFileList();
		logger.info("no of table entries locally: " + localFiles.length);
		logger.info("no of table entries on cloud: " + filesInVault.length);
		ArrayList<String> downloads = new ArrayList<String>();
		ArrayList<String> deletes = new ArrayList<String>();
		for (Object file : filesInVault) {
			String fileName = (String) file;
			if (!currentFiles.contains(fileName)) {
				if (!table.hasFile(fileName)
						|| (table.lastModified(fileName).before(newTable
								.lastModified(fileName)))) {
					logger.info("Sync -- File to be updated: " + fileName);
					Path filePath = Paths.get(vaultPath + "/" + fileName);
					downloads.add(filePath.toString());
					table.addNewFile(fileName, newTable.fileSize(fileName));
					if (newTable.fileSize(fileName) > 0) {
						try {
							Files.createDirectories(filePath.getParent());
						} catch (IOException e) {
							logger.error("sync -- unable to create directories to download file into");
						}
						downloadFile(fileName, filePath.toString(),
								newTable.fileSize(fileName));
					}
				}
			}
		}
		for (Object file : localFiles) {
			String fileName = (String) file;
			if (!currentFiles.contains(fileName) && !newTable.hasFile(fileName)) {
				logger.info("Sync -- File to be deleted: " + fileName);
				try {
					Path filePath = Paths.get(vaultPath + "/" + fileName);
					deletes.add(filePath.toString());
					table.removeFile(fileName);
					if (Files.exists(filePath)) {
						if (table.fileSize(fileName) > 0)
							Files.delete(filePath);
						else {
							Files.walkFileTree(filePath,
									new SimpleFileVisitor<Path>() {
										@Override
										public FileVisitResult visitFile(
												Path file,
												BasicFileAttributes attrs)
												throws IOException {
											Files.delete(file);
											return FileVisitResult.CONTINUE;
										}

										@Override
										public FileVisitResult postVisitDirectory(
												Path dir, IOException e)
												throws IOException {
											Files.delete(dir);
											return FileVisitResult.CONTINUE;
										}
									});
						}
					}
				} catch (IOException e) {
					logger.error("sync -- unable to delete " + fileName, e);
				}
			}
		}
		logger.exit("syncing done");
		return Pair.of(downloads, deletes);
	}

	public Pair<ArrayList<String>, ArrayList<String>> sync() {
		logger.trace("starting Sync");
		Pair<ArrayList<String>, ArrayList<String>> changes = null;
		boolean databaseChanged = false;
		downloadTinyFile("tablemeta.txt", databaseMetaPath, 12);
		try (DataInputStream in = new DataInputStream(new FileInputStream(
				databaseMetaPath))) {
			int tableHash = in.readInt();
			if (databaseHash != tableHash) {
				databaseChanged = true;
			}
			long tableSize = in.readLong();
			logger.info("Local Table: Size=" + databaseSize + " Hash="
					+ databaseHash);
			logger.info("Table on cloud: Size=" + tableSize + " Hash="
					+ tableHash);
			if (databaseChanged) {
				logger.trace("table hash mismatch: downloading database");
				downloadFile("table.ser", databasePath, tableSize);
				Table newTable = new Table(databasePath);
				changes = sync(newTable);
			}
		} catch (IOException e) {
			logger.error("sync error. " , e);
		}
		logger.trace("Sync done;");
		return changes;
	}

	public void shutdown() {
		cloudsHandler.shutdown();
	}
}
