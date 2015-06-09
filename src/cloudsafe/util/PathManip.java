package cloudsafe.util;

public class PathManip {
	static char LocalChar = '/';
	static char CloudChar = '$';
	static String invalidChar[] = {"/","\\",":","*","?","<",">","|","\"","$"};
	static String str;
	
	public PathManip(String str){
		this.str = str; 
	}
	
	public String toCloudFormat(){
		return str.replace(LocalChar, CloudChar);
	}
	
	public String toLocalFormat(){
		return str.replace(CloudChar, LocalChar);
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
