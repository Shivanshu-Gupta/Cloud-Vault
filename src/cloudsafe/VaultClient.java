package cloudsafe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

//import static java.nio.file.StandardOpenOption.*; //for READ, WRITE etc





import org.apache.http.util.*;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.ArrayDataDecoder;
import net.fec.openrq.ArrayDataEncoder;
import net.fec.openrq.EncodingPacket;
import net.fec.openrq.parameters.FECParameters;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.decoder.SourceBlockDecoder;
import cloudsafe.util.Pair;
import cloudsafe.FolderCloud;
import cloudsafe.Dropbox;
import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.CloudType;
import cloudsafe.cloud.WriteMode;
import cloudsafe.database.*;

/**
 * The entry point for the CloudSafe Application.
 */
public class VaultClient {

	static String vaultPath;
	
	static long databaseSize;
	static String databasePath = "temp/table.ser";
	final static String databaseSizePath = "temp/tablesize.txt";
	
	String cloudMetadataPath = "temp/cloudmetadata.ser";
	int cloudNum; // Co
	int cloudDanger; // Cd
	final static int overHead = 4; // epsilon

	static ArrayList<Cloud> clouds = new ArrayList<Cloud>();
	static ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();
	static Table table;

	public VaultClient(int cloudNum, int cloudDanger){
		this.cloudNum = cloudNum;
		this.cloudDanger = cloudDanger;
	}
	
