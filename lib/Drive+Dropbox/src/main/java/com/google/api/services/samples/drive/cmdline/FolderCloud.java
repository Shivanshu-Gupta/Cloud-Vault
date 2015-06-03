// package net.fec.openrq;
package com.google.api.services.samples.drive.cmdline;

import static java.nio.file.StandardOpenOption.CREATE; // for READ, WRITE etc
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import com.google.api.services.samples.drive.cmdline.cloud.Cloud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

// import net.fec.openrq.cloud.Cloud;

public final class FolderCloud implements Cloud {

  Path cloudPath;

  public FolderCloud(String cloudPath) {
    this.cloudPath = Paths.get(cloudPath);
  }

  public FolderCloud() {
    Scanner in = new Scanner(System.in);
    String s;
    System.out.println("Enter the path to the folder: ");
    s = in.nextLine();
    this.cloudPath = Paths.get(s);
  }

  @Override
  public String metadata() {
    return cloudPath.toString();
  }


  // @Override
  // public void setupNewAccess() {
  // return;
  // }
  //
  // @Override
  // public void setupAccess() {
  // return;
  // }

  public boolean isAvailable() {
    return Files.exists(cloudPath);
  }

  public void uploadFile(byte[] data, String fileID) {
    try {
      Path filePath = Paths.get(cloudPath.toString() + '/' + fileID);
      Files.write(filePath, data, CREATE);
    } catch (IOException x) {
      System.err.format("IOException in uploadFile: %s%n", x);
    }
  }

  @Override
  public void uploadFile(String name, String fileID) {
    try {
      Path path = Paths.get(name);
      byte[] data = Files.readAllBytes(path);
      Path filePath = Paths.get(cloudPath.toString() + '/' + fileID);
      Files.write(filePath, data, CREATE);
    } catch (IOException x) {
      System.err.format("IOException in uploadFile: %s%n", x);
    }
  }

  public byte[] downloadFile(String fileID) {
    byte[] data = {};
    try {
      Path filePath = Paths.get(cloudPath.toString() + "/" + fileID);
      data = Files.readAllBytes(filePath);
    } catch (IOException x) {
      System.err.format("IOException: %s%n", x);
    }
    return data;
  }


  @Override
  public void downloadFile(String path, String fileID) throws IOException {
    byte[] data = {};
    try {
      Path filePath = Paths.get(cloudPath.toString() + "/" + fileID);
      data = Files.readAllBytes(filePath);
      Files.write(Paths.get(path), data, CREATE, TRUNCATE_EXISTING);
    } catch (IOException x) {
      System.err.format("IOException: %s%n", x);
    }
  }

  @Override
  public boolean searchFile(String fileID) {
    Path filePath = Paths.get(cloudPath.toString() + "/" + fileID);
    return Files.exists(filePath);
  }
}
