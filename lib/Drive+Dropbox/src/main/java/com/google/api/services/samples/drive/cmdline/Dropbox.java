package com.google.api.services.samples.drive.cmdline;

import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuthNoRedirect;
import com.dropbox.core.DbxWriteMode;
import com.dropbox.core.http.HttpRequestor;
import com.dropbox.core.http.StandardHttpRequestor;
import com.google.api.services.samples.drive.cmdline.cloud.Cloud;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.List;
import java.util.Locale;

public class Dropbox implements Cloud {

  DbxClient client;
  String accessToken = null;

  public Dropbox() {
    setupNewAccess();
  }

  public Dropbox(String accessToken) {
    // System.out.println("Access Token: " + accessToken);
    this.accessToken = accessToken;
    setupAccess();
  }

  public HttpRequestor getProxy() {

    // if ("true".equals(System.getProperty("proxy", "false"))) {
    String ip = "proxy62.iitd.ernet.in";
    int port = 3128;

    final String authUser = "bb5130008";
    final String authPassword = "navij_tnayaj";

    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(authUser, authPassword.toCharArray());
      }
    });

    Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ip, port));

    HttpRequestor req = new StandardHttpRequestor(proxy);
    return req;
    // }
    // return null;
  }

  private void setupNewAccess() {
    // Get your app key and secret from the Dropbox developers website.
    final String APP_KEY = "lxu7s0kd7tse3ee";
    final String APP_SECRET = "8e2yj26uvjor3w0";

    DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);

    DbxRequestConfig config;
    HttpRequestor requ = getProxy();
    if (requ != null)
      config = new DbxRequestConfig("JavaTutorial/1.0", Locale.getDefault().toString(), requ);
    else
      config = new DbxRequestConfig("JavaTutorial/1.0", Locale.getDefault().toString());

    DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);

    // Have the user sign in and authorize your app.
    String authorizeUrl = webAuth.start();
    System.out.println("1. Go to: " + authorizeUrl);
    System.out.println("2. Click \"Allow\" (you might have to log in first)");
    System.out.println("3. Copy the authorization code.");

    try {
      String code = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
      // This will fail if the user enters an invalid authorization code.
      DbxAuthFinish authFinish = webAuth.finish(code);
      accessToken = authFinish.accessToken;

      // // store the code for future use.
      // Path path = Paths.get("DropboxAccessToken.txt");
      // byte[] buf = accessToken.getBytes();
      // Files.write(path, buf, WRITE, TRUNCATE_EXISTING, CREATE);

      client = new DbxClient(config, accessToken);

      System.out.println("Linked account: " + client.getAccountInfo().displayName);
    } catch (DbxException dbe) {
      System.out.println("DbxException: " + dbe);
      dbe.printStackTrace();
    } catch (IOException x) {
      System.out.println("IOException: " + x);
      x.printStackTrace();
    }
  }

  private void setupAccess() {
    DbxRequestConfig config;
    HttpRequestor requ = getProxy();
    if (requ != null)
      config = new DbxRequestConfig("JavaTutorial/1.0", Locale.getDefault().toString(), requ);
    else
      config = new DbxRequestConfig("JavaTutorial/1.0", Locale.getDefault().toString());
    try {

      client = new DbxClient(config, accessToken);

      System.out.println("Linked account: " + client.getAccountInfo().displayName);
    } catch (DbxException dbe) {
      System.out.println("DbxException: " + dbe);
      dbe.printStackTrace();
    }
  }

  public String metadata() {
    return accessToken;
  }

  @Override
  public boolean isAvailable() {
    // TODO Auto-generated method stub
    return true;
  }

  @Override
  public void uploadFile(byte[] data, String fileID) throws IOException {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
    try {
      DbxEntry.File uploadedFile =
          client.uploadFile("/CloudSafe/" + fileID, DbxWriteMode.add(), data.length, inputStream);
      // System.out.println("Uploaded: " + uploadedFile.toString());
    } catch (DbxException dbe) {
      System.out.println("DbxException: " + dbe);
      dbe.printStackTrace();
    } finally {
      inputStream.close();
    }

  }

  @Override
  public void uploadFile(String filename, String fileID) throws IOException {
    File inputFile = new File(filename);
    FileInputStream inputStream = new FileInputStream(inputFile);
    try {
      DbxEntry.File uploadedFile =
          client.uploadFile("/CloudSafe/" + fileID, DbxWriteMode.add(), inputFile.length(),
              inputStream);
      // System.out.println("Uploaded: " + uploadedFile.toString());
    } catch (DbxException dbe) {
      System.out.println("DbxException: " + dbe);
      dbe.printStackTrace();
    } finally {
      inputStream.close();
    }
  }

  @Override
  public byte[] downloadFile(String fileID) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      DbxEntry.File downloadedFile = client.getFile("/CloudSafe/" + fileID, null, outputStream);
      // System.out.println("Metadata: " + downloadedFile.toString());
    } catch (DbxException dbe) {
      System.out.println("DbxException: " + dbe);
      dbe.printStackTrace();
    } finally {
      outputStream.close();
    }
    return outputStream.toByteArray();
  }

  @Override
  public void downloadFile(String filename, String fileID) throws IOException {
    FileOutputStream outputStream = new FileOutputStream(filename);
    try {
      DbxEntry.File downloadedFile = client.getFile("/CloudSafe/" + fileID, null, outputStream);
      // System.out.println("Metadata: " + downloadedFile.toString());
    } catch (DbxException dbe) {
      System.out.println("DbxException: " + dbe);
      dbe.printStackTrace();
    } finally {
      outputStream.close();
    }
  }

  @Override
  public boolean searchFile(String fileID) {
    try {
      if (client == null) {
        System.out.println("Dafuq???!!");
      }
      List<DbxEntry> matchList = client.searchFileAndFolderNames("/CloudSafe", fileID);
      if (matchList == null || matchList.size() == 0)
        return false;
      else
        return true;
    } catch (DbxException dbe) {
      System.out.println("DbxException: " + dbe);
      dbe.printStackTrace();
    }
    return false;
  }
}