	@SuppressWarnings("unchecked")
	public VaultClient(int cloudNum, int cloudDanger, String cloudMetadataPath){
		this.cloudNum = cloudNum;
		this.cloudDanger = cloudDanger;
		this.cloudMetadataPath = cloudMetadataPath;
		try {
			FileInputStream fileIn = new FileInputStream(
					cloudMetadataPath.toString());
			ObjectInputStream in = new ObjectInputStream(fileIn);
			cloudMetaData = (ArrayList<Pair<String, String>>) in.readObject();

			for (Pair<String, String> metadata : cloudMetaData) {
				switch (metadata.first) {
				case "dropbox":
					clouds.add(new Dropbox(metadata.second));
					break;
				case "googledrive":
					clouds.add(new GoogleDrive());
					break;
				// case "onedrive" : clouds.add(new Dropbox());
				// break;
				// case "box" : clouds.add(new Dropbox());
				// break;
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
		}
		// download and populate the table
		downloadTable();
	}
	
	private Pair<FECParameters, Integer> getParams(long fileSize) {
		Pair<FECParameters, Integer> params = null;
		try {
			int symSize = (int) Math.round(Math.sqrt((float) fileSize * 8
					/ (float) overHead)); // symbol header length = 8, T =
											// sqrt(D * delta / epsilon)
			int blockCount = 1;
			FECParameters fecParams = FECParameters.newParameters(fileSize, symSize,
					blockCount);

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
	public void updateTableSizeFile(long tableSize)
	{
		try {
			DataOutputStream out = new DataOutputStream(
					new FileOutputStream(databaseSizePath));
			out.writeLong(databaseSize);
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void uploadFile(String localFilePath)
	{
		Path path = Paths.get(localFilePath);
		File file = new File(localFilePath);
		long fileSize = file.length();
		String localFileName = path.getFileName().toString();
		String cloudFileName = null;
		switch(localFileName)
		{
		case "table.ser" : cloudFileName = localFileName; break;
		case "tablesize.txt" : cloudFileName = localFileName; break;
		default : int version = table.addNewFile(localFileName, fileSize);
				cloudFileName = localFileName + " (" + Integer.toString(version) + ")";
				databaseSize = table.writeToFile(databasePath);
				updateTableSizeFile(databaseSize);
				uploadFile(databaseSizePath);
				uploadFile(databasePath);
		}
		upload(localFilePath, cloudFileName);
	}
	public void upload(String localFilePath, String cloudFilePath) {
//		System.out.println("Upload: ");
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
			System.out.println("dataEncoder created");
			System.out.println("data length: " + dataEncoder.dataLength());
			System.out.println("symbol size: " + dataEncoder.symbolSize());

			int packetID = 0, packetCount = 0, blockID = 0;
			byte[] packetdata;
			Iterable<SourceBlockEncoder> srcBlkEncoders = dataEncoder
					.sourceBlockIterable();

			for (SourceBlockEncoder srcBlkEnc : srcBlkEncoders) {
				System.out.println("Block " + blockID);
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
				System.out.println("number of repair packets for block"
						+ blockID + ": " + (packetID - packetCount));
				for (int i = 0; i < clouds.size(); i++) {
					Cloud cloud = clouds.get(i);
					if (cloud.isAvailable()) {
						cloud.uploadFile(dataArrays.get(i).toByteArray(),
								cloudFilePath + "_" + blockID,
								WriteMode.OVERWRITE);
					}
				}
				packetCount = packetID;
				blockID++;
			}
		} catch (Exception e) {
			System.out.println("Exception in upload: " + e);
			e.printStackTrace();
		}
	}
	
	public void downloadFile(String localFileName, int version) throws FileNotFoundException
	{
		String cloudFileName = null;
		String writePath = null;
		long fileSize = 0;
		if(!table.hasFileVersion(localFileName, version))
		{
			throw new FileNotFoundException();
		}
		switch(localFileName)
		{
		case "table.ser" : 
			cloudFileName = localFileName; 
			download("tablesize.txt", databaseSizePath, 8);
			try (DataInputStream in = new DataInputStream(new FileInputStream(
					databaseSizePath))) {
				databaseSize = in.readLong();
			} catch (IOException e) {
				System.out.println("IOException: " + e);
				e.printStackTrace();
			}
			fileSize = databaseSize;
			writePath = databasePath;
			break;
//		case "tablesize.txt" : 
//			cloudFileName = localFileName; break;
		default : 
			cloudFileName = localFileName + " (" + Integer.toString(version) + ")";
			fileSize = table.fileSize(localFileName, version);
			writePath = vaultPath + "/" + localFileName;
		}
		download(cloudFileName, writePath, fileSize);
	}
	
	public void download(String cloudFileName, String writePath, long fileSize) {
		System.out.println("Downloading: ");
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
				for (int i = 0; i < clouds.size(); i++) {
					Cloud cloud = clouds.get(i);
					blockFileName = cloudFileName + "_" + blockID;
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
			System.out.println("Packets available after cloud outage : "
					+ packetCount);
			while (!dataDecoder.isDataDecoded() && packetID < packetList.size()) {
				byte[] packet = packetList.get(packetID);
				EncodingPacket encPack = dataDecoder.parsePacket(packet, true)
						.value();
				int sbn = encPack.sourceBlockNumber();
				SourceBlockDecoder srcBlkDec = dataDecoder.sourceBlock(sbn);

				srcBlkDec.putEncodingPacket(encPack);
				packetID++;
			}

			System.out.println("file has been decoded!");
			System.out.println("used " + packetID + " packets out of "
					+ packetCount + " packets");

			byte dataNew[] = dataDecoder.dataArray();
			Path pathNew = Paths.get(writePath);
			Files.write(pathNew, dataNew);
		} catch (Exception e) {
			System.out.println("Exception in download: " + e);
			e.printStackTrace();
		}
	}
	
	public void createNewTable() {
		try {
			table = new Table();
			databaseSize = table.writeToFile(databasePath);
			updateTableSizeFile(databaseSize);
			uploadFile(databaseSizePath);
			uploadFile(databasePath);
		} catch (Exception x) {
			System.out.println("Exception in creating table: " + x);
		}
	}

	public void downloadTable() {
		try {
			downloadFile("table.ser", 0);
		} catch (FileNotFoundException e) {
			System.out.println("FileNotFoundException: " + e);
			e.printStackTrace();
		}
		table = new Table(databasePath);
	}

	public void addCloud(CloudType type) {
		Cloud cloud;
		switch (type) {
		case DROPBOX:
			cloud = new Dropbox();
			clouds.add(cloud);
			cloudMetaData.add(Pair.of("dropbox", cloud.metadata()));
			break;
		case GOOGLEDRIVE:
			cloud = new GoogleDrive();
			clouds.add(cloud);
			cloudMetaData.add(Pair.of("googledrive", cloud.metadata()));
			break;
		case ONEDRIVE:
			cloud = new FolderCloud();
			clouds.add(cloud);
			cloudMetaData.add(Pair.of("folder", cloud.metadata()));
			break;
		case BOX:
			cloud = new FolderCloud();
			clouds.add(cloud);
			cloudMetaData.add(Pair.of("folder", cloud.metadata()));
			break;
		case FOLDER:
			cloud = new FolderCloud();
			clouds.add(cloud);
			cloudMetaData.add(Pair.of("folder", cloud.metadata()));
			break;
		}
	}

	public Object[] getFileList(){
		return table.getFileList();
	}
	
	public ArrayList<FileMetadata> getFileHistory(String fileName) throws FileNotFoundException
	{ 
		try {
			return table.getFileHistory(fileName);
		} catch (FileNotFoundException e) {
			throw new FileNotFoundException();
		}
	}
	
	public void sync() {

	}
}
