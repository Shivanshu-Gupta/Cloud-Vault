package cloudsafe;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Arrays;

//import static java.nio.file.StandardOpenOption.*; //for READ, WRITE etc

import org.apache.http.util.*;
import org.apache.commons.io.input.CloseShieldInputStream;

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
import cloudsafe.cloud.WriteMode;
import cloudsafe.database.Table;

/**
 * The entry point for the CloudSafe Application.
 */
public class Main {

	static int tableFileSize = 1024 * 32;
	static String tablePath = "trials/table.ser";
	final static String tableSizeFilePath = "trials/tablesize.txt";
	final static Path cloudMetadataPath = Paths.get("trials/cloudmetadata.ser");

	static ArrayList<Cloud> clouds = new ArrayList<Cloud>();
	static ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();

	static int cloudNum = 4; // Co
	static int cloudDanger = 1; // Cd
	static int overHead = 4; // epsilon

	static Table table;

	Pair<FECParameters, Integer> getParams(int len) {
		Pair<FECParameters, Integer> params = null;
		try {
			int symSize = (int) Math.round(Math.sqrt((float) len * 8
					/ (float) overHead)); // symbol header length = 8, T =
											// sqrt(D * delta / epsilon)
			int blockCount = 1;
			FECParameters fecParams = FECParameters.newParameters(len, symSize,
					blockCount);

			int k = (int) Math.ceil((float) len / (float) symSize);
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

	public void upload(String filePath) {
		System.out.println("Upload: ");
		try {
			Path path = Paths.get(filePath);
			byte[] data = Files.readAllBytes(path);
			int fileSize = data.length;
			if (fileSize < 100) {
				uploadTinyFile(filePath);
				return;
			}
			System.out.println("Uploading: " + filePath);
			Pair<FECParameters, Integer> params = getParams(fileSize);
			FECParameters fecParams = params.first;
			int symSize = fecParams.symbolSize();
			int k = (int) Math.ceil((float) fileSize / (float) symSize);
			int r = params.second;

			String fileName = path.getFileName().toString();
			String fileID;
			int version;
			if (filePath.equals(tablePath)) {
				fileID = "table";
			} else if (filePath.equals(tableSizeFilePath)) {
				fileID = "tablesize";
			} else {
				version = table.addNewFile(fileName, fileSize);
				fileID = Integer.toString(version);
				tableFileSize = table.updateTable(tablePath);
				DataOutputStream out = new DataOutputStream(
						new FileOutputStream(tableSizeFilePath));
				out.writeInt(tableFileSize);
				out.flush();
				out.close();
				upload(tableSizeFilePath);
				upload(tablePath);
			}

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
								fileName + "(" + fileID + ")_" + blockID,
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

	public void download(String fileName, int version) {
		System.out.println("Download: ");
		String fileID = Integer.toString(version);
		int fileSize = table.fileSize(fileName, version);

		if (fileSize < 100) {
			downloadTinyFile(fileName, version);
			return;
		}

		System.out.println("Downloading: ");

		Pair<FECParameters, Integer> params = getParams(fileSize);
		FECParameters fecParams = params.first;
		int symSize = fecParams.symbolSize();
		int blockID = 0, blockCount = fecParams.numberOfSourceBlocks();
		int packetID = 0, packetCount = 0;
		int packetlength = symSize + 8;
		// String packetFileName = null;
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
					blockFileName = fileName + "(" + fileID + ")_" + blockID;
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

			// SourceBlockState state;
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

				// state = srcBlkDec.putEncodingPacket(encPack);
				srcBlkDec.putEncodingPacket(encPack);
				packetID++;
			}

			System.out.println("file has been decoded!");
			System.out.println("used " + packetID + " packets out of "
					+ packetCount + " packets");

			byte dataNew[] = dataDecoder.dataArray();
			Path pathNew = Paths.get("trials/" + fileName);
			Files.write(pathNew, dataNew);
		} catch (Exception e) {
			System.out.println("Exception in download: " + e);
			e.printStackTrace();
		}
	}

	public void uploadTinyFile(String filePath) {
		System.out.println("Uploading tiny file: " + filePath);
		Path path = Paths.get(filePath);
		byte[] data;
		try {
			data = Files.readAllBytes(path);
			int fileSize = data.length;
			String fileName = path.getFileName().toString();
			String fileID;
			int version;
			if (filePath.equals(tablePath)) {
				fileID = "table";
			} else if (filePath.equals(tableSizeFilePath)) {
				fileID = "tablesize";
			} else {
				version = table.addNewFile(fileName, fileSize);
				fileID = Integer.toString(version);

				tableFileSize = table.updateTable(tablePath);
				DataOutputStream out = new DataOutputStream(
						new FileOutputStream(tableSizeFilePath));
				out.writeInt(tableFileSize);
				out.flush();
				out.close();
				upload(tableSizeFilePath);
				upload(tablePath);
			}
			for (int i = 0; i < clouds.size(); i++) {
				Cloud cloud = clouds.get(i);
				if (cloud.isAvailable()) {
					cloud.uploadFile(data, fileName + "(" + fileID + ")",
							WriteMode.OVERWRITE);
				}
			}
		} catch (IOException x) {
			System.out.println("IOException: " + x);
			x.printStackTrace();
		}
	}

	public void downloadTinyFile(String fileName, int version) {
		System.out.println("Downloading tiny file: " + fileName);
		String fileID = Integer.toString(version);
		String name = fileName + "(" + fileID + ")";
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable() && cloud.searchFile(name)) {
				try {
					cloud.downloadFile(fileName, name);
					break;
				} catch (IOException e) {
					System.out.println("IOException: " + e);
					e.printStackTrace();
				}
			}
		}
	}

