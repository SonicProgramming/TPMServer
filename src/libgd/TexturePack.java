package libgd;

import java.io.Serializable;
import java.net.URL;

/**
 * @author Sonic
 */
public class TexturePack implements Serializable {

    static final long serialVersionUID = 900900900L;
    
    //Information about TPs
    //Is also referred to as "TP meta" in the comments
    //Don't occasionally think that only meta string is mentioned
    private String name = "";
    private String author = "";
    private String meta = "";
    private long id = -1;
    private float gameVersion = 0;
    private URL downloadURL = null;
    private double packSize = 0; //Unnecessary, 0 by default
    
    public TexturePack(String pname, String auth, String metadata, long ID, float ver, URL durl){
        name = pname;
        author = auth;
        meta = metadata;
        id = ID;
        gameVersion = ver;
        downloadURL = durl;
    }
    
    public TexturePack(String pname, String auth, String metadata, long ID, float ver, URL durl , double size){
        name = pname;
        author = auth;
        meta = metadata;
        id = ID;
        gameVersion = ver;
        downloadURL = durl;
        packSize = size;
    }
    
    public String getName(){
        return name;
    }
    
    public String getAuthor(){
        return author;
    }
    
    public String getMeta(){
        return meta;
    }
    
    public long getID(){
        return id;
    }
    
    public float getGameVersion(){
        return gameVersion;
    }
    
    public URL getURL(){
        return downloadURL;
    }
    
    public double getPackageSize(){
        return packSize;
    }
    
}
