package cloudsafe;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.net.Proxy;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

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
public class VaultClient {
	private final static Logger logger = LogManager
			.getLogger(VaultClientDesktop.class.getName());

	String vaultPath = Paths.get("trials/Cloud Vault").toAbsolutePath()
			.toString();
	String configPath = "trials/config";
	String cloudMetadataPath = configPath + "/cloudmetadata.ser";
	static int cloudNum = 4; // Co
	static int cloudDanger = 1; // Cd
	final static int overHead = 4; // epsilon
	Proxy proxy = Proxy.NO_PROXY;

	ArrayList<Cloud> clouds = new ArrayList<Cloud>();
	ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();
	Table table;

	long databaseSize;
	int databaseHash;
	String localDatabasePath = configPath + "/table.ser";
	String localDatabaseMetaPath = configPath + "/tablemeta.txt";

	@SuppressWarnings("unchecked")
	public VaultClient(String vaultPath, String configPath) {
		logger.entry("Setting up VaultClient");
//		 this.vaultPath = Paths.get(vaultPath).toAbsolutePath().toString();
		this.vaultPath = vaultPath;
		this.configPath = configPath;
		this.cloudMetadataPath = configPath + "/cloudmetadata.ser";
		this.localDatabasePath = configPath + "/table.ser";
		this.localDatabaseMetaPath = configPath + "/tablemeta.txt";
		logger.info("cloudMetadataPath: " + cloudMetadataPath);
		logger.info("localDatabasePath: " + localDatabasePath);
		logger.info("localDatabaseMetaPath: " + localDatabaseMetaPath);
		proxy = getProxy();
		try {
			int index = 1;
			FileInputStream fileIn = new FileInputStream(cloudMetadataPath);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			cloudMetaData = (ArrayList<Pair<String, String>>) in.readObject();

			for (Pair<String, String> metadata : cloudMetaData) {
				logger.info("adding cloud: " + metadata.first);
				switch (metadata.first) {
				case "dropbox":
					clouds.add(new Dropbox(metadata.second, proxy));
					break;
				case "googledrive":
					clouds.add(new GoogleDrive(proxy, index++));
					break;
				case "onedrive":
					clouds.add(new FolderCloud(metadata.second));
					break;
				case "box":
					clouds.add(new Box(proxy));
					break;
				case "folder":
					clouds.add(new FolderCloud(metadata.second));
					break;
				}
			}
			in.close();
			fileIn.close();
		} catch (IOException x) {
			logger.error("IOException: " + x);
			// x.printStackTrace();
		} catch (ClassNotFoundException cfe) {
			logger.error("ClassNotFoundException: " + cfe);
			// cfe.printStackTrace();
		} catch (BoxRestException | BoxServerException
				| AuthFatalFailureException e) {
			// e.printStackTrace();
		}

		boolean newUser = checkIfNewUser();
		if (newUser)
			createNewTable();
		else {
			if(Files.exists(Paths.get(localDatabasePath))){
				table = new Table(localDatabasePath);
			} else {
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
			logger.error("Proxy Configuretion File Not Found");
		} catch (IOException e) {
			logger.error("IOException: " + e);
		}
		logger.exit("proxy configured.");
		return proxy;
	}

	private Pair<FECParameters, Integer> getParams(long fileSize) {
		Pair<FECParameters, Integer> params = null;
		try {
			int symSize = (int) Math.round(Math.sqrt((float) fileSize * 8
					/ (float) overHead)); // symbol header length = 8, T =
											// sqrt(D * delta / epsilon)
			int blockCount = 1;
			FECParameters fecParams = FECParameters.newParameters(fileSize,
					symSize, blockCount);

			int k = (int) Math.ceil((float) fileSize / (float) symSize);
			// System.out.println("k = " + k);

			// double k_cloud = (int) Math.ceil( (float)k / (float)cloudNum );
			// System.out.println("source symbols/packets per cloud = " +
			// k_cloud);

			float gamma = (float) cloudDanger / (float) cloudNum;
			// System.out.println("Cloud Burst CoefficientL: " + gamma);

			int r = (int) Math.ceil((gamma * k + overHead) / (1 - gamma));
			// System.out.println("r = " + r);

			params = Pair.of(fecParams, r);
		} catch (Exception x) {
			logger.error("Exception: " + x);
		}
		return params;
	}

	public void upload(String localFilePath, String uploadPath) {
		Path path = Paths.get(localFilePath).normalize();
		try {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				String parent = uploadPath;

				@Override
				public FileVisitResult visitFile(Path file,
						BasicFileAttributes attrs) throws IOException {
					long fileSize = attrs.size();
					String cloudFilePath = file.getFileName().toString();
					if (parent.length() > 0) {
						cloudFilePath = parent + "/" + cloudFilePath;
					}
					logger.info(cloudFilePath);
					
					downloadTable();
					try {
						acquireLock();
					} catch (LockNotAcquiredException e) {
						// TODO handle this exception
						logger.error("Could Not acquire lock");
					}
					if (path == file) {
						int version = table.version(cloudFilePath);
						if (version > 0) {
							cloudFilePath = cloudFilePath + " ("
									+ table.version(cloudFilePath) + ")";
						}
					}
					// no need to append version as inside folder
					table.addNewFile(cloudFilePath, fileSize);
					cloudFilePath = (new PathManip(cloudFilePath))
							.toCloudFormat();
					uploadTable();
					releaseLock();
					if (fileSize > 50) {
						uploadFile(file.toString(), cloudFilePath);
					} else {
						uploadTinyFile(file.toString(), cloudFilePath);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path dir,
						BasicFileAttributes attrs) throws IOException {
					String cloudFilePath = dir.getFileName().toString();
					if (parent.length() > 0) {
						cloudFilePath = parent + "/" + cloudFilePath;
					}
					logger.info(cloudFilePath);
					downloadTable();
					try {
						acquireLock();
					} catch (LockNotAcquiredException e) {
						// TODO handle this exception
						logger.error("Could Not acquire lock");
					}
					if (path == dir) {
						int version = table.version(cloudFilePath);
						if (version > 0) {
							cloudFilePath = cloudFilePath + " ("
									+ table.version(cloudFilePath) + ")";
						}
					}
					table.addNewFile(cloudFilePath, -1);
					parent = cloudFilePath;
					uploadTable();
					releaseLock();
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir,
						IOException e) throws IOException {
					if (dir != path) {
						int index = parent.lastIndexOf("/");
						parent = parent.substring(0, index);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void acquireLock() throws LockNotAcquiredException {
		logger.trace("Acquiring lock");
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable()) {
				if(cloud.searchFile("tablelock")) {
					logger.trace("Deleting in cloud " + i);
					cloud.deleteFile("tablelock");
				} else {
					releaseLock();
					throw new LockNotAcquiredException("lock file not found");
				}
			}
		}
		logger.trace("Lock Acquired");
	}
	
	private void releaseLock() {
		logger.trace("Releasing Lock");
		byte[] lock = {};
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable()) {
				try {
					cloud.uploadFile(lock,
							"tablelock",
							WriteMode.OVERWRITE);
				} catch (IOException e) {
					//TODO handle this exception
					logger.error("IOException: " + e);
				}
			}
		}
		logger.trace("Lock Released");
	}

	public void uploadTinyFile(String localFilePath, String cloudFilePath) {
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable()) {
				try {
					cloud.uploadFile(localFilePath, cloudFilePath,
							WriteMode.OVERWRITE);
				} catch (IOException e) {
					logger.error("IOException: " + e);
					// e.printStackTrace();
				}
			}
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
			int k = (int) Math.ceil((float) fileSize / (float) symSize);
			int r = params.second;

			ArrayDataEncoder dataEncoder = OpenRQ.newEncoder(data, fecParams);

			int packetID = 0, blockID = 0;
			byte[] packetdata;
			Iterable<SourceBlockEncoder> srcBlkEncoders = dataEncoder
					.sourceBlockIterable();

			for (SourceBlockEncoder srcBlkEnc : srcBlkEncoders) {
				ArrayList<ByteArrayBuffer> dataArrays = new ArrayList<ByteArrayBuffer>(
						clouds.size());
				for (int i = 0; i < clouds.size(); i++) {
					int blockDataLength = (k + r) * (symSize + 8);
					dataArrays.add(new ByteArrayBuffer(blockDataLength));
				}

				// using only repair packets and no source packets
				Iterable<EncodingPacket> repPackets = srcBlkEnc
						.repairPacketsIterable(k + r);
				for (EncodingPacket repPack : repPackets) {
					packetdata = repPack.asArray();
					int cloudID = packetID % cloudNum;
					Cloud cloud = clouds.get(cloudID);

					if (cloud.isAvailable()) {
						ByteArrayBuffer dataArray = dataArrays.get(cloudID);
						dataArray.append(packetdata, 0, packetdata.length);
						dataArrays.set(cloudID, dataArray);
					}
					packetID++;
				}
				for (int i = 0; i < clouds.size(); i++) {
					Cloud cloud = clouds.get(i);
					if (cloud.isAvailable()) {
						cloud.uploadFile(dataArrays.get(i).toByteArray(),
								cloudFilePath + "_" + blockID,
								WriteMode.OVERWRITE);
					}
				}
				blockID++;
			}
		} catch (Exception e) {
			logger.error("Exception in upload: " + e);
			e.printStackTrace();
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
				try {
					Files.createDirectories(Paths.get(writePath));
					// appending a slash to ensure that only children have there
					// paths as the prefix
					Iterable<FileMetadata> childrendata = table
							.getChildren(cloudFilePath + "/");
					for (FileMetadata childdata : childrendata) {
						logger.trace("Child: " + childdata.fileName());
						download(childdata.fileName());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
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
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable()) {
				try {
					cloud.downloadFile(writePath, cloudFileName);
					break;
				} catch (IOException e) {
					logger.error("IOException: " + e);
					// e.printStackTrace();
				}
			}
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
			byte[] blockdata;
			byte[] packetdata;
			ArrayDataDecoder dataDecoder = OpenRQ.newDecoder(fecParams, 3);

			// reading in all the packets into a byte[][]
			List<byte[]> packetList = new ArrayList<byte[]>();
			while (blockID < blockCount) {
				blockFileName = cloudFileName + "_" + blockID;
				for (int i = 0; i < clouds.size(); i++) {
					Cloud cloud = clouds.get(i);
					if (cloud.isAvailable() && cloud.searchFile(blockFileName)) {
						blockdata = cloud.downloadFile(blockFileName);
						packetCount = blockdata.length / packetlength;
						for (int j = 0; j < packetCount; j++) {
							packetdata = Arrays.copyOfRange(blockdata, j
									* packetlength, (j + 1) * packetlength);
							packetList.add(packetdata);
						}
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
			logger.error("Exception in download: " + e);
			e.printStackTrace();
		}
		logger.exit("DownloadFile");
	}

	public void delete(String cloudFilePath) throws FileNotFoundException {
		logger.entry("delete");
		long fileSize = 0;
		if (table.hasFile(cloudFilePath)) {
			fileSize = table.fileSize(cloudFilePath);
			downloadTable();
			try {
				acquireLock();
			} catch (LockNotAcquiredException e) {
				// TODO handle this exception
				logger.error("Could Not acquire lock");
			}
			table.removeFile(cloudFilePath);
			uploadTable();
			releaseLock();
			if (fileSize < 0) {
				try {
					Iterable<FileMetadata> childrendata = table
							.getChildren(cloudFilePath + "/");
					for (FileMetadata childdata : childrendata) {
						logger.trace("Child: " + childdata.fileName());
						delete(childdata.fileName());
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (fileSize > 50) {
				cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
				Pair<FECParameters, Integer> params = getParams(fileSize);
				FECParameters fecParams = params.first;
				int blockID = 0, blockCount = fecParams.numberOfSourceBlocks();
				String blockFileName = null;
				while (blockID < blockCount) {
					blockFileName = cloudFilePath + "_" + blockID;
					logger.info("Deleteing block file" + blockFileName);
					for (int i = 0; i < clouds.size(); i++) {
						Cloud cloud = clouds.get(i);
						if (cloud.isAvailable()
								&& cloud.searchFile(blockFileName)) {
							logger.trace("Deleting in cloud " + i);
							cloud.deleteFile(blockFileName);
						}
					}
					blockID++;
				}
			} else {
				cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
				for (int i = 0; i < clouds.size(); i++) {
					Cloud cloud = clouds.get(i);
					if (cloud.isAvailable() && cloud.searchFile(cloudFilePath)) {
						logger.trace("Deleting in cloud " + i);
						cloud.deleteFile(cloudFilePath);
					}
				}
			}
		} else {
			throw new FileNotFoundException();
		}
		logger.exit("delete");
	}

	public void createNewTable() {
		logger.entry("createNewtable");
		try {
			table = new Table();
			table.writeToFile(localDatabasePath);
			byte[] tableBytes = Files
					.readAllBytes(Paths.get(localDatabasePath));
			databaseSize = tableBytes.length;
			databaseHash = table.hash();
			logger.info("Uploading new Table: Size=" + databaseSize + " Hash="
					+ databaseHash);

			updateTableMetaFile(databaseSize, databaseHash);
			// upload(cloudDatabaseMetaPath);
			uploadFile(localDatabaseMetaPath, "tablemeta.txt");

			// Files.write(Paths.get(cloudDatabasePath), tableBytes, CREATE,
			// WRITE, TRUNCATE_EXISTING);
			// upload(cloudDatabasePath);
			uploadFile(localDatabasePath, "table.ser");
		} catch (Exception x) {
			logger.error("Exception in creating new table: " + x);
		}
		releaseLock();
		logger.exit("createNewTable");
	}

	public void uploadTable() {
		logger.entry("UploadTable");
		try {
			table.writeToFile(localDatabasePath);
			byte[] tableBytes = Files
					.readAllBytes(Paths.get(localDatabasePath));
			databaseSize = tableBytes.length;
			databaseHash = table.hash();
			logger.info("Uploading Table: Size=" + databaseSize + " Hash="
					+ databaseHash);

			// this will write to local config folder then copy to local vault
			// no need to upload as watchDir will do that.
			updateTableMetaFile(databaseSize, databaseHash);
			// upload(cloudDatabaseMetaPath);
			uploadFile(localDatabaseMetaPath, "tablemeta.txt");

			// write the table to local vault too;
			// no need to upload as watchDir will do that.
			// Files.write(Paths.get(cloudDatabasePath), tableBytes, CREATE,
			// WRITE, TRUNCATE_EXISTING);
			// upload(cloudDatabasePath);
			uploadFile(localDatabasePath, "table.ser");

		} catch (IOException e) {
			logger.error("Exception while uploading database" + e);
		}
		logger.exit("UploadTable");
	}

	public void downloadTable() {
		logger.entry("DownloadTable");
		boolean databaseChanged = false;
		downloadFile("tablemeta.txt", localDatabaseMetaPath, 12);
		try (DataInputStream in = new DataInputStream(new FileInputStream(
				localDatabaseMetaPath))) {
			// no need to copy database files from vaultPath to configPath.
			// Files.copy(Paths.get(cloudDatabaseMetaPath),
			// Paths.get(localDatabaseMetaPath), REPLACE_EXISTING);
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
				downloadFile("table.ser", localDatabasePath, tableSize);
				// Files.copy(Paths.get(cloudDatabasePath),
				// Paths.get(localDatabasePath), REPLACE_EXISTING);
				Table newTable = new Table(localDatabasePath);
				sync(newTable);
			}
		} catch (IOException e) {
			logger.error("IOException: " + e);
		}
		logger.exit("DownloadTable");
	}

	private void updateTableMetaFile(long tableSize, int tableHash) {
		logger.entry("Update Table Meta");
		logger.info("Table Size:" + tableSize + " Hash:" + tableHash);
		try {
			FileOutputStream fileOut = new FileOutputStream(
					localDatabaseMetaPath);
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

	public boolean checkIfNewUser() {
		boolean newUser = true;
		for (int i = 0; i < clouds.size(); i++) {
			if (clouds.get(i).searchFile("table.ser_0")) {
				logger.trace("Found table.ser");
				newUser = false;
				break;
			}
		}
		return newUser;
		// return !Files.exists(Paths.get(localDatabasePath));
	}

	public Object[] getFileList() {
		downloadTable();
		return table.getFileList();
	}

	private void sync(Table newTable) {
		logger.entry("syncing local and downloaded table.");
		logger.trace("Syncing with new table");
		Object[] localFiles = table.getFileList();
		Object[] filesInVault = newTable.getFileList();
		for (Object file : filesInVault) {
			if (!table.hasFile((String) file)) {
				logger.info("File in Cloud not present locally: "
						+ (String) file);
				table.addNewFile((String) file,
						newTable.fileSize((String) file));
				try {
					download((String) file);
				} catch (FileNotFoundException e) {
					logger.error(e);
				}
			}
		}
		for (Object file : localFiles) {
			if (!newTable.hasFile((String) file)) {
				logger.info("File present locally not in Cloud : "
						+ (String) file);
				try {
					Path filePath = Paths.get(vaultPath + "/" + (String) file);
					Files.delete(filePath);
				} catch (IOException e) {
					logger.error(e);
				}
			}
		}
		logger.exit("syncing done");
	}

	public void sync() {
		logger.trace("starting Sync");
		downloadTable();
		logger.trace("Sync done;");
	}
}
