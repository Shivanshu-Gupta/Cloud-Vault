package cloudsafe;

import com.dropbox.core.*;
import com.dropbox.core.http.HttpRequestor;
import com.dropbox.core.http.StandardHttpRequestor;

import java.awt.Desktop;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletRequestWrapper;
import javax.swing.JOptionPane;

import cloudsafe.cloud.Cloud;
import cloudsafe.cloud.WriteMode;
import cloudsafe.exceptions.AuthenticationException;;
public class Dropbox implements Cloud {

	DbxClient client;
	String accessToken = null;
	Boolean available = true;

	public Dropbox() throws AuthenticationException {
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

		// final String authUser = "bb5130008";
		// final String authPassword = "navij_tnayaj";

		// Authenticator.setDefault(new Authenticator() {
		// @Override
		// protected PasswordAuthentication getPasswordAuthentication() {
		// return new PasswordAuthentication(authUser, authPassword
		// .toCharArray());
		// }
		// });

		Proxy proxy = new Proxy(Proxy.Type.HTTP,
				new InetSocketAddress(ip, port));

		HttpRequestor req = new StandardHttpRequestor(proxy);
		return req;
		// }
		// return null;
	}

	private void setupNewAccess() throws AuthenticationException {
		final String APP_KEY = "jahcg9ypjnokceh";
		final String APP_SECRET = "7tgx90ejlj65v12";

		DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);

		DbxRequestConfig config;
		HttpRequestor requ = getProxy();
		if (requ != null)
			config = new DbxRequestConfig("CloudVault/1.0", Locale.getDefault()
					.toString(), requ);
		else
			config = new DbxRequestConfig("CloudVault/1.0", Locale.getDefault()
					.toString());

//		HttpServletRequest request = new HttpServletRequestWrapper(null);
//		javax.servlet.http.HttpSession session = request.getSession(true);
//		String sessionKey = "dropbox-auth-csrf-token";
//		DbxSessionStore csrfTokenStore = new DbxStandardSessionStore(session,
//				sessionKey);
//
//		String redirectUri = "http://my-server.com/dropbox-auth-finish";
		
		DbxWebAuthNoRedirect webAuth = new DbxWebAuthNoRedirect(config, appInfo);

		// Have the user sign in and authorize your app.
		String authorizeUrl = webAuth.start();
		try {
			if (Desktop.isDesktopSupported()) {
				// Windows
				try {
					Desktop.getDesktop().browse(new URI(authorizeUrl));
				} catch (URISyntaxException e) {
					System.out.println("Excpetion: " + e);
					e.printStackTrace();
				}
			} else {
				// Ubuntu
				Runtime runtime = Runtime.getRuntime();
				runtime.exec("/usr/bin/firefox -new-window " + authorizeUrl);
			}

//			System.out.println("Copy the authorization code.");
			
		    String code = JOptionPane.showInputDialog(null,
	                "Copy the Authorization Code here",
	                "Dropbox Authorization",
	                JOptionPane.QUESTION_MESSAGE);
		    if(code==null)
		    {
		    	throw new AuthenticationException("User cancelled Authentication");
		    }
//			String code = new BufferedReader(new InputStreamReader(System.in))
//					.readLine().trim();
			// This will fail if the user enters an invalid authorization code.
			DbxAuthFinish authFinish = webAuth.finish(code);
			accessToken = authFinish.accessToken;

			client = new DbxClient(config, accessToken);

			System.out.println("Linked account: "
					+ client.getAccountInfo().displayName);
		} catch (DbxException.NetworkIO | DbxException.ServerError
				| DbxException.ProtocolError dbe) {
			available = false;
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
			config = new DbxRequestConfig("CloudSafe/1.0", Locale.getDefault()
					.toString(), requ);
		else
			config = new DbxRequestConfig("CloudSafe/1.0", Locale.getDefault()
					.toString());
		try {

			client = new DbxClient(config, accessToken);

			System.out.println("Linked account: "
					+ client.getAccountInfo().displayName);
		} catch (DbxException.NetworkIO | DbxException.ServerError
				| DbxException.ProtocolError | DbxException.InvalidAccessToken dbe) {
			available = false;
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
		} catch (DbxException.NetworkIO | DbxException.ServerError
				| DbxException.ProtocolError dbe) {
			available = false;
		} catch (DbxException dbe) {
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
		} catch (DbxException.NetworkIO | DbxException.ServerError
				| DbxException.ProtocolError dbe) {
			available = false;
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
			client.getFile("/CloudSafe/" + fileID, null, outputStream);
		} catch (DbxException.NetworkIO | DbxException.ServerError
				| DbxException.ProtocolError dbe) {
			available = false;
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
			client.getFile("/CloudSafe/" + fileID, null, outputStream);
		} catch (DbxException.NetworkIO | DbxException.ServerError
				| DbxException.ProtocolError dbe) {
			available = false;
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
			List<DbxEntry> matchList = client.searchFileAndFolderNames(
					"/CloudSafe", fileID);
			if (matchList == null || matchList.size() == 0)
				return false;
			else
				return true;
		} catch (DbxException.NetworkIO | DbxException.ServerError
				| DbxException.ProtocolError dbe) {
			available = false;
		} catch (DbxException dbe) {
			System.out.println("DbxException: " + dbe);
			dbe.printStackTrace();
		}
		return false;
	}
}
