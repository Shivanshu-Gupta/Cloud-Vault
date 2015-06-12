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

//import cloudsafe.exceptions.AuthenticationException;

/**
 * The entry point for the CloudSafe Application.
 */
public class VaultClientDesktop {


	String vaultPath = Paths.get("trials/Cloud Vault").toAbsolutePath()
			.toString();
	String localConfigPath = "trials/config";
	String cloudConfigPath = vaultPath;
	String cloudMetadataPath = localConfigPath + "/cloudmetadata.ser";
	int cloudNum = 4; // Co
	int cloudDanger = 1; // Cd
	final static int overHead = 4; // epsilon
	Proxy proxy = Proxy.NO_PROXY;

	ArrayList<Cloud> clouds = new ArrayList<Cloud>();
	ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();
	Table table;

	long databaseSize;
	int databaseHash;
	String databasePath = cloudConfigPath + "/table.ser";
	String databaseMetaPath = cloudConfigPath + "/tablemeta.txt";
	
	String currentFile = "";
	
	@SuppressWarnings("unchecked")
	public VaultClientDesktop(String vaultPath) {
		// this.vaultPath = Paths.get(vaultPath).toAbsolutePath().toString();
		// System.out.println("vaultPath: " + vaultPath);
		// this.localConfigPath = "trials/config";
		// this.cloudConfigPath = vaultPath;
		// System.out.println("cloudConfigPath: " + cloudConfigPath);
		// this.cloudMetadataPath = localConfigPath + "/cloudmetadata.ser";
		// this.databasePath = cloudConfigPath + "/table.ser";
		// System.out.println("sizePath: " + databaseMetaPath);
		// this.databaseMetaPath = cloudConfigPath + "/tablemeta.txt";
		// System.out.println("sizePath: " + databaseMetaPath);
		proxy = getProxy();
		try {
			FileInputStream fileIn = new FileInputStream(cloudMetadataPath);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			cloudMetaData = (ArrayList<Pair<String, String>>) in.readObject();

			for (Pair<String, String> metadata : cloudMetaData) {
				System.out.println("adding cloud: " + metadata.first);
				switch (metadata.first) {
				case "dropbox":
					clouds.add(new Dropbox(metadata.second, proxy));
					break;
				case "googledrive":
					clouds.add(new GoogleDrive(proxy));
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
			System.out.println("IOException: " + x);
			x.printStackTrace();
		} catch (ClassNotFoundException cfe) {
			System.out.println("ClassNotFoundException: " + cfe);
			cfe.printStackTrace();
		} catch (BoxRestException | BoxServerException
				| AuthFatalFailureException e) {
			e.printStackTrace();
		}

		boolean newUser = checkIfNewUser();
		if (newUser)
			createNewTable();
		else
			downloadTable();
	}

	private Proxy getProxy() {
		Proxy proxy = Proxy.NO_PROXY;
		try {
			Properties proxySettings = new Properties();
			File configFile = new File(localConfigPath + "/config.properties");
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
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
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
			System.out.println("Exception: " + x);
		}
		return params;
	}

	public void upload(String localFilePath) throws NoSuchFileException {
		// localFilePath = localFilePath.replace("\\", "/");
//		System.out.println(localFilePath);
//		System.out.println(vaultPath);
		Path path = Paths.get(localFilePath).normalize();
//		System.out.println("Path : " + path.toString());
		Path temp = Paths.get(vaultPath).relativize(path).getParent();
		//System.out.println("temp : " + temp);
		String uploadPath = "";
		if (temp != null) {
			uploadPath = temp.toString();
		}
		BasicFileAttributes attrs = null;
		try {
			attrs = Files.readAttributes(path, BasicFileAttributes.class);
		} catch (Exception e) {
			throw new NoSuchFileException(path.toString());
		}
		long fileSize = attrs.size();
		String localFileName = path.getFileName().toString();
		String cloudFilePath = null;
		switch (localFileName) {
		case "table.ser":
			cloudFilePath = localFileName;
			break;
		case "tablemeta.txt":
			cloudFilePath = localFileName;
			break;
		default:
			if (uploadPath.length() > 0) {
				cloudFilePath = uploadPath + "/" + localFileName;
			} else {
				cloudFilePath = localFileName;
			}
			acquireLock();
			currentFile = cloudFilePath;
			downloadTable();
			table.addNewFile(cloudFilePath, fileSize);
			cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
			uploadTable();
			currentFile = "";
			releaseLock();
		}
		uploadFile(localFilePath, cloudFilePath);
	}

	private void releaseLock() {
		// TODO Auto-generated method stub
		
	}

	private void acquireLock() {
		// TODO Auto-generated method stub
		
	}

	public void uploadFile(String localFilePath, String cloudFilePath) {
		// System.out.println("Upload: ");
		try {
			Path path = Paths.get(localFilePath);
			byte[] data = Files.readAllBytes(path);
			long fileSize = data.length;
			System.out.println("Uploading: " + localFilePath);
			Pair<FECParameters, Integer> params = getParams(fileSize);
			FECParameters fecParams = params.first;
			int symSize = fecParams.symbolSize();
			int k = (int) Math.ceil((float) fileSize / (float) symSize);
			int r = params.second;

			ArrayDataEncoder dataEncoder = OpenRQ.newEncoder(data, fecParams);
//			System.out.println("dataEncoder created");
//			System.out.println("data length: " + dataEncoder.dataLength());
//			System.out.println("symbol size: " + dataEncoder.symbolSize());

			int packetID = 0, blockID = 0;
			byte[] packetdata;
			Iterable<SourceBlockEncoder> srcBlkEncoders = dataEncoder
					.sourceBlockIterable();

			for (SourceBlockEncoder srcBlkEnc : srcBlkEncoders) {
//				System.out.println("Block " + blockID);
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
//				System.out.println("number of repair packets for block"
//						+ blockID + ": " + (packetID - packetCount));
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
			System.out.println("Exception in upload: " + e);
			e.printStackTrace();
		}
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
				downloadFile(cloudFilePath, writePath, fileSize);
			}

		} else {
			throw new FileNotFoundException();
		}
	}

	public void downloadFile(String cloudFileName, String writePath,
			long fileSize) {
		System.out.println("Downloading: " + cloudFileName);
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
			System.out.println("packets have been downloaded!");

			packetID = 0;
			packetCount = packetList.size();
//			System.out.println("Packets available: " + packetCount);
			while (!dataDecoder.isDataDecoded() && packetID < packetList.size()) {
				byte[] packet = packetList.get(packetID);
				EncodingPacket encPack = dataDecoder.parsePacket(packet, true)
						.value();
				int sbn = encPack.sourceBlockNumber();
				SourceBlockDecoder srcBlkDec = dataDecoder.sourceBlock(sbn);

				srcBlkDec.putEncodingPacket(encPack);
				packetID++;
			}

//			System.out.println("file has been decoded!");
//			System.out.println("used " + packetID + " packets out of "
//					+ packetCount + " packets");

			byte dataNew[] = dataDecoder.dataArray();
			Path pathNew = Paths.get(writePath);
			Files.write(pathNew, dataNew);
		} catch (Exception e) {
			System.out.println("Exception in download: " + e);
			e.printStackTrace();
		}
	}

