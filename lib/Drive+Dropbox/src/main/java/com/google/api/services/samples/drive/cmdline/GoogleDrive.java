/*
 * Copyright (c) 2012 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.api.services.samples.drive.cmdline;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Preconditions;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.samples.drive.cmdline.cloud.Cloud;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * A sample application that runs multiple requests against the Drive API. The requests this sample
 * makes are:
 * <ul>
 * <li>Does a resumable media upload</li>
 * <li>Updates the uploaded file by renaming it</li>
 * <li>Does a resumable media download</li>
 * <li>Does a direct media upload</li>
 * <li>Does a direct media download</li>
 * </ul>
 *
 * @author rmistry@google.com (Ravi Mistry)
 */
public class GoogleDrive implements Cloud {

  /**
   * Be sure to specify the name of your application. If the application name is {@code null} or
   * blank, the application will log a warning. Suggested format is "MyCompany-ProductName/1.0".
   */
  private static final String APPLICATION_NAME = "CloudSafe";


  private static final String UPLOAD_FILE_PATH = "testb";
  private static final String DIR_FOR_DOWNLOADS = "Downloads";
  private static final java.io.File UPLOAD_FILE = new java.io.File(UPLOAD_FILE_PATH);

  /** Directory to store user credentials. */
  private static final java.io.File DATA_STORE_DIR = new java.io.File(
      System.getProperty("user.home"), ".store/drive_sample4");

  /**
   * Global instance of the {@link DataStoreFactory}. The best practice is to make it a single
   * globally shared instance across your application.
   */
  private static FileDataStoreFactory dataStoreFactory;

  /** Global instance of the HTTP transport. */

  private static HttpTransport httpTransport;

  /** Global instance of the JSON factory. */
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

  /** Global Drive API client. */
  private static Drive drive;

  /** Authorizes the installed application to access user's protected data. */
  private static Credential authorize() throws Exception {

    GoogleClientSecrets.Details installedDetails = new GoogleClientSecrets.Details();
    installedDetails
        .setClientId("904659510531-l0jgindfe7vd00hkshrthatauqp908pi.apps.googleusercontent.com");
    installedDetails.setClientSecret("YiEnTPHx1ovie8tj5jk9Q189");

    GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
    clientSecrets.setInstalled(installedDetails);

    // load client secrets
    // GoogleClientSecrets clientSecrets =
    // GoogleClientSecrets.load(JSON_FACTORY,
    // new InputStreamReader(GoogleDrive.class.getResourceAsStream("/client_secrets.json")));
    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
      System.out
          .println("Enter Client ID and Secret from https://code.google.com/apis/console/?api=drive "
              + "into drive-cmdline-sample/src/main/resources/client_secrets.json");
      System.exit(1);
    }


    // System.out.println("\tClient Secrets Loading Completed");

