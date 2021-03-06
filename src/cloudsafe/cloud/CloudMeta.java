package cloudsafe.cloud;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created on 24-10-2015.
 */
public class CloudMeta {
    private int id;
    private String type;
    private ConcurrentHashMap<String, String> meta;

    public CloudMeta(int id, String type, ConcurrentHashMap<String,String> meta) {
        this.id = id;
        this.type = type;
        this.meta = meta;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ConcurrentHashMap<String, String> getMeta() {
        return meta;
    }

    public void setMeta(ConcurrentHashMap<String, String> meta) {
        this.meta = meta;
    }
    
    public String getName() {
    	if(type.equals(FolderCloud.NAME)){
    		return meta.get("path");
    	} else {
    		return meta.get("username");
    	}
    }

    public String getGenericName() {
        String genericName = "";
        switch (type) {
            case FolderCloud.NAME:
                genericName = type + " | " + meta.get("path");
                break;
            case Dropbox.NAME:
                genericName = type + " | " + meta.get("username");
                break;
            case GoogleDrive.NAME:
                genericName = type + " | " + meta.get("username");
                break;
            case Box.NAME:
            	genericName = type + " | " + meta.get("username");
            	break;
        }
        return genericName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CloudMeta other = (CloudMeta) obj;
        if (id != other.id)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Product [id=" + id + ", Cloud name=" + type + ", Meta=" + meta + "]";
    }

}
