package com.google.api.services.samples.drive.cmdline.cloud;

import java.io.IOException;

public interface Cloud {

  // private void setupNewAccess() throws IOException;
  //
  // public void setupAccess() throws IOException;
  public String metadata();

  public boolean isAvailable();

  public void uploadFile(byte[] data, String fileID) throws IOException;

  public void uploadFile(String path, String fileID) throws IOException;

  public byte[] downloadFile(String fileID) throws IOException;

  public void downloadFile(String path, String fileID) throws IOException;

  public boolean searchFile(String fileID);

}
