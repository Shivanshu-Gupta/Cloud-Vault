package com.google.api.services.samples.drive.cmdline;

import com.google.api.services.samples.drive.cmdline.cloud.Cloud;
import com.google.api.services.samples.drive.cmdline.util.Pair;

import net.fec.openrq.ArrayDataDecoder;
import net.fec.openrq.ArrayDataEncoder;
import net.fec.openrq.EncodingPacket;
import net.fec.openrq.OpenRQ;
import net.fec.openrq.decoder.SourceBlockDecoder;
// import net.fec.openrq.encoder.DataEncoder;
// import net.fec.openrq.decoder.DataDecoder;
// import net.fec.openrq.decoder.SourceBlockState;
// import net.fec.openrq.parameters.ParameterChecker;
// import net.fec.openrq.util.collection.ImmutableList;
import net.fec.openrq.encoder.SourceBlockEncoder;
import net.fec.openrq.parameters.FECParameters;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;



/**
 * The entry point for the OpenRQ API.
 * <p>
 * This class provides methods for creating encoder objects from source data and FEC parameters, and
 * methods for creating decoder objects that decode source data according to FEC parameters.
 * <p>
 * This class also provides miscellaneous utility methods.
 */
public class Main {

  final static int tableFileSize = 10 * 32;
  final static Path tablePath = Paths.get("trials/table.txt");

  static ArrayList<Cloud> clouds = new ArrayList<Cloud>();
  static ArrayList<Pair<String, String>> cloudMetaData = new ArrayList<Pair<String, String>>();

  static int cloudNum = 4; // Co
  static int cloudDanger = 1; // Cd
  static int overHead = 4; // epsilon

  static Table table;

  public void upload(String filePath) {
    System.out.println("Encoding: ");
    try {
      Path path = Paths.get(filePath);
      byte[] data = Files.readAllBytes(path);
      int fileSize = data.length;
      Pair<FECParameters, Integer> params = getParams(fileSize);
      FECParameters fecParams = params.first;
      int r = params.second;
      // int symSize = fecParams.symbolSize();
      Path name = path.getFileName();
      String fileName = name.toString();
      String fileID;
      if (fileName.equals("table.txt")) {
        fileID = "table";
        // System.out.println("fileName:" + fileName + " fileID:" + fileID + " fileSize:" +
        // fileSize);
      } else {
        fileID = Integer.toString(table.fileCount());
        // System.out.println("fileName:" + fileName + " fileID:" + fileID + " fileSize:" +
        // fileSize);

        table.addNewFile(fileName, fileID, fileSize);
        table.updateTable(tablePath, tableFileSize);
        upload(tablePath.toString());
      }
      ArrayDataEncoder dataEncoder = OpenRQ.newEncoder(data, fecParams);
      System.out.println("dataEncoder created");
      System.out.println("data length: " + dataEncoder.dataLength());
      System.out.println("symbol size: " + dataEncoder.symbolSize());

      System.out.println("expected symbols : " + dataEncoder.dataLength()
          / dataEncoder.symbolSize());

      int packetID = 0, packetCount = 0, idx = 0;
      byte[] packetdata;
      Iterable<SourceBlockEncoder> srcBlkEncoders = dataEncoder.sourceBlockIterable();

      for (SourceBlockEncoder srcBlkEnc : srcBlkEncoders) {
        Iterable<EncodingPacket> srcPackets = srcBlkEnc.sourcePacketsIterable();

        Iterable<EncodingPacket> repPackets = srcBlkEnc.repairPacketsIterable(r);

        System.out.println("Block " + idx);
        for (EncodingPacket srcPack : srcPackets) {
          // System.out.println("storing source packets");
          packetdata = srcPack.asArray();

          int cloudID = packetID % cloudNum;
          Cloud cloud = clouds.get(cloudID);

          if (cloud.isAvailable()) {
            System.out.print("" + packetID);
            cloud.uploadFile(packetdata, fileID + "_" + packetID);
          }
          packetID++;
        }
        System.out.println("number of source packets: " + (packetID - packetCount));

        for (EncodingPacket repPack : repPackets) {
          // System.out.println("storing repair packets");
          packetdata = repPack.asArray();
          int cloudID = packetID % cloudNum;
          Cloud cloud = clouds.get(cloudID);

          if (cloud.isAvailable()) {
            System.out.print("" + packetID);
            cloud.uploadFile(packetdata, fileID + "_" + packetID);
          }

          packetID++;
        }
        System.out.println("total number of encoding packets: " + (packetID - packetCount));

        packetCount = packetID;
        idx++;
      }
    } catch (Exception e) {
      System.out.println("Exception in upload: " + e);
      e.printStackTrace();
    }
  }