	public void delete(String vaultFolderAbsolutePath) throws FileNotFoundException {
		
		Path path = Paths.get(vaultFolderAbsolutePath).normalize();
		System.out.println("Path : " + path.toString());
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

		System.out.println("CloudFilePath : " + cloudFilePath.toString());
		if (table.hasFile(cloudFilePath)) {
			fileSize = table.fileSize(cloudFilePath);
			acquireLock();
			downloadTable();
			table.removeFile(cloudFilePath);
			uploadTable();
			releaseLock();
			if (fileSize < 0) {
				throw new FileNotFoundException();
			} else {
				cloudFilePath = (new PathManip(cloudFilePath)).toCloudFormat();
				Pair<FECParameters, Integer> params = getParams(fileSize);
				FECParameters fecParams = params.first;
				int blockID = 0, blockCount = fecParams.numberOfSourceBlocks();
				String blockFileName = null;
				while (blockID < blockCount) {
					blockFileName = cloudFilePath + "_" + blockID;
					System.out.println("Deleteing " + blockFileName);
					for (int i = 0; i < clouds.size(); i++) {
						Cloud cloud = clouds.get(i);
						if (cloud.isAvailable()
								&& cloud.searchFile(blockFileName)) {
							cloud.deleteFile(blockFileName);
						}
					}
					blockID++;
				}
			}

		} else {
			throw new FileNotFoundException();
		}
	}

