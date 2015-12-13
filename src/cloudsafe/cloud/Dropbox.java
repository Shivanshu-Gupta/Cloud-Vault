package cloudsafe.cloud;

import com.dropbox.core.*;
import com.dropbox.core.http.HttpRequestor;
import com.dropbox.core.http.StandardHttpRequestor;

import java.awt.Desktop;
import java.io.*;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;







import java.util.concurrent.ConcurrentHashMap;




//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletRequestWrapper;
import javax.swing.JOptionPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.exceptions.AuthenticationException;

public class Dropbox implements Cloud {
	public static final String NAME = "DROPBOX";
	
	private final static Logger logger = LogManager
			.getLogger(Dropbox.class.getName());
	private final String APP_KEY = "jahcg9ypjnokceh";
	private final String APP_SECRET = "7tgx90ejlj65v12";

	DbxClient client;
	public static final String ACCESS_SECRET_NAME = "accesstoken";
	String accessToken = null;
	Boolean available = true;

	public Dropbox(Proxy proxy) throws AuthenticationException {
		DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);
		DbxRequestConfig config;
		HttpRequestor requ = new StandardHttpRequestor(proxy);
		config = new DbxRequestConfig("CloudVault/1.0", Locale.getDefault()
				.toString(), requ);

		DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);

		// Have the user sign in and authorize your app.
		String authorizeUrl = webAuth.start();
		try {
			if (Desktop.isDesktopSupported()) {
				// Windows
				try {
					Desktop.getDesktop().browse(new URI(authorizeUrl));
				} catch (URISyntaxException e) {
					logger.info("Excpetion: " + e);
					e.printStackTrace();
				}
			} else {
				// Ubuntu
				Runtime runtime = Runtime.getRuntime();
				runtime.exec("/usr/bin/firefox -new-window " + authorizeUrl);
			}
		} catch (IOException e) {
			throw new AuthenticationException("Authentication URL couldn't be opened.");
		}

		String code = JOptionPane.showInputDialog(null,
				"Copy the Authorization Code here",
				"Dropbox Authorization", JOptionPane.QUESTION_MESSAGE);
		if (code == null) {
			throw new AuthenticationException(
					"User cancelled Authentication");
		}
		code = code.trim();

		try {
			DbxAuthFinish authFinish = webAuth.finish(code);
			accessToken = authFinish.accessToken;
			client = new DbxClient(config, accessToken);

			logger.info("Linked account: "
					+ client.getAccountInfo().displayName);
		} catch (DbxException e) {
			throw new AuthenticationException(e);
		}
	}

	public Dropbox(String accessToken, Proxy proxy) throws DbxException {
		this.accessToken = accessToken;
		DbxRequestConfig config;
		HttpRequestor requ = new StandardHttpRequestor(proxy);
		config = new DbxRequestConfig("CloudVault/1.0", Locale.getDefault()
				.toString(), requ);
		try {
			client = new DbxClient(config, accessToken);
			logger.info("Linked account: "
					+ client.getAccountInfo().displayName);
		} catch (DbxException e) {
			logger.error("Error linking to Dropbox. ", e);
			throw e;
		}
	}

	public String metadata() {
		return accessToken;
	}

	@Override
	public boolean isAvailable() {
		if(!available){
			try{
				client.getAccountInfo();
				available = true;
			} catch (DbxException.RetryLater|DbxException.NetworkIO|DbxException.InvalidAccessToken e) {
				//TODO wrap these exceptions in a common 'CloudExcpetion' and throw.
//				throw e;
			} catch (DbxException e) {
				available = false;
			}
		}
		return available;
	}

	@Override
	public void uploadFile(byte[] data, String fileID, WriteMode mode)
			throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
		try {
			if (mode == WriteMode.ADD) {
				client.uploadFile("/CloudVault/" + fileID, DbxWriteMode.add(),
						data.length, inputStream);
			} else if (mode == WriteMode.OVERWRITE) {
				client.uploadFile("/CloudVault/" + fileID,
						DbxWriteMode.force(), data.length, inputStream);
			}
		} catch (DbxException.RetryLater|DbxException.NetworkIO|DbxException.InvalidAccessToken e) {
			//TODO wrap these exceptions in a common 'CloudExcpetion' and throw.
//			throw e;
		} catch (DbxException e) {
			available = false;
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
				client.uploadFile("/CloudVault/" + fileID, DbxWriteMode.add(),
						inputFile.length(), inputStream);
			} else if (mode == WriteMode.OVERWRITE) {
				client.uploadFile("/CloudVault/" + fileID,
						DbxWriteMode.force(), inputFile.length(), inputStream);
			}
		} catch (DbxException.RetryLater|DbxException.NetworkIO|DbxException.InvalidAccessToken e) {
			//TODO wrap these exceptions in a common 'CloudExcpetion' and throw.
//			throw e;
		} catch (DbxException e) {
			available = false;
		} finally {
			inputStream.close();
		}
	}

	@Override
	public byte[] downloadFile(String fileID) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			client.getFile("/CloudVault/" + fileID, null, outputStream);
		} catch (DbxException.RetryLater|DbxException.NetworkIO|DbxException.InvalidAccessToken e) {
			//TODO wrap these exceptions in a common 'CloudExcpetion' and throw.
//			throw e;
		} catch (DbxException e) {
			available = false;
		} finally {
			outputStream.close();
		}
		return outputStream.toByteArray();
	}

	@Override
	public void downloadFile(String filename, String fileID) throws IOException {
		FileOutputStream outputStream = new FileOutputStream(filename);
		try {
			client.getFile("/CloudVault/" + fileID, null, outputStream);
		} catch (DbxException.RetryLater|DbxException.NetworkIO|DbxException.InvalidAccessToken e) {
			//TODO wrap these exceptions in a common 'CloudExcpetion' and throw.
//			throw e;
		} catch (DbxException e) {
			available = false;
		} finally {
			outputStream.close();
		}
	}

	@Override
	public boolean searchFile(String fileID) {
		try {
			List<DbxEntry> matchList = client.searchFileAndFolderNames(
					"/CloudVault", fileID);
			if (matchList == null || matchList.size() == 0)
				return false;
			else
				return true;
		} catch (DbxException.RetryLater|DbxException.NetworkIO|DbxException.InvalidAccessToken e) {
			//TODO wrap these exceptions in a common 'CloudExcpetion' and throw.
//			throw e;
		} catch (DbxException e) {
			available = false;
		}
		return false;
	}

	@Override
	public void deleteFile(String path) {
		try {
			client.delete("/CloudVault/" + path);
		} catch (DbxException.RetryLater|DbxException.NetworkIO|DbxException.InvalidAccessToken e) {
			//TODO wrap these exceptions in a common 'CloudExcpetion' and throw.
//			throw e;
		} catch (DbxException e) {
			available = false;
		}
	}
	
	public ConcurrentHashMap<String, String> getUserInfo() {
		ConcurrentHashMap<String, String> meta = new ConcurrentHashMap<String, String>();
		try {
			DbxAccountInfo account = client.getAccountInfo();
			meta.put("uid", String.valueOf(account.userId));
			meta.put("username", account.displayName);
			//email id not available in desktop api.
		} catch (DbxException e) {
			e.printStackTrace();
		}
		return meta;
	}
	
	public ConcurrentHashMap<String, String> getMetaData() {
		ConcurrentHashMap<String, String> meta = new ConcurrentHashMap<>();
		meta.put(ACCESS_SECRET_NAME, accessToken);
		meta.putAll(getUserInfo());
		return meta;
	}
}
