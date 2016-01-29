package cloudsafe.util;

public class PathManip {
	private char slash = '/';
	private char backSlash = '\\';
	private char dollar = '$';
	private String invalidChar[] = {"/","\\",":","*","?","<",">","|","\"","$"};
	private String str;
	
	public PathManip(String str){
		this.str = str; 
	}
	
	public String toCloudFormat(){
		return str.replace(slash, dollar).replace(backSlash, dollar);
	}
	
	public String toLocalFormat(){
		return str.replace(dollar, slash);
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
