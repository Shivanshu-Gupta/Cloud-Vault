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

package cloudsafe.cloud;

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
//import com.google.api.client.util.Preconditions;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
//import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A sample application that runs multiple requests against the Drive API. The
 * requests this sample makes are:
 * <ul>
 * <li>Does a resumable media upload</li>
 * <li>Updates the uploaded file by renaming it</li>
 * <li>Does a resumable media download</li>
 * <li>Does a direct media upload</li>
 * <li>Does a direct media download</li>
 * </ul>
 */
public class GoogleDrive implements Cloud {
	public static final String NAME = "GOOGLEDRIVE";
	private Proxy proxy;
	private boolean available = true;
	private final static Logger logger = LogManager
			.getLogger(GoogleDrive.class.getName());

	/**
	 * Be sure to specify the name of your application. If the application name
	 * is {@code null} or blank, the application will log a warning. Suggested
	 * format is "MyCompany-ProductName/1.0".
	 */
	private static final String APPLICATION_NAME = "CloudVault";
	static final String assistingFolder = "trials/googledrive";
	private static final String DIR_FOR_DOWNLOADS = assistingFolder;
	static int userIndex;

	/** Directory to store user credentials. */
	// Set up a location to store retrieved credentials. This avoids having to
	// ask for authorization
	// every time the application is run
	private static java.io.File DATA_STORE_DIR = new java.io.File(
			System.getProperty("user.home"), ".store/sura_01");
//	private static final java.io.File DATA_STORE_DIR2 = new java.io.File(
//			System.getProperty("user.home"), ".store/drive_sample_2_0");

	/**
	 * Global instance of the {@link DataStoreFactory}. The best practice is to
	 * make it a single globally shared instance across your application.
	 */
	private static FileDataStoreFactory dataStoreFactory;

	/** Global instance of the HTTP transport. */
	private static HttpTransport httpTransport;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory
			.getDefaultInstance();

	/** Global Drive API client. */
	private static Drive drive;

	static String CloudVaultFolderID = null;

	public GoogleDrive(Proxy proxy, int userIndex) {
		this.proxy = proxy;
		GoogleDrive.userIndex = userIndex;
		mainOld(proxy);
	}

