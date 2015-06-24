package cloudsafe;

import java.io.File;

public class UndoSetup {
	
	public boolean delete(String filePath, boolean recursive) {
	      File file = new File(filePath);
	      if (!file.exists()) {
	          return true;
	      }

	      if (!recursive || !file.isDirectory())
	          return file.delete();

	      String[] list = file.list();
	      for (int i = 0; i < list.length; i++) {
	          if (!delete(filePath + File.separator + list[i], true))
	              return false;
	      }

	      return file.delete();
	  }
}