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

	String currentFile = "";

	@SuppressWarnings("unchecked")
	public VaultClientDesktop(String vaultPath, String configPath) {
		logger.entry("Setting up VaultClient");
		this.vaultPath = vaultPath;
		this.configPath = configPath;
		this.databasePath = configPath + "/table.ser";
		this.databaseMetaPath = configPath + "/tablemeta.txt";
		logger.info("databasePath: " + databasePath);
		logger.info("databaseMetaPath: " + databaseMetaPath);
		proxy = getProxy();
		try {
			int index = 1;
			String cloudMetadataPath = configPath + "/cloudmetadata.ser";
			FileInputStream fileIn = new FileInputStream(cloudMetadataPath);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			ArrayList<Pair<String, String>> cloudMetaData = (ArrayList<Pair<String, String>>) in
					.readObject();

			ArrayList<Cloud> clouds = new ArrayList<Cloud>();
			for (Pair<String, String> metadata : cloudMetaData) {
				logger.info("adding cloud: " + metadata.first);
				try {
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
						try {
							clouds.add(new Box(proxy));
						} catch (BoxRestException | BoxServerException
								| AuthFatalFailureException e) {
							// e.printStackTrace();
						}
						break;
					case "folder":
						clouds.add(new FolderCloud(metadata.second));
						break;
					}
				} catch (Exception e) {
					logger.error("couldn't add cloud " + metadata.first + " error:" + e);
				}
			}
			in.close();
			fileIn.close();
			cloudsHandler = new CloudsHandler(clouds, configPath);
			cloudNum = clouds.size();
		} catch (IOException x) {
			logger.error("IOException: " + x);
			// x.printStackTrace();
		} catch (ClassNotFoundException cfe) {
			cfe.printStackTrace();
		}

		boolean newUser = cloudsHandler.checkIfNewUser();
		if (newUser)
			createNewTable();
		else {
			if(Files.exists(Paths.get(databasePath))) {
				logger.info("Found a local copy of the database");
				table = new Table(databasePath);
				try(DataInputStream in = new DataInputStream(new FileInputStream(
						databaseMetaPath))) {
					databaseHash = in.readInt();
					databaseSize = in.readLong();
				} catch (FileNotFoundException e) {
					logger.error("tablemeta.txt not found" + e);
				} catch (IOException e) {
					logger.error("tablemeta.txt could not be loaded " + e);
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
			// epsilon
			int overHead = 4;
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

	public void upload(String localFilePath) throws NoSuchFileException {
		Path path = Paths.get(localFilePath).normalize();
		Path temp = Paths.get(vaultPath).relativize(path).getParent();
		String uploadPath = "";
		if (temp != null) {
			uploadPath = temp.toString();
		}

		long fileSize = -1;
		if (!Files.isDirectory(path)) {
			BasicFileAttributes attrs = null;
			try {
				attrs = Files.readAttributes(path, BasicFileAttributes.class);
			} catch (Exception e) {
				throw new NoSuchFileException(path.toString());
			}
			fileSize = attrs.size();
		}

		String localFileName = path.getFileName().toString();
		String cloudFilePath = null;

		if (uploadPath.length() > 0) {
			cloudFilePath = uploadPath + "/" + localFileName;
		} else {
			cloudFilePath = localFileName;
		}
		try {
			acquireLock();
		} catch (LockNotAcquiredException e) {
			// TODO handle this exception
			logger.error("Could Not acquire lock");
		}
		currentFile = cloudFilePath;
		downloadTable();
		table.addNewFile(cloudFilePath, fileSize);
		cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
		uploadTable();
		currentFile = "";
		releaseLock();
		if (!Files.isDirectory(path)) {
			if (fileSize > 50) {
				uploadFile(localFilePath, cloudFilePath);
			} else {
				uploadTinyFile(localFilePath, cloudFilePath);
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
			logger.error("IOException: " + e);
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
			logger.error("Exception in upload: " + e);
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
			logger.error("IOException: " + e);
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
			logger.error("Exception in download: " + e);
			e.printStackTrace();
		}
		logger.exit("DownloadFile");
	}

	public void delete(String vaultFolderAbsolutePath)
			throws FileNotFoundException {
		logger.entry("delete");
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

		long fileSize = 0;

		logger.info("CloudFilePath : " + cloudFilePath.toString());
		if (table.hasFile(cloudFilePath)) {
			fileSize = table.fileSize(cloudFilePath);
			try {
				acquireLock();
			} catch (LockNotAcquiredException e) {
				// TODO handle this exception
				logger.error("Could Not acquire lock");
			}
			downloadTable();
			table.removeFile(cloudFilePath);
			uploadTable();
			releaseLock();
			if (fileSize < 0) {
				// throw new FileNotFoundException();
			} else if (fileSize > 50) {
				cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
				Pair<FECParameters, Integer> params = getParams(fileSize);
				FECParameters fecParams = params.first;
				int blockID = 0, blockCount = fecParams.numberOfSourceBlocks();
				String blockFileName = null;
				while (blockID < blockCount) {
					blockFileName = cloudFilePath + "_" + blockID;
					logger.info("Deleteing block file" + blockFileName);
					// for (int i = 0; i < clouds.size(); i++) {
					// Cloud cloud = clouds.get(i);
					// if (cloud.isAvailable()
					// && cloud.searchFile(blockFileName)) {
					// logger.trace("Deleting in cloud " + i);
					// cloud.deleteFile(blockFileName);
					// }
					// }
					cloudsHandler.deleteFile(blockFileName);
					blockID++;
				}
			} else {
				cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
				logger.info("Deleteing file" + cloudFilePath);
				// for (int i = 0; i < clouds.size(); i++) {
				// Cloud cloud = clouds.get(i);
				// if (cloud.isAvailable() && cloud.searchFile(cloudFilePath)) {
				// logger.trace("Deleting in cloud " + i);
				// cloud.deleteFile(cloudFilePath);
				// }
				// }
				cloudsHandler.deleteFile(cloudFilePath);
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
			table.writeToFile(databasePath);
			byte[] tableBytes = Files
					.readAllBytes(Paths.get(databasePath));
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
			logger.error("Exception in creating new table: " + x);
		}
		releaseLock();
		logger.exit("createNewTable");
	}

	public void uploadTable() {
		logger.entry("UploadTable");
		try {
			table.writeToFile(databasePath);
			byte[] tableBytes = Files
					.readAllBytes(Paths.get(databasePath));
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
			logger.error("Exception while uploading database" + e);
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
			logger.error("IOException: " + e);
		}
		logger.exit("DownloadTable");
	}

	private void updateTableMetaFile(long tableSize, int tableHash) {
		logger.entry("Update Table Meta");
		logger.info("Table Size:" + tableSize + " Hash:" + tableHash);
		try {
			FileOutputStream fileOut = new FileOutputStream(
					databaseMetaPath);
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
			if (!table.hasFile((String) file) && !currentFile.equals(file)) {
				logger.info("File in Cloud not present locally: "
						+ (String) file);
				Path filePath = Paths.get(vaultPath + "/" + (String) file);
				downloads.add(filePath.toString());
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
					deletes.add(filePath.toString());
					Files.delete(filePath);
				} catch (IOException e) {
					logger.error(e);
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
			logger.error("IOException: " + e);
		}
		logger.trace("Sync done;");
		return changes;
	}
}