  public void download() {
    Scanner in = new Scanner(System.in);
    System.out.println("Enter the name of the file to download");
    String fileName;
    fileName = in.nextLine();
    in.close();

    String fileID = table.fileID(fileName);
    int fileSize = table.fileSize(fileName);

    System.out.println("Downloading: ");

    Pair<FECParameters, Integer> params = getParams(fileSize);
    FECParameters fecParams = params.first;
    int r = params.second;
    int symSize = fecParams.symbolSize();

    int packetID = 0, packetCount = 0;
    try {
      byte[] packetdata;
      // Path packetpath;
      ArrayDataDecoder dataDecoder = OpenRQ.newDecoder(fecParams, 3);

      packetCount = (int) Math.ceil(fileSize / (float) symSize) + r;
      packetID = 0;
      // reading in all the packets into a byte[][]
      List<byte[]> packetList = new ArrayList<byte[]>();
      while (packetID < packetCount) {
        int cloudID = packetID % cloudNum;
        Cloud cloud = clouds.get(cloudID);

        System.out.println("downloading : " + fileID + "_" + packetID);

        if (cloud.isAvailable() && cloud.searchFile(fileID)) {
          System.out.println("downloading : " + fileID + "_" + packetID);
          packetdata = cloud.downloadFile(fileID + "_" + packetID);
          packetList.add(packetdata);
        }
        packetID++;
      }
      System.out.println("packets have been downloaded!");

      // SourceBlockState state;
      packetID = 0;

      System.out.println("Packets available after cloud outage : " + packetList.size());
      while (!dataDecoder.isDataDecoded() && packetID < packetList.size()) {
        byte[] packet = packetList.get(packetID);
        EncodingPacket encPack = dataDecoder.parsePacket(packet, true).value();
        int sbn = encPack.sourceBlockNumber();
        SourceBlockDecoder srcBlkDec = dataDecoder.sourceBlock(sbn);

        // state = srcBlkDec.putEncodingPacket(encPack);
        srcBlkDec.putEncodingPacket(encPack);
        packetID++;
      }

      System.out.println("file has been decoded!");
      System.out.println("used " + packetID + " packets out of " + packetCount + " packets");

      byte dataNew[] = dataDecoder.dataArray();
      Path pathNew = Paths.get("trials/" + fileName);
      Files.write(pathNew, dataNew);
    } catch (Exception e) {
      System.out.println("Exception in download: " + e);
      e.printStackTrace();
    }
  }

