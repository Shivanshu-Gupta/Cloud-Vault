package cloudsafe;

import com.box.boxjavalibv2.*;
import com.box.boxjavalibv2.dao.*;
import com.box.boxjavalibv2.exceptions.*;
import com.box.boxjavalibv2.jsonparsing.BoxJSONParser;
import com.box.boxjavalibv2.jsonparsing.BoxResourceHub;
import com.box.boxjavalibv2.requests.requestobjects.BoxFolderRequestObject;
//import com.box.boxjavalibv2.requests.requestobjects.*;
import com.box.restclientv2.IBoxRESTClient;
import com.box.restclientv2.exceptions.*;
import com.box.restclientv2.requestsbase.BoxDefaultRequestObject;
import com.box.restclientv2.requestsbase.BoxFileUploadRequestObject;
//import com.box.restclientv2.requestsbase.BoxOAuthRequestObject;


import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.WriteMode;

import java.io.*;
import java.awt.Desktop;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.params.ConnRoutePNames;

public class Box implements Cloud {
	private String ID;
	
	private static final int PORT = 4000;
	private static final String key = "okg0mkf7xmbx371w0awevez9m7jxuhes";
	private static final String secret = "trrTxLEN8x5ZtOZbg45pPZp4uFpBERbx";
	
	Boolean available = true;			// for availability of cloud
	
	String code = null;
	BoxClient client;
	public String metadata = "";
	String CloudVaultFolderID = "";

	public Box(String cloudID, Proxy proxy) throws BoxRestException, BoxServerException,
			AuthFatalFailureException {
		this.setID(cloudID);
		String url = "https://www.box.com/api/oauth2/authorize?response_type=code&client_id="
				+ key + "&redirect_uri=http%3A//localhost%3A" + PORT;
		try {

			Desktop.getDesktop().browse(java.net.URI.create(url));
			code = getCode();

		} catch (IOException e) {
			e.printStackTrace();
		}
		client = getAuthenticatedClient(code, proxy);
		CloudVaultFolderID = getCloudVaultFolderID();
	}

