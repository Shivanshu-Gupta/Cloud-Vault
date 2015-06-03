package cloudsafe;

import com.dropbox.core.*;
import com.dropbox.core.http.HttpRequestor;
import com.dropbox.core.http.StandardHttpRequestor;

import java.io.*;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.List;
import java.util.Locale;

import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.WriteMode;

public class Dropbox implements Cloud {

	DbxClient client;
	String accessToken = null;
	Boolean available = true;

	public Dropbox() {
		setupNewAccess();
	}

	public Dropbox(String accessToken) {
		// System.out.println("Access Token: " + accessToken);
		this.accessToken = accessToken;
		setupAccess();
	}

	private static HttpRequestor getProxy() {

		// if ("true".equals(System.getProperty("proxy", "false"))) {
		String ip = "proxy61.iitd.ernet.in";
		int port = 3128;

		final String authUser = "bb5130008";
		final String authPassword = "navij_tnayaj";

		Authenticator.setDefault(new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(authUser, authPassword
						.toCharArray());
			}
		});

		Proxy proxy = new Proxy(Proxy.Type.HTTP,
				new InetSocketAddress(ip, port));

		HttpRequestor req = new StandardHttpRequestor(proxy);
		return req;
		// }
		// return null;
	}

	private void setupNewAccess() {
		final String APP_KEY = "lxu7s0kd7tse3ee";
		final String APP_SECRET = "8e2yj26uvjor3w0";

		DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);

		DbxRequestConfig config;
		HttpRequestor requ = getProxy();
		if (requ != null)
			config = new DbxRequestConfig("CloudSafe/1.0", Locale.getDefault()
					.toString(), requ);
		else
			config = new DbxRequestConfig("CloudSafe/1.0", Locale.getDefault()
					.toString());

		DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);

		// Have the user sign in and authorize your app.
		String authorizeUrl = webAuth.start();
		System.out.println("1. Go to: " + authorizeUrl);
		System.out
				.println("2. Click \"Allow\" (you might have to log in first)");
		System.out.println("3. Copy the authorization code.");

		try {
			String code = new BufferedReader(new InputStreamReader(System.in))
					.readLine().trim();
			// This will fail if the user enters an invalid authorization code.
			DbxAuthFinish authFinish = webAuth.finish(code);
			accessToken = authFinish.accessToken;

			// // store the code for future use.
			// Path path = Paths.get("DropboxAccessToken.txt");
			// byte[] buf = accessToken.getBytes();
			// Files.write(path, buf, WRITE, TRUNCATE_EXISTING, CREATE);

			client = new DbxClient(config, accessToken);

			System.out.println("Linked account: "
					+ client.getAccountInfo().displayName);
		} catch (DbxException.NetworkIO|DbxException.ServerError|DbxException.ProtocolError dbe) {
			available = false;
		} catch (DbxException dbe){
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
			config = new DbxRequestConfig("CloudSafe/1.0", Locale.getDefault()
					.toString(), requ);
		else
			config = new DbxRequestConfig("CloudSafe/1.0", Locale.getDefault()
					.toString());
		try {

			client = new DbxClient(config, accessToken);

			System.out.println("Linked account: "
					+ client.getAccountInfo().displayName);
		} catch (DbxException.NetworkIO|DbxException.ServerError|DbxException.ProtocolError dbe) {
			available = false;
		} catch (DbxException.InvalidAccessToken dbe){
			setupNewAccess();
		} catch (DbxException dbe){
			System.out.println("DbxException: " + dbe);
			dbe.printStackTrace();
		}
		
	}

	public String metadata() {
		return accessToken;
	}

	@Override
	public boolean isAvailable() {
		return available;
	}

	@Override
	public void uploadFile(byte[] data, String fileID, WriteMode mode)
			throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
		try {
			if (mode == WriteMode.ADD) {
				client.uploadFile("/CloudSafe/" + fileID, DbxWriteMode.add(),
						data.length, inputStream);
			} else if (mode == WriteMode.OVERWRITE) {
				client.uploadFile("/CloudSafe/" + fileID, DbxWriteMode.force(),
						data.length, inputStream);
			}
		} catch (DbxException.NetworkIO|DbxException.ServerError|DbxException.ProtocolError dbe) {
			available = false;
		} catch (DbxException.InvalidAccessToken dbe){
			setupNewAccess();
		} catch (DbxException dbe){
			System.out.println("DbxException: " + dbe);
			dbe.printStackTrace();
		} finally {
			inputStream.close();
		}

	}

	@Override
	public void uploadFile(String filename, String fileID, WriteMode mode)
			throws IOException {
		File inputFile = new File(filename);
		FileInputStream inputStream = new FileInputStream(inputFile);
		try {
			if (mode == WriteMode.ADD) {
				client.uploadFile("/CloudSafe/" + fileID, DbxWriteMode.add(),
						inputFile.length(), inputStream);
			} else if (mode == WriteMode.OVERWRITE) {
				client.uploadFile("/CloudSafe/" + fileID, DbxWriteMode.force(),
						inputFile.length(), inputStream);
			}
		} catch (DbxException.NetworkIO|DbxException.ServerError|DbxException.ProtocolError dbe) {
			available = false;
		} catch (DbxException.InvalidAccessToken dbe){
			setupNewAccess();
		} catch (DbxException dbe){
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
			client.getFile("/CloudSafe/" + fileID, null, outputStream);
		} catch (DbxException.NetworkIO|DbxException.ServerError|DbxException.ProtocolError dbe) {
			available = false;
		} catch (DbxException.InvalidAccessToken dbe){
			setupNewAccess();
		} catch (DbxException dbe){
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
			client.getFile("/CloudSafe/" + fileID, null, outputStream);
		} catch (DbxException.NetworkIO|DbxException.ServerError|DbxException.ProtocolError dbe) {
			available = false;
		} catch (DbxException.InvalidAccessToken dbe){
			setupNewAccess();
		} catch (DbxException dbe){
			System.out.println("DbxException: " + dbe);
			dbe.printStackTrace();
		} finally {
			outputStream.close();
		}
	}

	@Override
	public boolean searchFile(String fileID) {
		try {
			List<DbxEntry> matchList = client.searchFileAndFolderNames(
					"/CloudSafe", fileID);
			if (matchList == null || matchList.size() == 0)
				return false;
			else
				return true;
		} catch (DbxException.NetworkIO|DbxException.ServerError|DbxException.ProtocolError dbe) {
			available = false;
		} catch (DbxException.InvalidAccessToken dbe){
			setupNewAccess();
		} catch (DbxException dbe){
			System.out.println("DbxException: " + dbe);
			dbe.printStackTrace();
		} 
		return false;
	}
}
