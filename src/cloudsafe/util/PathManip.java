package cloudsafe.util;

public class PathManip {
	static char LocalChar = '/';
	static char CloudChar = '$';
	static String invalidChar[] = {"/","\\",":","*","?","<",">","|","\"","$"};

	public String toCloudFormat(String LocalFormat){
		return LocalFormat.replace(LocalChar, CloudChar);
	}
	
	public String toLocalFormat(String LocalFormat){
		return LocalFormat.replace(CloudChar, LocalChar);
	}
	
	public boolean isLocalFormat(String filename){
		for(int i=0; i<invalidChar.length; i++)
		{
			if(filename.contains(invalidChar[i]))
			{
				return false;
			}
		}
		return true;
	}
}