	public void createNewTable() {
		try {
			table = new Table();
			uploadTable();
		} catch (Exception x) {
			System.out.println("Exception in creating table: " + x);
			x.printStackTrace();
		}
	}

	public void uploadTable() {
		try {
			table.writeToFile(databasePath);
			byte[] tableBytes = Files.readAllBytes(Paths.get(databasePath));
			databaseSize = tableBytes.length;
			databaseHash = tableBytes.hashCode();
			updateTableMetaFile(databaseSize, databaseHash);
			upload(databaseMetaPath);
			upload(databasePath);
		} catch (IOException e) {
			System.out.println("Exception while uploading database");
			e.printStackTrace();
		}
	}
	
	public void downloadTable() {
		boolean databaseChanged = false; 
		String cloudFilePath = "table.ser";
		downloadFile("tablemeta.txt", databaseMetaPath, 8);
		try (DataInputStream in = new DataInputStream(new FileInputStream(
				databaseMetaPath))) {
			int tableHash = in.readInt();
			if(databaseHash != tableHash){
				databaseChanged = true;
			}
			databaseSize = in.readLong();
		} catch (IOException e) {
			System.out.println("IOException: " + e);
			e.printStackTrace();
		}
		if (databaseChanged) {
			System.out.println("Size of the database on cloud: " + databaseSize);
			System.out.println("downloading database");
			downloadFile(cloudFilePath, databasePath, databaseSize);
			Table newTable = new Table(databasePath);
			sync(newTable);
		}
	}

	private void updateTableMetaFile(long tableSize, int tableHash) {
		try {
			FileOutputStream fileOut = new FileOutputStream(databaseMetaPath);
			DataOutputStream out = new DataOutputStream(fileOut);
			out.writeInt(tableHash);
			out.writeLong(databaseSize);
			out.flush();
			fileOut.flush();
			out.close();
			fileOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public boolean checkIfNewUser() {
		boolean newUser = true;
		for (int i = 0; i < clouds.size(); i++) {
			if (clouds.get(i).searchFile("table.ser")) {
				System.out.println("Found table.ser");
				newUser = false;
				break;
			}
		}
		return newUser;
	}

	public Object[] getFileList() {
		downloadTable();
		return table.getFileList();
	}
	
	public void sync(Table newTable) {
		Object[] localFiles = table.getFileList();
		Object[] filesInVault = newTable.getFileList();
		for(Object file : filesInVault){
			if(!table.hasFile((String)file) && !currentFile.equals(file)){
				table.addNewFile((String)file, newTable.fileSize((String)file));
				try {
					download((String)file);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
		for(Object file : localFiles){
			if(!newTable.hasFile((String)file)){
				try {
					Path filePath = Paths.get(vaultPath + "/" + (String)file);
					Files.delete(filePath);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public void sync() {
		boolean databaseChanged = false; 
		String cloudFilePath = "table.ser";
		downloadFile("tablemeta.txt", databaseMetaPath, 8);
		try (DataInputStream in = new DataInputStream(new FileInputStream(
				databaseMetaPath))) {
			int tableHash = in.readInt();
			if(databaseHash != tableHash){
				databaseChanged = true;
			}
			databaseSize = in.readLong();
		} catch (IOException e) {
			System.out.println("IOException: " + e);
			e.printStackTrace();
		}
		if (databaseChanged) {
			System.out.println("database Size: " + databaseSize);
			downloadFile(cloudFilePath, databasePath, databaseSize);
			Table newTable = new Table(databasePath);
			sync(newTable);
		}
	}
}