  Pair<FECParameters, Integer> getParams(int len) {
    Pair<FECParameters, Integer> params = null;
    try {
      int symSize = (int) Math.round(Math.sqrt((float) len * 12 / (float) overHead));
      int blockCount = 1;
      FECParameters fecParams = FECParameters.newParameters(len, symSize, blockCount);

      int k = (int) Math.ceil((float) len / (float) symSize);
      // System.out.println("k = " + k);

      // double k_cloud = (int) Math.ceil( (float)k / (float)cloudNum );
      // System.out.println("source symbols/packets per cloud = " + k_cloud);

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

  public void createNewTable() {
    try {
      byte[] tabledata = {};
      table = new Table(tabledata);
      table.updateTable(tablePath, tableFileSize);
      upload(tablePath.toString());
    } catch (Exception x) {
      System.out.println("Exception in creating table: " + x);
    }
  }

  public void populateTable() {
    Pair<FECParameters, Integer> params = getParams(tableFileSize);
    FECParameters fecParams = params.first;
    int r = params.second;
    int symSize = fecParams.symbolSize();
    int packetID = 0, packetCount = 0;
    try {
      byte[] packetdata;
      // Path packetpath;
      ArrayDataDecoder dataDecoder = OpenRQ.newDecoder(fecParams, 3);

      packetCount = (int) Math.ceil(tableFileSize / (float) symSize) + r;
      packetID = 0;

      // reading in all the packets into a byte[] list
      List<byte[]> packetList = new ArrayList<byte[]>();
      // String cloudPath;
      while (packetID < packetCount) {
        int cloudID = packetID % cloudNum;
        Cloud cloud = clouds.get(cloudID);
        // cloudPath = "trials/packets/cloud" + Integer.toString(cloudID);
        // Cloud cloud = new FolderCloud(cloudPath);

        if (cloud.isAvailable() && cloud.searchFile("table_" + packetID)) {
          packetdata = cloud.downloadFile("table_" + packetID);
          packetList.add(packetdata);
        }

        packetID++;
      }

      System.out.println("Packets available after cloud outage : " + packetList.size());

      // SourceBlockState state;
      packetID = 0;
      while (!dataDecoder.isDataDecoded() && packetID < packetList.size()) {
        byte[] packet = packetList.get(packetID);

        EncodingPacket encPack = dataDecoder.parsePacket(packet, true).value();

        int sbn = encPack.sourceBlockNumber();
        SourceBlockDecoder srcBlkDec = dataDecoder.sourceBlock(sbn);

        // state = srcBlkDec.putEncodingPacket(encPack);
        srcBlkDec.putEncodingPacket(encPack);
        packetID++;
      }

      System.out.println("used " + packetID + " packets out of " + packetCount + " packets");
      byte tabledata[] = dataDecoder.dataArray();
      Path pathTable = Paths.get("trials/table.txt");
      Files.write(pathTable, tabledata);
      table = new Table(tabledata);
    } catch (Exception e) {
      System.out.println("Exception in populateTable: " + e);
      e.printStackTrace();
    }
  }

  private void addCloud() {
    Scanner in = new Scanner(System.in);
    String s = null;
    Cloud cloud;
    System.out.println("Select one amongst the following drives: ");
    System.out.println("1. Dropbox\t" + "2. Google Drive\t" + "3. Onedrive\t" + "4. Box\t"
        + "5. Folder");
    System.out.println("Enter drive number as choice: ");
    s = in.nextLine();
    while (!s.equals("1") && !s.equals("2") && !s.equals("3") && !s.equals("4") && !s.equals("5")) {
      System.out.println("Invalid choice! Enter drive number as choice: ");
      s = in.nextLine();
    }
    switch (s) {
      case "1":
        cloud = new Dropbox();
        clouds.add(cloud);
        cloudMetaData.add(Pair.of("dropbox", cloud.metadata()));
        break;
      case "2":
        cloud = new GoogleDrive();
        clouds.add(cloud);
        cloudMetaData.add(Pair.of("googledrive", cloud.metadata()));
        break;
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
  }

  private void newSetup() {
    String s;
    Scanner in = new Scanner(System.in);
    try {
      for (int i = 0; i < 4; i++) {
        System.out.println("CLOUD " + i);
        addCloud();
      }
      System.out.println("Add more Clouds (Yes/NO)?");
      s = in.nextLine();
      while ((s.equals("Yes"))) {
        addCloud();
        System.out.println("Add more Clouds (Yes/NO)?");
        s = in.nextLine();
      }
    } catch (Exception e) {
      System.out.println("Exception in newSetup(): " + e);
    }
    // save the meta data
    try {
      FileOutputStream fileOut = new FileOutputStream("cloudmetadata.ser");
      ObjectOutputStream out = new ObjectOutputStream(fileOut);
      out.writeObject(cloudMetaData);
      out.close();
      fileOut.close();
      System.out.println("Serialized data is saved in cloudmetadata.ser");
    } catch (IOException i) {
      i.printStackTrace();
    }

    // create and upload the new table
    createNewTable();
  }

  @SuppressWarnings("unchecked")
  private void setup() {
    try {
      FileInputStream fileIn = new FileInputStream("cloudmetadata.ser");
      ObjectInputStream in = new ObjectInputStream(fileIn);
      cloudMetaData = (ArrayList<Pair<String, String>>) in.readObject();
      for (Pair<String, String> metadata : cloudMetaData) {
        switch (metadata.first) {
          case "dropbox":
            clouds.add(new Dropbox(metadata.second));
            break;
          // case "google drive" : clouds.add(new Dropbox());
          // break;
          // case "onedrive" : clouds.add(new Dropbox());
          // break;
          // case "box" : clouds.add(new Dropbox());
          // break;
          case "googledrive":
            clouds.add(new GoogleDrive());
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

  public static void main(String[] args) {
    try {
      Main prog = new Main();
      prog.run();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public void run() {
    System.out.println("And the game begins...");
    Scanner in = new Scanner(System.in);
    try {
      System.out.println("Signup or Login (S/L)?  ");
      String s;
      s = in.nextLine();
      if (s.equals("S") || s.equals("s"))
        newSetup();
      else if (s.equals("L") || s.equals("l"))
        setup();
      else {
        System.out.println("Invalid Choice");
        System.exit(0);
      }

      System.out.println("Upload or Download (U/D)?  ");
      s = in.nextLine();
      do {
        if (s.equals("U") || s.equals("u")) {
          System.out.println("Enter the path of the file to upload");
          String filePath = in.nextLine();
          upload(filePath);
        } else if (s.equals("D") || s.equals("d"))
          download();
        else {
          System.out.println("Invalid Choice");
          System.exit(0);
        }
        System.out.println("Continue (Yes/No)? ");
        s = in.nextLine();
      } while (s.equals("Yes") || s.equals("yes"));
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
    }
  }
}