	/** Authorizes the installed application to access user's protected data. */
	private static Credential authorize() throws IOException {

		GoogleClientSecrets.Details installedDetails = new GoogleClientSecrets.Details();
		installedDetails
				.setClientId("904659510531-l0jgindfe7vd00hkshrthatauqp908pi.apps.googleusercontent.com");
		installedDetails.setClientSecret("YiEnTPHx1ovie8tj5jk9Q189");

		GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
		clientSecrets.setInstalled(installedDetails);

		// load client secrets
		if (clientSecrets.getDetails().getClientId().startsWith("Enter")
				|| clientSecrets.getDetails().getClientSecret()
						.startsWith("Enter ")) {
			logger.info("Enter Client ID and Secret from https://code.google.com/apis/console/?api=drive "
							+ "into src/lib/client_secrets.json");
			System.exit(1);
		}
		// set up authorization code flow
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				httpTransport, JSON_FACTORY, clientSecrets,
				Collections.singleton(DriveScopes.DRIVE_FILE))
				.setDataStoreFactory(dataStoreFactory).build();
		// authorize
		LocalServerReceiver recieve = new LocalServerReceiver();
		return new AuthorizationCodeInstalledApp(flow, recieve)
				.authorize("user");
	}
	
	public boolean isAvailable() {
		if(!available)
		{
			available = mainOld(proxy);
		}
		return available;
	}
	static HttpTransport newProxyTransport(Proxy proxy)
			throws GeneralSecurityException, IOException {
		NetHttpTransport.Builder builder = new NetHttpTransport.Builder();
		builder.setProxy(proxy);
		return builder.build();
	}

	public static boolean mainOld(Proxy proxy) {
		try {
			httpTransport = newProxyTransport(proxy);
//			if(userIndex == 1){
				if(DATA_STORE_DIR.exists())
				{
					DATA_STORE_DIR.delete();
					DATA_STORE_DIR = new java.io.File(
							System.getProperty("user.home"), ".store/sura_01");
				}
				dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
//			}
//			else
//				dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR2);
				
			// authorization
			Credential credential = authorize();
			// set up the global Drive instance
			drive = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
					.setApplicationName(APPLICATION_NAME).build();

			Drive.Files.List file_list = drive
					.files()
					.list()
					.setQ("title='CloudVault' and mimeType='application/vnd.google-apps.folder' and trashed=false");
			FileList CHILDREN = file_list.execute();

			java.util.List<File> ChildActualList = CHILDREN.getItems();
			if (ChildActualList.size() >= 1) {
				CloudVaultFolderID = ChildActualList.get(0).getId();
			} else {
				File body = new File();
				body.setTitle("CloudVault");
				body.setMimeType("application/vnd.google-apps.folder");
				File file = drive.files().insert(body).execute();
				CloudVaultFolderID = file.getId();
			}

			return true;
		} catch (IOException | GeneralSecurityException e) {
			System.err.println(e.getMessage());
		}
		return false;
	}

	public ConcurrentHashMap<String, String> getUserInfo() {
		ConcurrentHashMap<String, String> meta = new ConcurrentHashMap<String, String>();
		try {
			About about = drive.about().get().execute();
			meta.put("uid", String.valueOf(about.getPermissionId()));
			meta.put("username", about.getName());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return meta;
	}
	
	
	public ConcurrentHashMap<String, String> getMetaData() {
		return getUserInfo();
	}



	public void uploadFile(byte[] data, String fileID, WriteMode mode) {
		String tempPath = assistingFolder + "/" + fileID;
		try {
			if (!Files.exists(Paths.get(tempPath))) {
				Files.createDirectories(Paths.get(tempPath).getParent());
			}
			FileOutputStream fos = new FileOutputStream(tempPath);
			fos.write(data);
			fos.close();
			uploadFile(tempPath, fileID, mode);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
	}

	public void uploadFile(String path, String fileID, WriteMode mode)
	{
		try {
			if (mode == WriteMode.ADD) {
				java.io.File UPLOAD_FILE = new java.io.File(path);
				File uploadedFile = uploadFile(false, UPLOAD_FILE); // resumable
																	// media upload
				updateFileWithTestSuffix(uploadedFile.getId(), fileID);
			} else if (mode == WriteMode.OVERWRITE) {
				Drive.Files.List file_list = drive
						.files()
						.list()
						.setQ("title = '" + fileID + "' and '" + CloudVaultFolderID
								+ "' in parents and" + " trashed=false");
				FileList Files = file_list.execute();

				java.util.List<File> FileActualList = Files.getItems();
				if (FileActualList.size() == 0) {
					uploadFile(path, fileID, WriteMode.ADD);
				} else {
					java.io.File UPLOAD_FILE = new java.io.File(path);
					File fileMetadata = new File();
					FileContent mediaContent = new FileContent("image/jpeg",
							UPLOAD_FILE);
					Drive.Files.Update update = drive.files().update(
							FileActualList.get(0).getId(), fileMetadata,
							mediaContent);
					update.execute();
				}
			}
		} catch (IOException e) {
			available  = false;
		}

	}

	/** Uploads a file using either resumable or direct media upload. */
	private static File uploadFile(boolean useDirectUpload,
			java.io.File UPLOAD_FILE) throws IOException {
		File fileMetadata = new File();
		fileMetadata.setTitle(UPLOAD_FILE.getName());
		fileMetadata.setParents(Arrays.asList(new ParentReference()
				.setId(CloudVaultFolderID)));
		FileContent mediaContent = new FileContent("image/jpeg", UPLOAD_FILE);
		Drive.Files.Insert insert = drive.files().insert(fileMetadata,
				mediaContent);
		MediaHttpUploader uploader = insert.getMediaHttpUploader();
		uploader.setDirectUploadEnabled(useDirectUpload);
		return insert.execute();
	}

	/** Updates the name of the uploaded file to have a "drivetest-" prefix. */
	private static File updateFileWithTestSuffix(String id, String NameOnCloud)
			throws IOException {
		File fileMetadata = new File();
		fileMetadata.setTitle(NameOnCloud);
		Drive.Files.Update update = drive.files().update(id, fileMetadata);
		return update.execute();
	}

	@Override
	public byte[] downloadFile(String fileID){
		try {
			Drive.Files.List file_list = drive
					.files()
					.list()
					.setQ("title = '" + fileID + "' and '" + CloudVaultFolderID
							+ "' in parents and" + " trashed=false");
			FileList FilesPresent = file_list.execute();
			java.util.List<File> FileActualList = FilesPresent.getItems();
			OutputStream out = new FileOutputStream(new java.io.File(
					assistingFolder, fileID));
			MediaHttpDownloader downloader = new MediaHttpDownloader(httpTransport,
					drive.getRequestFactory().getInitializer());
			downloader.setDirectDownloadEnabled(false);
			downloader.download(new GenericUrl(FileActualList.get(0)
					.getDownloadUrl()), out);
			Path path = Paths.get(assistingFolder + "/" + fileID);
			return Files.readAllBytes(path);
			
		} catch (IOException e) {
			available = false;
			return null;
		}

	}

	public void downloadFile(String path, String fileID){
		try {
			Drive.Files.List file_list = drive
					.files()
					.list()
					.setQ("title = '" + fileID + "' and '" + CloudVaultFolderID
							+ "' in parents and" + " trashed=false");
			FileList Files = file_list.execute();
			java.util.List<File> FileActualList = Files.getItems();
			downloadFile(false, FileActualList.get(0), path);
		} catch (IOException e) {
			available = false;
		}
	}

	/** Downloads a file using either resumable or direct media download. */
	private static void downloadFile(boolean useDirectDownload,
			File uploadedFile, String path) throws IOException {
		// create parent directory (if necessary)
		java.io.File parentDir = new java.io.File(DIR_FOR_DOWNLOADS);
		if (!parentDir.exists() && !parentDir.mkdirs()) {
			throw new IOException("Unable to create parent directory");
		}
		OutputStream out = new FileOutputStream(new java.io.File(path));
		MediaHttpDownloader downloader = new MediaHttpDownloader(httpTransport,
				drive.getRequestFactory().getInitializer());
		downloader.setDirectDownloadEnabled(useDirectDownload);
		downloader.download(new GenericUrl(uploadedFile.getDownloadUrl()), out);
	}

	@Override
	public boolean searchFile(String fileID) {
		try {
			Drive.Files.List file_list = drive
					.files()
					.list()
					.setQ("title = '" + fileID + "' and '" + CloudVaultFolderID
							+ "' in parents and" + " trashed=false");
			FileList Files = file_list.execute();
			java.util.List<File> FileActualList = Files.getItems();
			if (FileActualList.size() != 0)
				return true;
			else
				return false;
		} catch (IOException e1) {
			available = false;
		} catch (NullPointerException e) {
			return false;
		}
		return false;
	}

	public void deleteFile(String Filename) {
		try {
			Drive.Files.List file_list = drive
					.files()
					.list()
					.setQ("title = '" + Filename + "' and '" + CloudVaultFolderID
							+ "' in parents and" + " trashed=false");
			FileList Files = file_list.execute();
			java.util.List<File> FileActualList = Files.getItems();
			if (FileActualList.size() != 0)
			{
				drive.files().delete(FileActualList.get(0).getId()).execute();
				return;
			}
			else
				return;
		} catch (IOException e1) {
			available = false;
		} catch (NullPointerException e) {
			return;
		}
		return;
	}
}