	public void createNewTable() {
		try {
			// byte[] tabledata = {};
			// Files.write(Paths.get(tablePath), tabledata, CREATE_NEW);
			table = new Table();
			tableFileSize = table.tableFileSize();
			tablePath = table.tableFilePath();
			DataOutputStream out = new DataOutputStream(new FileOutputStream(
					tableSizeFilePath));
			out.writeInt(tableFileSize);
			out.flush();
			out.close();
			upload(tableSizeFilePath);
			upload(tablePath);
		} catch (Exception x) {
			System.out.println("Exception in creating table: " + x);
		}
	}

	public void populateTable() {
		String fileID = "tablesize";
		String fileName = Paths.get(tableSizeFilePath).getFileName().toString();
		String name = fileName + "(" + fileID + ")";
		for (int i = 0; i < clouds.size(); i++) {
			Cloud cloud = clouds.get(i);
			if (cloud.isAvailable() && cloud.searchFile(name)) {
				try {
					cloud.downloadFile(tableSizeFilePath, name);
					break;
				} catch (IOException e) {
					System.out.println("IOException: " + e);
					e.printStackTrace();
				}
			}
		}
		try (DataInputStream in = new DataInputStream(new FileInputStream(
				tableSizeFilePath))) {
			tableFileSize = in.readInt();
		} catch (IOException e) {
			System.out.println("IOException: " + e);
			e.printStackTrace();
		}

		fileID = "table";
		fileName = Paths.get(tablePath).getFileName().toString();
		byte[] tabledata = {};
		if (tableFileSize < 100) {
			name = fileName + "(" + fileID + ")";
			for (int i = 0; i < clouds.size(); i++) {
				Cloud cloud = clouds.get(i);
				if (cloud.isAvailable() && cloud.searchFile(name)) {
					try {
						tabledata = cloud.downloadFile(name);
						Files.write(Paths.get(tablePath), tabledata);
						break;
					} catch (IOException e) {
						System.out.println("IOException: " + e);
						e.printStackTrace();
					}
				}
			}
		} else {
			Pair<FECParameters, Integer> params = getParams(tableFileSize);
			FECParameters fecParams = params.first;
			int symSize = fecParams.symbolSize();
			int blockID = 0, blockCount = fecParams.numberOfSourceBlocks();
			int packetID = 0, packetCount = 0;
			String blockFileName = null;
			int packetlength = symSize + 8;
			try {
				byte[] packetdata;
				byte[] blockdata;
				ArrayDataDecoder dataDecoder = OpenRQ.newDecoder(fecParams, 3);

				// reading in all the packets into a byte[] list
				List<byte[]> packetList = new ArrayList<byte[]>();
				while (blockID < blockCount) {
					for (int i = 0; i < clouds.size(); i++) {
						Cloud cloud = clouds.get(i);
						blockFileName = fileName + "(" + fileID + ")_"
								+ blockID;
						if (cloud.isAvailable()
								&& cloud.searchFile(blockFileName)) {
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
				packetCount = packetList.size();
				System.out.println("Packets available after cloud outage : "
						+ packetCount);

				// SourceBlockState state;
				packetID = 0;
				while (!dataDecoder.isDataDecoded()
						&& packetID < packetList.size()) {
					byte[] packet = packetList.get(packetID);

					EncodingPacket encPack = dataDecoder.parsePacket(packet,
							true).value();

					int sbn = encPack.sourceBlockNumber();
					SourceBlockDecoder srcBlkDec = dataDecoder.sourceBlock(sbn);

					// state = srcBlkDec.putEncodingPacket(encPack);
					srcBlkDec.putEncodingPacket(encPack);
					packetID++;
				}

				System.out.println("used " + packetID + " packets out of "
						+ packetCount + " packets");
				tabledata = dataDecoder.dataArray();
				Files.write(Paths.get(tablePath), tabledata);
			} catch (Exception e) {
				System.out.println("Exception in populateTable: " + e);
				e.printStackTrace();
			}
		}
		table = new Table(tabledata);
	}

	private void addCloud() {
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		String s = null;
		Cloud cloud;
		System.out.println("Select one amongst the following drives: ");
		System.out.println("1. Dropbox\t" + "2. Google Drive\t"
				+ "3. Onedrive\t" + "4. Box\t" + "5. Folder");
		System.out.println("Enter drive number as choice: ");
		s = in.nextLine();
		while (!s.equals("1") && !s.equals("2") && !s.equals("3")
				&& !s.equals("4") && !s.equals("5")) {
			System.out
					.println("Invalid choice! Enter drive number as choice: ");
			s = in.nextLine();
		}
		switch (s) {
		case "1":
			cloud = new Dropbox();
			clouds.add(cloud);
			cloudMetaData.add(Pair.of("dropbox", cloud.metadata()));
			break;
		// case "2":
		// cloud = new GoogleDrive();
		// clouds.add(cloud);
		// cloudMetaData.add(Pair.of("googledrive", cloud.metadata()));
		// break;
		case "3":
			cloud = new FolderCloud();
			clouds.add(cloud);
			cloudMetaData.add(Pair.of("folder", cloud.metadata()));
			break;
		case "4":
			cloud = new FolderCloud();
			clouds.add(cloud);
			cloudMetaData.add(Pair.of("folder", cloud.metadata()));
			break;
		case "5":
			cloud = new FolderCloud();
			clouds.add(cloud);
			cloudMetaData.add(Pair.of("folder", cloud.metadata()));
			break;
		}
		in.close();
	}

	private void newSetup() {
		String s;
		try (Scanner in = new Scanner(new CloseShieldInputStream(System.in))) {
			for (int i = 0; i < 4; i++) {
				System.out.println("CLOUD " + (i + 1));
				addCloud();
			}
			System.out.println("Add more Clouds (Yes/No)?");
			s = in.nextLine();
			while ((s.equals("Yes"))) {
				addCloud();
				System.out.println("Add more Clouds (Yes/No)?");
				s = in.nextLine();
			}
		} catch (Exception e) {
			System.out.println("Exception in newSetup(): " + e);
		}
		// save the meta data
		try {
			FileOutputStream fileOut = new FileOutputStream(
					cloudMetadataPath.toString());
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(cloudMetaData);
			out.close();
			fileOut.close();
			System.out.println("Serialized data is saved in cloudmetadata.ser");
		} catch (IOException i) {
			i.printStackTrace();
		}

		boolean newUser = true;
		for (int i = 0; i < clouds.size(); i++) {
			if (clouds.get(i).searchFile("table.ser(table)")) {
				System.out.println("Found table.ser");
				newUser = false;
				break;
			}
		}
		if (newUser)
			createNewTable();
		else
			populateTable();
	}

	@SuppressWarnings("unchecked")
	private void setup() {
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
				// case "google drive":
				// clouds.add(new GoogleDrive());
				// break;
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
		populateTable();
	}

	private void handleUpload() {
		System.out.println("Enter the path of the file to upload");
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		String filePath = in.nextLine();
		in.close();
		if (!Files.exists(Paths.get(filePath))) {
			System.out.println("File not found");
			return;
		}
		populateTable();
		upload(filePath);
	}

	private void handleDownload() {
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		System.out.println("Enter the name of the file to download");
		String fileName;
		fileName = in.nextLine();
		if (!table.hasFile(fileName)) {
			System.out.println("File not found");
			in.close();
			return;
		}
		System.out.println("Enter the version to download");
		int version;
		version = in.nextInt();
		if (!table.hasFileVersion(fileName, version)) {
			System.out.println("Version not found");
			in.close();
			return;
		}
		in.close();
		download(fileName, version);
	}

	private void sync() {

	}

	private static int showMenu() {
		System.out.println("1. Upload File");
		System.out.println("2. Download File");
		System.out.println("3. Sync with Vault");
		System.out.println("4. Show Files in Vault");
		System.out.println("5. Show File History");
		System.out.println("6. Exit");
		System.out.println("What do you want to do? ");

		System.out.println("Enter the number corresponding to your choice: ");
		Scanner in = new Scanner(new CloseShieldInputStream(System.in));
		int choice = in.nextInt();
		if (choice < 1 || 5 < choice) {
			System.out.println("Invalid choice.");
			System.out.println("You have the following options: ");
			choice = showMenu();
		}
		in.close();
		return choice;
	}

	public static void main(String[] args) {
		try {
			System.out.println("Welcome to your Cloud Vault!");
			// CloseShieldInputStream shieldedIn = new
			// CloseShieldInputStream(System.in);
			// System.setIn(shieldedIn);
			Main prog = new Main();
			prog.run();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void run() {
		Scanner in = new Scanner(System.in);
		String s;
		try {
			if (Files.exists(cloudMetadataPath)) {
				setup();
			} else {
				System.out
						.println("It seems this is the first time you are using Cloud Vault on this device.");
				System.out
						.println("We will now setup access to your Cloud Vault.");
				newSetup();
			}

			int choice;
			do {
				choice = showMenu();
				switch (choice) {
				case 1:
					handleUpload();
					break;
				case 2:
					handleDownload();
					break;
				case 3:
					sync();
					break;
				case 4:
					table.printTable();
					break;
				case 5:
					System.out.println("Enter the name of the file: ");
					s = in.nextLine();
					if (table.hasFile(s)) {
						table.printHistory(s);
					} else {
						System.out.println("File not found");
					}
					break;
				case 6:
					System.exit(0);
				}
				System.out.println("Continue (Yes/No)? ");
				s = in.nextLine();
			} while (s.equals("Yes") || s.equals("yes"));

		} catch (Exception e) {
			System.out.println(e);
			e.printStackTrace();
		} finally {
			in.close();
		}
	}
}
