package com.google.api.services.samples.drive.cmdline;

// import java.io.IOException;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ; // for READ, WRITE etc
import static java.nio.file.StandardOpenOption.WRITE;

import com.google.api.services.samples.drive.cmdline.util.Pair;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
// import java.io.BufferedReader;
// import java.io.BufferedWriter;
// import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;

class Table {

  private static HashMap<String, Pair<String, Integer>> table =
      new HashMap<String, Pair<String, Integer>>();

  public Table(byte[] data) {
    System.out.println("reading Table file:");
    try {
      String tableData = new String(data);
      System.out.println(tableData);
      if (tableData.length() > 0) {
        String temp1[], temp2[];
        String fileName, fileID;
        Integer fileSize;
        String[] records = tableData.split("\n");
        int idx = 0;
        System.out.println("number of records: " + (records.length - 1));
        // for(String record : records)
        while (idx < records.length - 1) {
          String record = records[idx];
          temp1 = record.split("\t=\t");
          fileName = temp1[0];
          temp2 = temp1[1].split("\t");
          fileID = temp2[0];
          fileSize = Integer.parseInt(temp2[1]);

          Pair<String, Integer> idAndSize = Pair.of(fileID, fileSize);
          table.put(fileName, idAndSize);
          idx++;
        }
      }
    } catch (Exception x) {
      System.out.println("Exception in table constructor: " + x);
    }
  }

  // public Table(Path tablePath)
  // {
  // System.out.println("reading Table:");
  // try
  // {
  // tablePath = tablePath.toRealPath();
  // byte[] data = Files.readAllBytes(tablePath);

  // String tableData = new String(data);
  // String temp[];
  // String fileName, fileID;
  // Integer fileSize;
  // String[] records = tableData.split("\n");
  // for(String record : records)
  // {
  // temp = record.split("\t=\t");
  // fileName = temp[0];
  // temp = temp[1].split("\t");
  // fileID = temp[0];
  // fileSize = Integer.parseInt(temp[1]);

  // Pair<String, Integer> idAndSize = new Pair<String, Integer>(fileID, fileSize);
  // table.put(fileName, idAndSize);
  // }
  // System.out.println("read the table.");
  // }
  // catch (IOException x) {
  // System.err.format("IOException: %s%n", x);
  // }
  // }

  public final int fileCount() {
    try {
      System.out.println("Is the table empty? " + table.isEmpty());
      System.out.println(table.size());
    } catch (Exception e) {
      System.out.println("Exception in fileCount: " + e);
    }
    return table.size();
  }

  public final void addNewFile(String fileName, String fileID, Integer fileSize) {
    Pair<String, Integer> idAndSize = Pair.of(fileID, fileSize);
    table.put(fileName, idAndSize);
  }

  public final String fileID(String fileName) {
    return table.get(fileName).first;
  }

  public final Integer fileSize(String fileName) {
    return table.get(fileName).second;
  }

  public final void updateTable(Path tablePath, Integer tableSize) {
    try (FileChannel tableChannel = FileChannel.open(tablePath, READ, WRITE, CREATE)) {
      tableChannel.truncate(0);
      tableChannel.position(0);
      ByteBuffer buf;
      for (Object fileName : table.keySet()) {

        Pair<String, Integer> idAndSize = table.get(fileName);
        String line =
            (String) fileName + "\t=\t" + idAndSize.first + "\t" + idAndSize.second + '\n';
        byte[] data = line.getBytes();
        buf = ByteBuffer.wrap(data);
        while (buf.hasRemaining()) {
          tableChannel.write(buf);
        }
      }
      tableChannel.position(tableSize);
      byte[] temp = new byte[1];
      temp[0] = 0;
      buf = ByteBuffer.wrap(temp);

      while (buf.hasRemaining()) {
        tableChannel.write(buf);
      }
      tableChannel.truncate(tableSize);
    } catch (Exception x) {
      System.out.println("table update error: " + x);
    }
  }
}