	public String metadata() {
		return code;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	public void uploadFile(byte[] data, String fileID, WriteMode mode) {
		FileOutputStream fos;
		String tempPath = GoogleDrive.assistingFolder+"/"+fileID;
		try {
			if (!Files.exists(Paths.get(tempPath))) {
				Files.createDirectories(Paths.get(tempPath).getParent());
			}
			fos = new FileOutputStream(tempPath);
			fos.write(data);
			fos.close();
			uploadFile(tempPath, fileID, mode);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void uploadFile(String path, String fileID, WriteMode mode) {
		if (mode == WriteMode.ADD) {
			java.io.File UPLOAD_FILE = new java.io.File(path);
			BoxFileUploadRequestObject requestObj;

			try {
				requestObj = BoxFileUploadRequestObject
						.uploadFileRequestObject(CloudVaultFolderID, fileID,
								UPLOAD_FILE);
				client.getFilesManager().uploadFile(requestObj);
			} catch(BoxJSONException | AuthFatalFailureException e) {
				//TODO
			} catch (BoxRestException | BoxServerException
					 | InterruptedException e) {
				available = false;
			}

			// System.out.println("file_uploaded");

		} else if (mode == WriteMode.OVERWRITE) {
			BoxFolder boxFolder;
			try {
				boxFolder = client.getFoldersManager().getFolder(
						CloudVaultFolderID, null);
				ArrayList<BoxTypedObject> folderEntries = boxFolder
						.getItemCollection().getEntries();
				int folderSize = folderEntries.size();
				String RequiredFileID = "";
				for (int i = 0; i <= folderSize - 1; i++) {
					BoxTypedObject folderEntry = folderEntries.get(i);
					String name = (folderEntry instanceof BoxItem) ? ((BoxItem) folderEntry)
							.getName() : "(unknown)";
					// System.out.println("i:" + i + ", Type:"
					// + folderEntry.getType() + ", Id:"
					// + folderEntry.getId() + ", Name:" + name);
					if (name.equals(fileID)) {
						RequiredFileID = folderEntry.getId();
					}
				}
				if (RequiredFileID.equals("")) {
					uploadFile(path, fileID, WriteMode.ADD);
				} else {
					java.io.File UPLOAD_FILE = new java.io.File(path);
					BoxFileUploadRequestObject requestObj;
					requestObj = BoxFileUploadRequestObject
							.uploadFileRequestObject(CloudVaultFolderID, fileID,
									UPLOAD_FILE);
					client.getFilesManager().uploadNewVersion(RequiredFileID,
							requestObj);
					// System.out.println("file_updated");
				}
			} catch (BoxRestException | BoxServerException
					| AuthFatalFailureException | InterruptedException
					| BoxJSONException e) {
				e.printStackTrace();
			}

		}
	}

	public byte[] downloadFile(String fileID) throws IOException {
		BoxFolder boxFolder;
		byte[] byteArray = null;
		try {
			boxFolder = client.getFoldersManager().getFolder(CloudVaultFolderID,
					null);
			ArrayList<BoxTypedObject> folderEntries = boxFolder
					.getItemCollection().getEntries();
			int folderSize = folderEntries.size();
			String RequiredFileID = "";
			for (int i = 0; i <= folderSize - 1; i++) {
				BoxTypedObject folderEntry = folderEntries.get(i);
				String name = (folderEntry instanceof BoxItem) ? ((BoxItem) folderEntry)
						.getName() : "(unknown)";
				if (name.equals(fileID)) {
					RequiredFileID = folderEntry.getId();
				}
			}
			InputStream in = client.getFilesManager().downloadFile(
					RequiredFileID, null);
			return IOUtils.toByteArray(in);
		} catch (BoxRestException | BoxServerException
				| AuthFatalFailureException e) {
			e.printStackTrace();
		}
		return byteArray;

	}

	@SuppressWarnings("resource")
	public void downloadFile(String path, String fileID) {
		try {
			BoxFolder boxFolder = client.getFoldersManager().getFolder(
					CloudVaultFolderID, null);
			ArrayList<BoxTypedObject> folderEntries = boxFolder
					.getItemCollection().getEntries();
			int folderSize = folderEntries.size();
			String RequiredFileID = "";
			for (int i = 0; i <= folderSize - 1; i++) {
				BoxTypedObject folderEntry = folderEntries.get(i);
				String name = (folderEntry instanceof BoxItem) ? ((BoxItem) folderEntry)
						.getName() : "(unknown)";
				if (name.equals(fileID)) {
					RequiredFileID = folderEntry.getId();
				}
			}

			InputStream in = client.getFilesManager().downloadFile(
					RequiredFileID, null);
			OutputStream out = null;
			out = new FileOutputStream(new java.io.File("path"));
			int read = 0;
			byte[] bytes = new byte[1024];
			while ((read = in.read(bytes)) != -1) {
				out.write(bytes, 0, read);
			}
		} catch (BoxRestException | BoxServerException
				| AuthFatalFailureException | IOException e) {
			e.printStackTrace();
		}
	}

	public boolean searchFile(String fileID) {
		try {
			if (CloudVaultFolderID.equals("")) {
				CloudVaultFolderID = getCloudVaultFolderID();
			}
			BoxFolder boxFolder = client.getFoldersManager().getFolder(
					CloudVaultFolderID, null);
			ArrayList<BoxTypedObject> folderEntries = boxFolder
					.getItemCollection().getEntries();
			int folderSize = folderEntries.size();
			for (int i = 0; i <= folderSize - 1; i++) {
				BoxTypedObject folderEntry = folderEntries.get(i);
				String name = (folderEntry instanceof BoxItem) ? ((BoxItem) folderEntry)
						.getName() : "(unknown)";
				if (name.equals(fileID)) {
					return true;
				}
			}
		} catch (BoxRestException | BoxServerException
				| AuthFatalFailureException e) {
			e.printStackTrace();
		}
		return false;
	}

	public void deleteFile(String Filename) {
		try {
			if (CloudVaultFolderID.equals("")) {
				CloudVaultFolderID = getCloudVaultFolderID();
			}
			BoxFolder boxFolder = client.getFoldersManager().getFolder(
					CloudVaultFolderID, null);
			ArrayList<BoxTypedObject> folderEntries = boxFolder
					.getItemCollection().getEntries();
			int folderSize = folderEntries.size();
			for (int i = 0; i <= folderSize - 1; i++) {
				BoxTypedObject folderEntry = folderEntries.get(i);
				String name = (folderEntry instanceof BoxItem) ? ((BoxItem) folderEntry)
						.getName() : "(unknown)";
				if (name.equals(Filename)) {
					BoxDefaultRequestObject requestObj = new BoxDefaultRequestObject();
					String fileId = ((BoxItem) folderEntry).getId();
					client.getFilesManager().deleteFile(fileId, requestObj);
					return;
				}
			}
		} catch (BoxRestException | BoxServerException
				| AuthFatalFailureException e) {
			e.printStackTrace();
		}
		return;
	}

	private String getCloudVaultFolderID() {
		BoxFolder boxFolder;
		try {
			boxFolder = client.getFoldersManager().getFolder("0", null);
			ArrayList<BoxTypedObject> folderEntries = boxFolder
					.getItemCollection().getEntries();
			int folderSize = folderEntries.size();
			for (int i = 0; i <= folderSize - 1; i++) {
				BoxTypedObject folderEntry = folderEntries.get(i);
				String name = (folderEntry instanceof BoxItem) ? ((BoxItem) folderEntry)
						.getName() : "(unknown)";
				if (name.equals("CloudVault")) {
					// System.out.println("ID changed : " + CloudVaultFolderID);
					return folderEntry.getId();
				}
			}

			// create folder CloudVault

			BoxFolderRequestObject requestObj;

			try {
				requestObj = BoxFolderRequestObject.createFolderRequestObject(
						"CloudVault", "0");
				return client.getFoldersManager().createFolder(requestObj).getId();
			} catch (BoxRestException | BoxServerException
					| AuthFatalFailureException e) {
				e.printStackTrace();
			}

		} catch (BoxRestException | BoxServerException
				| AuthFatalFailureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;

	}

	private static BoxClient getAuthenticatedClient(String code, Proxy netproxy)
			throws BoxRestException, BoxServerException,
			AuthFatalFailureException {
		BoxResourceHub hub = new BoxResourceHub();
		BoxJSONParser parser = new BoxJSONParser(hub);
		IBoxRESTClient restClient = new BoxRESTClient() {
			@SuppressWarnings("deprecation")
			@Override
			public HttpClient getRawHttpClient() {
				// System.out.println("started");
				HttpClient client = super.getRawHttpClient();
				// System.out.println("client generated");
				InetSocketAddress addr = (InetSocketAddress) netproxy.address();
				HttpHost proxy = new HttpHost(addr.getHostString(),
						addr.getPort(), "http");
				client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY,
						proxy);

				return client;
			}
		};
		IBoxConfig config = (new BoxConfigBuilder()).build();
		BoxClient client = new BoxClient(key, secret, hub, parser, restClient,
				config);
		BoxOAuthToken bt = client.getOAuthManager().createOAuth(code, key,
				secret, "http://localhost:" + PORT);
		client.authenticate(bt);
		return client;
	}

	private static String getCode() throws IOException {

		@SuppressWarnings("resource")
		ServerSocket serverSocket = new ServerSocket(PORT);
		Socket socket = serverSocket.accept();
		BufferedReader in = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));
		while (true) {
			String code = "";
			try {
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
						socket.getOutputStream()));
				out.write("HTTP/1.1 200 OK\r\n");
				out.write("Content-Type: text/html\r\n");
				out.write("\r\n");

				code = in.readLine();
				//System.out.println(code);
				String match = "code";
				int loc = code.indexOf(match);

				if (loc > 0) {
					int httpstr = code.indexOf("HTTP") - 1;
					code = code.substring(code.indexOf(match), httpstr);
					String parts[] = code.split("=");
					code = parts[1];
					out.write("<html><h3>Now return to Cloud Vault Setup to Proceed</h3></html>.");
				} else {
					// It doesn't have a code
					out.write("<html><h3>Code not found in the URL</h3></html>!");
				}

				out.close();

				return code;
			} catch (IOException e) {
				// error ("System: " + "Connection to server lost!");
				System.exit(1);
				break;
			}
		}
		return "";
	}

	public String getID() {
		return ID;
	}

	public void setID(String iD) {
		ID = iD;
	}

}
