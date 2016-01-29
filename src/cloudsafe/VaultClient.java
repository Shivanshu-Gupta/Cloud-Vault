package cloudsafe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import net.fec.openrq.ArrayDataDecoder;
import net.fec.openrq.ArrayDataEncoder;
import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.decoder.SourceBlockDecoder;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.parameters.FECParameters;

import org.apache.http.util.ByteArrayBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.cloud.Box;
import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.CloudMeta;
import cloudsafe.cloud.Dropbox;
import cloudsafe.cloud.FolderCloud;
import cloudsafe.cloud.GoogleDrive;
import cloudsafe.cloud.WriteMode;
import cloudsafe.database.Database;
import cloudsafe.database.FileMetadata;
import cloudsafe.database.TempDatabase;
import cloudsafe.exceptions.DatabaseException;
import cloudsafe.exceptions.LockNotAcquiredException;
import cloudsafe.exceptions.SetupException;
import cloudsafe.util.Pair;
import cloudsafe.util.PathManip;
import cloudsafe.util.UserProxy;

import com.box.boxjavalibv2.exceptions.AuthFatalFailureException;
import com.box.boxjavalibv2.exceptions.BoxServerException;
import com.box.restclientv2.exceptions.BoxRestException;
import com.google.gson.Gson;

//import cloudsafe.exceptions.AuthenticationException;

/**
 * The entry point for the CloudVault Application.
 */
public class VaultClient {
	private final static Logger logger = LogManager.getLogger(VaultClient.class
			.getName());

	String vaultPath = Paths.get("trials/Cloud Vault").toAbsolutePath()
			.toString();
	String configPath = "trials/config";

	Proxy proxy = Proxy.NO_PROXY;
	int cloudNum = 4;
	int cloudDanger = 1; // Cd
	CloudsHandler cloudsHandler;

	final String DB_META = "dbmeta.txt";
	final String DBNAME = Database.DBNAME;

	Database db = null;
	long localDBSize = -1;
	byte[] localDBHash = new byte[16];

	ArrayList<String> currentFiles = new ArrayList<String>();

	public VaultClient(String vaultPath, String configPath)
			throws DatabaseException {
		logger.entry("Setting up VaultClient");
		this.vaultPath = vaultPath;
		this.configPath = configPath;

		String databasePath = configPath + "/" + DBNAME;
		String databaseMetaPath = configPath + "/" + DB_META;

		// passing in configPath only for debug purposes.
		try {
			this.db = Database.getInstance(databasePath);
		} catch (SQLException e) {
			throw new DatabaseException(
					"Could not open connection to database.", e);
		}

		logger.info("databasePath: " + databasePath);
		logger.info("databaseMetaPath: " + databaseMetaPath);

		proxy = UserProxy.getProxy();
		int index = 1;

		ArrayList<Cloud> clouds = new ArrayList<>();
		ArrayList<CloudMeta> cloudMetas = new ArrayList<>();
		Setup cloudVaultSetup = new Setup(vaultPath, configPath);
		cloudMetas = cloudVaultSetup.cloudMetas;

		CloudMeta cloudMeta;
		String cloudType;
		for (int i = 0; i < cloudMetas.size(); i++) {
			cloudMeta = cloudMetas.get(i);
			cloudType = cloudMeta.getType();
			logger.info("adding cloud " + i + " of type: " + cloudType);
			try {
				switch (cloudType) {
				case Dropbox.NAME:
					clouds.add(new Dropbox(cloudMeta.getMeta().get(
							"accesstoken"), proxy));
					break;
				case GoogleDrive.NAME:
					clouds.add(new GoogleDrive(proxy, index++));
					break;
				case "ONEDRIVE":
					clouds.add(new FolderCloud(cloudMeta.getMeta().get("path")));
					break;
				case Box.NAME:
					try {
						clouds.add(new Box(proxy));
					} catch (BoxRestException | BoxServerException
							| AuthFatalFailureException e) {
						// e.printStackTrace();
					}
					break;
				case FolderCloud.NAME:
					clouds.add(new FolderCloud(cloudMeta.getMeta().get("path")));
					break;
				}
			} catch (Exception e) {
				logger.error("couldn't add cloud " + i + " of type: "
						+ cloudType);
			}
		}

		cloudsHandler = new CloudsHandler(clouds, cloudMetas, configPath);
		cloudNum = clouds.size();
	}