    // set up authorization code flow
    GoogleAuthorizationCodeFlow flow =
        new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets,
            Collections.singleton(DriveScopes.DRIVE_FILE)).setDataStoreFactory(dataStoreFactory)
            .build();

    // System.out.println("\tAuthorisation Code Flow Completed");

    // authorize
    return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");


  }

  static HttpTransport newProxyTransport() throws GeneralSecurityException, IOException {
    NetHttpTransport.Builder builder = new NetHttpTransport.Builder();
    builder.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("10.10.78.61", 3128)));
    return builder.build();
  }

  public static boolean mainOld() {
    Preconditions.checkArgument(
        !UPLOAD_FILE_PATH.startsWith("Enter ") && !DIR_FOR_DOWNLOADS.startsWith("Enter "),
        "Please enter the upload file path and download directory in %s", GoogleDrive.class);

    try {
      // httpTransport = GoogleNetHttpTransport.newTrustedTransport();

      httpTransport = newProxyTransport();
      dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
      // authorization


      // System.out.println("Authorisation started");

      Credential credential = authorize();

      // System.out.println("Authorisation succeeded");

      // set up the global Drive instance
      drive =
          new Drive.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(
              APPLICATION_NAME).build();

      // run commands


      // --------

      View.header1("Success!");
      return true;
    } catch (IOException e) {
      System.err.println(e.getMessage());
    } catch (Throwable t) {
      t.printStackTrace();
    }
    return false;
  }

  public String metadata() {
    return "";
  }

  public boolean isAvailable() {
    // TODO Auto-generated method stub
    return mainOld();
  }

  public void uploadFile(byte[] data, String fileID) throws IOException {
    FileOutputStream fos = new FileOutputStream("Downloads/drive_table");
    fos.write(data);
    fos.close();
    java.io.File UPLOAD_FILE = new java.io.File("Downloads/drive_table");
    File uploadedFile = uploadFile(false, UPLOAD_FILE); // resumable media upload
    updateFileWithTestSuffix(uploadedFile.getId(), fileID);
    try {
      Process p = Runtime.getRuntime().exec("rm -rf Downloads/drive_table");
      p.waitFor();
    } catch (IOException e1) {
    } catch (InterruptedException e2) {
    }
  }


  public void uploadFile(String path, String fileID) throws IOException {
    java.io.File UPLOAD_FILE = new java.io.File(path);
    File uploadedFile = uploadFile(false, UPLOAD_FILE); // resumable media upload
    updateFileWithTestSuffix(uploadedFile.getId(), fileID);
  }


  /** Uploads a file using either resumable or direct media upload. */
  private static File uploadFile(boolean useDirectUpload, java.io.File UPLOAD_FILE)
      throws IOException {

    File fileMetadata = new File();
    fileMetadata.setTitle(UPLOAD_FILE.getName());
    // fileMetadata.setId("test_encrypted");

    // System.out.println("Title for the file is : " + UPLOAD_FILE.getName());

    FileContent mediaContent = new FileContent("image/jpeg", UPLOAD_FILE);

    Drive.Files.Insert insert = drive.files().insert(fileMetadata, mediaContent);
    MediaHttpUploader uploader = insert.getMediaHttpUploader();
    uploader.setDirectUploadEnabled(useDirectUpload);
    uploader.setProgressListener(new FileUploadProgressListener());
    // System.out.println("Exiting upload");
    return insert.execute();
  }

  /** Updates the name of the uploaded file to have a "drivetest-" prefix. */
  private static File updateFileWithTestSuffix(String id, String NameOnCloud) throws IOException {
    File fileMetadata = new File();
    fileMetadata.setTitle(NameOnCloud);
    Drive.Files.Update update = drive.files().update(id, fileMetadata);
    return update.execute();
  }

  @Override
  public byte[] downloadFile(String fileID) throws IOException {
    Drive.Files.List file_list = drive.files().list().setQ("title contains '" + fileID + "'");
    // System.out.println("execution started");
    FileList FilesPresent = file_list.execute();
    // System.out.println("execution done");
    java.util.List<File> FileActualList = FilesPresent.getItems();

    View.header1("Starting Simple Media Download");
    OutputStream out = new FileOutputStream(new java.io.File("Downloads", fileID));
    MediaHttpDownloader downloader =
        new MediaHttpDownloader(httpTransport, drive.getRequestFactory().getInitializer());
    downloader.setDirectDownloadEnabled(true);
    downloader.setProgressListener(new FileDownloadProgressListener());
    downloader.download(new GenericUrl(FileActualList.get(0).getDownloadUrl()), out);
    out.close();
    Path path = Paths.get("Downloads/" + fileID);
    return Files.readAllBytes(path);
  }

  public void downloadFile(String path, String fileID) throws IOException {
    Drive.Files.List file_list = drive.files().list().setQ("title contains '" + fileID + "'");
    FileList Files = file_list.execute();
    java.util.List<File> FileActualList = Files.getItems();

    downloadFile(true, FileActualList.get(0), path);
  }


  /** Downloads a file using either resumable or direct media download. */
  private static void downloadFile(boolean useDirectDownload, File uploadedFile, String Filename)
      throws IOException {
    // create parent directory (if necessary)
    java.io.File parentDir = new java.io.File(DIR_FOR_DOWNLOADS);
    if (!parentDir.exists() && !parentDir.mkdirs()) {
      throw new IOException("Unable to create parent directory");
    }

    OutputStream out = new FileOutputStream(new java.io.File(parentDir, Filename));


    MediaHttpDownloader downloader =
        new MediaHttpDownloader(httpTransport, drive.getRequestFactory().getInitializer());


    downloader.setDirectDownloadEnabled(useDirectDownload);

    downloader.setProgressListener(new FileDownloadProgressListener());

    downloader.download(new GenericUrl(uploadedFile.getDownloadUrl()), out);
  }

  @Override
  public boolean searchFile(String fileID) {
    try {
      Drive.Files.List file_list = drive.files().list().setQ("title contains '" + fileID + "'");
      FileList Files = file_list.execute();
      java.util.List<File> FileActualList = Files.getItems();
      if (FileActualList.size() != 0)
        return true;
      else
        return false;
    } catch (IOException e1) {
    }
    return false;
  }
}
