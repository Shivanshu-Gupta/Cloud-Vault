package cloudsafe.util;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class UserProxy {
	public static final String REQUIREPROXY = "Require Proxy";
	public static final String PROXYHOST = "Proxy Host";
	public static final String PROXYPORT = "Proxy Port";
	public static final String PROXYUSER = "Proxy Username";
	public static final String PROXYPASS = "Proxy Password";
	
	public static Preferences proxyConfigPrefs = Preferences.userNodeForPackage(UserProxy.class);
	
	public static Proxy getProxy() {
		Proxy proxy = Proxy.NO_PROXY;
		if (proxyConfigPrefs.get(REQUIREPROXY, "no").equals("yes")) {
			String host = proxyConfigPrefs.get(PROXYHOST, "");
			int port = proxyConfigPrefs.getInt(PROXYPORT, 0);
			String authUser = proxyConfigPrefs.get(PROXYUSER, "");
			String authPass = proxyConfigPrefs.get(PROXYPASS, "");;
			if(!host.isEmpty() && port != 0) {
				Authenticator.setDefault(new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(authUser, authPass
								.toCharArray());
					}
				});
				proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host,
						port));
			}
		}	
		return proxy;
	}
	
	public static String reqProxy() {
		return proxyConfigPrefs.get(REQUIREPROXY, "no");
	}
	
	public static String host() {
		return proxyConfigPrefs.get(PROXYHOST, "");
	}
	
	public static int port() {
		return proxyConfigPrefs.getInt(PROXYPORT, 0);
	}
	
	public static String user() {
		return proxyConfigPrefs.get(PROXYUSER, "");
	}
	
	public static String pass() {
		return proxyConfigPrefs.get(PROXYPASS, "");
	}
	
	public static void saveProxy(String reqProxy, String host, int port, String user, String pass) throws BackingStoreException {
		proxyConfigPrefs.put(REQUIREPROXY, reqProxy);
		proxyConfigPrefs.put(PROXYHOST, host);
		proxyConfigPrefs.putInt(PROXYPORT, port);
		proxyConfigPrefs.put(PROXYUSER, user);
		proxyConfigPrefs.put(PROXYPASS, pass);
		proxyConfigPrefs.flush();
	}
}
