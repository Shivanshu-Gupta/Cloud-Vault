package cloudsafe;

import java.io.File;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cloudsafe.cloud.Cloud;
import cloudsafe.util.UserProxy;

public class UndoSetup {
	private final static Logger logger = LogManager.getLogger(UndoSetup.class
			.getName());
	public boolean deleteDirectory(String filePath, boolean recursive) {
	      File file = new File(filePath);
	      if (!file.exists()) {
	          return true;
	      }

	      if (!recursive || !file.isDirectory())
	          return file.delete();

	      String[] list = file.list();
	      for (int i = 0; i < list.length; i++) {
	          if (!deleteDirectory(filePath + File.separator + list[i], true))
	              return false;
	      }

	      return file.delete();
	  }
	
	public void clearPrefs() {
		try {
			Preferences vaultPrefs = Preferences.userNodeForPackage(Main.class);
			Preferences cloudConfigPrefs = Preferences.userNodeForPackage(Cloud.class);
			Preferences proxyConfigPrefs = Preferences.userNodeForPackage(UserProxy.class);
			vaultPrefs.clear();
			cloudConfigPrefs.clear();
			proxyConfigPrefs.clear();
		} catch (BackingStoreException e) {
			logger.error("Error clearing Preferences.", e);
			e.printStackTrace();
		}
	}
}