	private Pair<FECParameters, Integer> getParams(long fileSize) {
		// epsilon
		int overHead = 4;
		int symSize = (int) Math.round(Math.sqrt((float) fileSize * 8
				/ (float) overHead)); // symbol header length = 8, T =
										// sqrt(D * delta / epsilon)
										// int blockCount = 1;
		int blockCount = (int) Math.ceil((float) fileSize / (float) 30000000);
		FECParameters fecParams = FECParameters.newParameters(fileSize,
				symSize, blockCount);

		int blockSize = (int) Math.ceil((float) fileSize
				/ (float) fecParams.numberOfSourceBlocks());
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
		logger.debug("upload : Acquiring Lock...");
		try {
			acquireLock();
		} catch (LockNotAcquiredException e) {
			logger.warn("Could Not acquire lock");
			throw e;
		}
		ArrayList<String> cloudFilePaths = new ArrayList<>();
		ArrayList<Long> fileSizes = new ArrayList<>();
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
				logger.error("upload : Could not fetch file attributes for "
						+ localFilePath, e);
				localFilePaths.remove(i);
			}
		}

		logger.info("upload : Downloading Database...");
		try {
			downloadTable();
		} catch (IOException e) {
			logger.error("upload : Could not download database.", e);
			logger.warn("Cancelling upload operation...");
			return;
		} catch (SQLException e) {
			logger.error("delete : Could not open downloaded Database.", e);
			logger.warn("Cancelling upload operation... ");
			return;
		}
		for (int i = 0; i < cloudFilePaths.size(); i++) {
			String localFilePath = localFilePaths.get(i);
			String cloudFilePath = cloudFilePaths.get(i);
			long fileSize = fileSizes.get(i);
			String[] cloudsUsed = {};
			if (fileSize > 0) {
				// fileSize > 0 implies it's a file not a folder
				if (fileSize > 50) {
					cloudsUsed = uploadFile(localFilePath, cloudFilePath);
				} else {
					cloudsUsed = uploadTinyFile(localFilePath, cloudFilePath);
				}
			}

			if (cloudsUsed != null) {
				Gson gson = new Gson();
				String cloudListString = gson.toJson(cloudsUsed);
				int minClouds = cloudsUsed.length - cloudDanger;
				FileMetadata file = new FileMetadata(cloudFilePath, fileSize,
						cloudListString, minClouds);
				try {
					HashSet<FileMetadata> prevRecords = db.getFileRecords(cloudFilePath);
					if(prevRecords.isEmpty()){
						db.insertFileRecord(file);
					} else {
						db.updateFileRecord(file);
					}
				} catch (SQLException e) {
					logger.error("Error inserting file: " + cloudFilePath, e);
				}
			}
		}
		try {
			uploadTable();
		} catch (DatabaseException e) {
			logger.error("Error uploading Database", e);
		}
		currentFiles.clear();
		releaseLock();
	}

	public void upload(String localFilePath) {
		Path path = Paths.get(localFilePath).normalize();
		Path temp = Paths.get(vaultPath).relativize(path).getParent();
		String uploadPath = "";
		String cloudFilePath = null;
		long fileSize = -1;
		if (temp != null) {
			uploadPath = temp.toString();
		}
		try {
			if (!Files.isDirectory(path)) {
				BasicFileAttributes attrs = null;
				attrs = Files.readAttributes(path, BasicFileAttributes.class);
				fileSize = attrs.size();
			}

			String localFileName = path.getFileName().toString();

			if (uploadPath.length() > 0) {
				cloudFilePath = uploadPath + "/" + localFileName;
			} else {
				cloudFilePath = localFileName;
			}

			currentFiles.add(cloudFilePath);
			cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
		} catch (Exception e) {
			logger.error("upload : Could not fetch file attributes for "
					+ localFilePath, e);
			logger.warn("Cancelling upload operation...");
			return;
		}

		try {
			downloadTable();
		} catch (IOException e1) {
			logger.error("upload : Could not download database.", e1);
			logger.warn("Cancelling upload operation...");
			return;
		} catch (SQLException e) {
			logger.error("delete : Could not open downloaded Database.", e);
			logger.warn("delete : Cancelling delete operation... ");
			return;
		}
		String[] cloudsUsed = {};
		// could replace this with just a check for fileSize < 0 i think
		if (!Files.isDirectory(path)) {
			if (fileSize > 50) {
				cloudsUsed = uploadFile(localFilePath, cloudFilePath);
			} else {
				cloudsUsed = uploadTinyFile(localFilePath, cloudFilePath);
			}
		}
		logger.debug("cloudsUsed : " + cloudsUsed);
		// cloudsUsed will be null iff the FILE could not be uploaded
		// for folders, it will be {}
		if (cloudsUsed != null) {
			Gson gson = new Gson();
			String cloudListString = gson.toJson(cloudsUsed);
			int minClouds = cloudsUsed.length - cloudDanger;
			FileMetadata file = new FileMetadata(cloudFilePath, fileSize,
					cloudListString, minClouds);
			try {
				HashSet<FileMetadata> prevRecords = db.getFileRecords(cloudFilePath);
				if(prevRecords.isEmpty()){
					db.insertFileRecord(file);
				} else {
					db.updateFileRecord(file);
				}
			} catch (SQLException e) {
				logger.error("Error inserting file: " + cloudFilePath, e);
			}
			try {
				uploadTable();
			} catch (DatabaseException e) {
				logger.error("Error uploading Database", e);
			}
		}
		currentFiles.clear();
	}

	private void acquireLock() throws LockNotAcquiredException {
		logger.trace("Acquiring lock");
		if (!cloudsHandler.acquireLock()) {
			throw new LockNotAcquiredException("lock file not found");
		}
		logger.trace("Lock Acquired");
	}

	void releaseLock() {
		logger.trace("Releasing Lock");
		cloudsHandler.releaseLock();
		logger.trace("Lock Released");
	}

	public String[] uploadTinyFile(String localFilePath, String cloudFilePath) {
		String[] cloudsUsed = null;
		try {
			cloudsUsed = cloudsHandler.uploadFile(localFilePath, cloudFilePath,
					WriteMode.OVERWRITE);
		} catch (IOException e) {
			logger.error(
					"Exception while uploading tiny file " + cloudFilePath, e);
		}
		return cloudsUsed;
	}

	public String[] uploadFile(String localFilePath, String cloudFilePath) {
		String[] cloudsUsed = null;
		try {
			Path path = Paths.get(localFilePath);
			byte[] data = Files.readAllBytes(path);
			long fileSize = data.length;
			logger.info("Uploading: " + localFilePath);
			Pair<FECParameters, Integer> params = getParams(fileSize);
			FECParameters fecParams = params.first;
			int symSize = fecParams.symbolSize();
			int blockSize = (int) Math.ceil((float) fileSize
					/ (float) fecParams.numberOfSourceBlocks());
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
				cloudsUsed = cloudsHandler.uploadFile(dataArrays, cloudFilePath
						+ "_" + blockID, WriteMode.OVERWRITE);
				blockID++;
			}
		} catch (Exception e) {
			logger.error("Exception in uploading File " + cloudFilePath, e);
			// e.printStackTrace();
		}
		return cloudsUsed;
	}

	public void download(String cloudFilePath) throws FileNotFoundException {
		try {
			downloadTable();
		} catch (IOException e) {
			logger.error("download : Could not download Database.", e);
			logger.warn("download : Going ahead with old Database....");
		} catch (SQLException e) {
			logger.error("delete : Could not open downloaded Database.", e);
			logger.warn("download : Going ahead with old Database....");
		}
		try {
			HashSet<FileMetadata> files = db.getFileRecords(cloudFilePath);
			if (!files.isEmpty()) {
				String writePath = vaultPath + "/" + cloudFilePath;
				long fileSize = (files.toArray(new FileMetadata[0]))[0].fileSize();

				// TODO : use the stored cloud list
				// String cloudListString =
				// rs.getString(FilesDatabase.CLOUDLIST);
				// Gson gson = new Gson();
				// String[] cloudsUsed = gson.fromJson(cloudListString,
				// String[].class);
				// int minClouds = rs.getInt(FilesDatabase.MINCLOUDS);

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
					cloudFilePath = (new PathManip(cloudFilePath))
							.toCloudFormat();
					if (fileSize > 50) {
						downloadFile(cloudFilePath, writePath, fileSize);
					} else {
						try {
							downloadTinyFile(cloudFilePath, writePath);
						} catch (IOException e) {
							logger.error("Could not download Tiny File : "
									+ cloudFilePath, e);
						}
					}
				}
			} else {
				throw new FileNotFoundException();
			}
		} catch (SQLException e1) {
			logger.error("Could not retrieve file data from Files Database.",
					e1);
			// e1.printStackTrace();
		}
	}

	public void downloadTinyFile(String cloudFileName, String writePath)
			throws IOException {
		cloudsHandler.downloadFile(writePath, cloudFileName);
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
			try {
				HashSet<FileMetadata> files = db.getFileRecords(cloudFilePath);
				if (!files.isEmpty()) {
					currentFiles.add(cloudFilePath);
					fileSizes.add((files.toArray(new FileMetadata[0]))[0].fileSize());
					cloudFilePath = (new PathManip(cloudFilePath))
							.toCloudFormat();
					cloudFilePaths.add(cloudFilePath);
				} else {
					logger.error("file "
							+ cloudFilePath
							+ " to be deleted not found in database hence skipping");
					vaultFolderAbsolutePaths.remove(i);
				}
			} catch (SQLException e) {
				logger.error("Could not delete " + vaultFolderAbsolutePath, e);
				// e.printStackTrace();
			}
		}

		if(!currentFiles.isEmpty()) {
			try {
				downloadTable();
			} catch (IOException e) {
				logger.error("delete : Could not download Database.", e);
				logger.warn("delete : Cancelling delete operation... ");
				return;
			} catch (SQLException e) {
				logger.error("delete : Could not open downloaded Database.", e);
				logger.warn("delete : Cancelling delete operation... ");
				return;
			}

			for (int i = 0; i < currentFiles.size(); i++) {
				try {
					db.removeFileRecord(currentFiles.get(i));
				} catch (SQLException e) {
					logger.error(
							"Error deleting record for file: "
									+ currentFiles.get(i), e);
					e.printStackTrace();
				}
			}

			try {
				uploadTable();
			} catch (DatabaseException e) {
				logger.error("error in Upload Table", e);
			}

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
		}
		logger.exit("delete");
	}

	public void uploadTable() throws DatabaseException {
		try {
			db.close();
			String databasePath = configPath + "/" + DBNAME;
			String databaseMetaPath = configPath + "/" + DB_META;
			byte[] dbData = Files.readAllBytes(Paths.get(databasePath));
			long dbSize = dbData.length;
			// get the 128-bit MD5 hash
			MessageDigest digester = MessageDigest.getInstance("MD5");
			digester.update(dbData);
			byte[] dbHash = digester.digest(); // 16 bytes

			localDBSize = dbSize;
			localDBHash = dbHash;

			logger.info("Uploading Table: Size=" + localDBSize + " Hash="
					+ Arrays.toString(localDBHash));

			updateTableMetaFile(dbHash, dbSize);
			uploadTinyFile(databaseMetaPath, DB_META);

			uploadFile(databasePath, DBNAME);
			db = Database.getInstance(databasePath);
		} catch (IOException e) {
			throw new DatabaseException("database file could not be read", e);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			throw new DatabaseException(
					"Could not reopen database after upload", e);
		}
	}

	public void downloadTable() throws IOException, SQLException {
		boolean databaseChanged = false;
		String databaseMetaPath = configPath + "/" + DB_META;

		String downloadedDBMeta = "downloaded.txt";
		String downloadedDBName = "downloaded.db";
		String downloadedDBMetaPath = configPath + "/" + downloadedDBMeta;
		String downloadedDBPath = configPath + "/" + downloadedDBName;

		// read the database size and hash from the file only if they haven't
		// been reinitialized.
		if (localDBSize == -1) {
			try {
				DataInputStream in = new DataInputStream((new FileInputStream(
						databaseMetaPath)));
				in.read(localDBHash, 0, localDBHash.length);
				localDBSize = in.readLong();
				in.close();
			} catch (IOException e) {
				logger.warn("downloadTable : could not open existing dbMeta.txt: "
						+ e.getLocalizedMessage());
			} catch (Exception e) {
				logger.warn("downloadTable : could not get local db meta");
			}
		}
		downloadTinyFile(DB_META, downloadedDBMetaPath);
		DataInputStream in = new DataInputStream(new FileInputStream(
				downloadedDBMetaPath));
		byte[] downloadedDBHash = new byte[16];
		in.read(downloadedDBHash, 0, downloadedDBHash.length);
		if (!Arrays.equals(localDBHash, downloadedDBHash) || localDBSize == -1) {
			// database changed if hashes mismatch or the db file hasn't
			// been created yet.
			databaseChanged = true;
		}
		long downloadedDBSize = in.readLong();
		in.close();
		logger.info("VaultClient : downloadTable : Local Files DB: Size="
				+ localDBSize + " Hash=" + Arrays.toString(localDBHash));
		logger.info("VaultClient : downloadTable : Files DB on cloud: Size="
				+ downloadedDBSize + " Hash="
				+ Arrays.toString(downloadedDBHash));
		if (databaseChanged) {
			logger.trace("table hash mismatch: downloading database");

			downloadFile(DBNAME, downloadedDBPath, downloadedDBSize);

			TempDatabase downloadedDB = TempDatabase.getInstance(downloadedDBPath);
			sync(downloadedDB);
			downloadedDB.close();
		}
	}
	
	public void setupTable() throws SetupException.DbError, SetupException.NetworkError {
		if(cloudsHandler.checkIfNewUser()) {
			try {
				uploadTable();
				releaseLock();
			} catch (DatabaseException e) {
				logger.error("Error uplaoding table", e);
				throw new SetupException.DbError(e);
			}
		} else {
			try {
				downloadTable();
			} catch (IOException e) {
				logger.error("Error downloading table", e);
				throw new SetupException.NetworkError(e);
			} catch (SQLException e) {
				logger.error("Error downloading table", e);
				throw new SetupException.DbError(e);
			}
		}
	}

	private boolean updateTableMetaFile(byte[] dbHash, long dbSize) {
		logger.entry("Update Table Meta");
		logger.info("Table Size:" + dbSize + " Hash:" + Arrays.toString(dbHash));
		try {
			String databaseMetaPath = configPath + "/" + DB_META;
			FileOutputStream fout = new FileOutputStream(databaseMetaPath);
			DataOutputStream dout = new DataOutputStream(fout);
			dout.write(dbHash);
			dout.writeLong(dbSize);

			dout.flush();
			fout.flush();
			dout.close();
			fout.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		logger.exit("Update Table Meta");
		return true;
	}

	public Pair<ArrayList<String>, ArrayList<String>> sync(TempDatabase downloadedDB) {
		logger.trace("Syncing local and downloaded table.");
		ArrayList<String> downloads = new ArrayList<String>();
		ArrayList<String> deletes = new ArrayList<String>();
		HashSet<FileMetadata> fileList1 = null;
		HashSet<FileMetadata> fileList2 = null;
		try {
			fileList1 = downloadedDB.getTableContents(Database.FILES_TABLE);
			fileList2 = db.getTableContents(Database.FILES_TABLE);
		} catch (SQLException e) {
			logger.error("Unable to get files list from databases", e);
			return Pair.of(downloads, deletes);
		}

		boolean downloadReq = true;
		try {
			logger.trace("starting to look for files to be updated");
			for(FileMetadata file1 : fileList1) {
				downloadReq = true;
				String fileName = file1.fileName();
				long fileSize = file1.fileSize();
				Timestamp t1 = Timestamp.valueOf(file1.timestamp());
				logger.trace("sync : downloaded table : filename : " + fileName);
				logger.trace("sync : downloaded table : filesize : " + fileSize);
				logger.trace("sync : downloaded table : timestamp : " + t1.toString());
				if (!currentFiles.contains(fileName)) {
					HashSet<FileMetadata> files = db.getFileRecords(fileName);
					if (!files.isEmpty()) {
						Timestamp t2 = Timestamp.valueOf((files.toArray(new FileMetadata[0])[0]).timestamp());
						logger.trace("sync : local table : timestamp : " + t2.toString());
						if (t2.getTime() >= t1.getTime()) {
							downloadReq = false;
						}
					}
					if (downloadReq) {
						logger.info("Sync : File to be updated: "
								+ fileName);
						Path filePath = Paths.get(vaultPath + "/"
								+ fileName);
						downloads.add(filePath.toString());
						FileMetadata file = new FileMetadata(fileName, fileSize,
								file1.cloudList(),
								file1.minClouds(), t1.toString());
						HashSet<FileMetadata> prevRecords = db.getFileRecords(fileName);
						if(prevRecords.isEmpty()){
							db.insertFileRecord(file);
						} else {
							db.updateFileRecord(file);
						}
						if (fileSize > 0) {
							try {
								Files.createDirectories(filePath
										.getParent());
							} catch (IOException e) {
								logger.error("Sync : unable to create parent directories for file: "
										+ fileName);
							}
							downloadFile(fileName, filePath.toString(),
									fileSize);
						}
					}
				}
			}
			logger.trace("done looking for files to update.");
		} catch (SQLException e) {
			logger.error(
					"Error occurred while looking for files to update ", e);
		}

		boolean deleteReq = false;
		try {
			logger.trace("starting to look for files to be deleted");
			for(FileMetadata file2 : fileList2) {
				deleteReq = true;
				String fileName = file2.fileName();
				long fileSize = file2.fileSize();
				logger.trace("sync : local table : filename : " + fileName);
				logger.trace("sync : local table : filesize : " + fileSize);
				if (!currentFiles.contains(fileName)) {
					HashSet<FileMetadata> files = downloadedDB.getFileRecords(fileName);
					if (!files.isEmpty()) {
						deleteReq = false;
					}					
					if (deleteReq) {
						logger.info("Sync : File to be deleted: "
								+ fileName);
						try {
							Path filePath = Paths.get(vaultPath + "/"
									+ fileName);
							deletes.add(filePath.toString());
							if (Files.exists(filePath)) {
								if (fileSize > 0)
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
														Path dir,
														IOException e)
														throws IOException {
													Files.delete(dir);
													return FileVisitResult.CONTINUE;
												}
											});
								}
							}
							db.removeFileRecord(fileName);
						} catch (IOException e) {
							logger.error("Sync : unable to delete "
									+ fileName, e);
						}
					}
				}
			}
			logger.trace("done looking for files to delete.");
		} catch (SQLException e) {
			logger.error(
					"Error occurred while while looking for files to delete ",
					e);
		}	
		return Pair.of(downloads, deletes);
	}

	public Pair<ArrayList<String>, ArrayList<String>> sync() {
		logger.trace("Starting Sync");
		Pair<ArrayList<String>, ArrayList<String>> changes = null;
		boolean databaseChanged = false;
		String databaseMetaPath = configPath + "/" + DB_META;

		String downloadedDBMeta = "downloaded.txt";
		String downloadedDBName = "downloaded.db";
		String downloadedDBMetaPath = configPath + "/" + downloadedDBMeta;
		String downloadedDBPath = configPath + "/" + downloadedDBName;

		// read the database size and hash from the file only if they haven't
		// been reinitialized.
		if (localDBSize == -1) {
			try {
				DataInputStream in = new DataInputStream((new FileInputStream(
						databaseMetaPath)));
				in.read(localDBHash, 0, localDBHash.length);
				localDBSize = in.readLong();
				in.close();
			} catch (IOException e) {
				logger.warn("sync : could not open existing dbMeta.txt: "
						+ e.getLocalizedMessage());
			} catch (Exception e) {
				logger.warn("sync : could not get local db meta");
			}
		}
		try {
			downloadTinyFile(DB_META, downloadedDBMetaPath);
			DataInputStream in = new DataInputStream(new FileInputStream(
					downloadedDBMetaPath));
			byte[] downloadedDBHash = new byte[16];
			in.read(downloadedDBHash, 0, downloadedDBHash.length);
			if (!Arrays.equals(localDBHash, downloadedDBHash)
					|| localDBSize == -1) {
				// database changed if hashes mismatch or the db file hasn't
				// been created yet.
				databaseChanged = true;
			}
			long downloadedDBSize = in.readLong();
			in.close();
			logger.info("VaultClient : sync : Local Files DB: Size="
					+ localDBSize + " Hash=" + Arrays.toString(localDBHash));
			logger.info("VaultClient : sync : Files DB on cloud: Size="
					+ downloadedDBSize
					+ " Hash="
					+ Arrays.toString(downloadedDBHash));
			if (databaseChanged) {
				//Download database.
				logger.trace("table hash mismatch: downloading database");
				
				downloadFile(DBNAME, downloadedDBPath, downloadedDBSize);

				TempDatabase downloadedDB = TempDatabase.getInstance(downloadedDBPath);
				//Sync databases. 
				sync(downloadedDB);
				downloadedDB.close();
				
				//now need to update the meta file.
				db.close();
				String databasePath = configPath + "/" + DBNAME;
				byte[] dbData = Files.readAllBytes(Paths.get(databasePath));
				long dbSize = dbData.length;
				// get the 128-bit MD5 hash
				MessageDigest digester = MessageDigest.getInstance("MD5");
				digester.update(dbData);
				byte[] dbHash = digester.digest(); // 16 bytes

				localDBSize = dbSize;
				localDBHash = dbHash;

				logger.info("Uploading Table: Size=" + localDBSize + " Hash="
						+ Arrays.toString(localDBHash));

				updateTableMetaFile(dbHash, dbSize);
				// reopen database
				db = Database.getInstance(databasePath);
			}
		} catch (IOException | SQLException e) {
			logger.error("Error occurred while syncing.", e);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return changes;	
	}

	public void shutdown() {
		cloudsHandler.shutdown();
	}
}
