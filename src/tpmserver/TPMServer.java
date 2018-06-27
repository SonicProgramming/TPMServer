package tpmserver;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import libgd.TexturePack;

/**
 * @author Sonic
 */
public class TPMServer {

    static long id = 0;
    
    //Tried to make these more concurrent than before
    static Map<String, Long> byName = new ConcurrentHashMap<>();
    static Map<Long, TexturePack> byIDs = new ConcurrentHashMap<>();
    static List<Long> ids = new CopyOnWriteArrayList<>();
    static String updateMessage = "";
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        //Fill up all the maps the server uses
        File f = new File(System.getProperty("user.dir") + "/files/");
        System.out.println("[General]: Getting cached TPs");
        for(File file : f.listFiles()) {
            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
                TexturePack current = (TexturePack) ois.readObject();
                System.out.println("[Cache]: Addedd <"+current.getName()+", "+current.getID()+">");
                byName.put(current.getName(), current.getID());
                byIDs.put(current.getID(), current);
                ids.add(current.getID());
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(TPMServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        //Read update message
        try {
            BufferedReader br = new BufferedReader(new FileReader(System.getProperty("user.dir") + "/update.txt"));
            updateMessage = br.readLine();
            br.close();
        } catch (IOException ex) {
            Logger.getLogger(TPMServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        //Start it
        Server serv = new Server();
        Thread thr = new Thread(serv);
        thr.start();
    }
    
}

class Server implements Runnable {
    ExecutorService pool;
    ServerSocket servsock;    
    
    
    @Override
    public void run(){
        pool = Executors.newCachedThreadPool(new LoggingThreadFactory());
        System.out.println("[General]: Created thread pool");
        try {
        System.out.println("[General]: Now ready");
            deploy();
            
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void deploy() throws IOException{
        servsock = new ServerSocket(65531);       
        ClientHandler handler = new ClientHandler();     
        while(true) {
            Socket childsock = servsock.accept();
            System.out.println("[Network]: Client connected from " + childsock.getInetAddress().getHostAddress());
            pool.submit(() -> handler.onClient(childsock));
        }
    }
    
    class ClientHandler {
        public void onClient(Socket sock) {
            try {
                java.io.InputStream is = sock.getInputStream();
                java.io.OutputStream os = sock.getOutputStream();
                DataInputStream dis = new DataInputStream(is);
                DataOutputStream dos = new DataOutputStream(os); //For sending simple responses
                ObjectOutputStream oos = new ObjectOutputStream(os); //For sending complex responses
                //ObjectInputStream ois = new ObjectInputStream(is); //For code 600 //UPD: not working, throws StreamCorruptedException somewhy
                int code = dis.readInt();
                System.out.println("[Network]: " + sock.getInetAddress().getHostAddress() + " requested code " + code);
                switch(code){
                    case 100: run100(dis, oos, dos);
                    break;
                    case 200: run200(dis, dos);
                    break;
                    case 300: run300(oos);
                    break;
                    case 400: run400(dos);
                    break;
                    case 500: run500(oos, dos);
                    break;
                    case 600: run600(dos, dis);
                    break;
                    case 1000: run1000();
                    break;
                }
                //Get rid of the connection
                System.out.println("[Network]: Dropping " + sock.getInetAddress().getHostAddress() + " as we are done with him");
                dis.close();
                dos.close();
                oos.close();
                sock.close();
            } catch (IOException | ClassNotFoundException ex) { //Let this big catch be here
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        //Code 1000, check connection
        private void run1000(){
            //Nothing here
        }
        
        //Code 100, request TP meta by ID
        private void run100(DataInputStream dis, ObjectOutputStream oos, DataOutputStream dos) throws IOException, ClassNotFoundException{
            long id = dis.readLong();
            if(id == 0) {
                oos.writeObject(null);
                oos.flush();
                dos.writeInt(-1);
                dos.flush();
            }
            else {
                oos.writeObject(TPMServer.byIDs.get(id));
                oos.flush();
                dos.writeInt(0);
                dos.flush();
            }
            
        }
        
        //Code 200, request TP ID by name
        private void run200(DataInputStream dis, DataOutputStream dos) throws IOException{
            String namePiece = dis.readUTF();
            TPMServer.id = 0;
            TPMServer.byName.forEach((String key, Long value) -> {        
                long toreturn = key.contains(namePiece) ? value : 0;
                if(toreturn != 0) TPMServer.id = toreturn;                
            });
            dos.writeLong(TPMServer.id);
            dos.flush();           
            dos.writeInt(0);
            dos.flush();
        }
        
        //Code 300, get online TPs
        private void run300(ObjectOutputStream oos) throws IOException{
            oos.writeObject(TPMServer.byName);
            oos.flush();
        }
        
        //Code 400, get update message
        private void run400(DataOutputStream dos) throws IOException{
            dos.writeUTF(TPMServer.updateMessage);
            dos.flush();
            dos.writeInt(0);
            dos.flush();
        }
        
        //Code 500, get used IDs
        private void run500(ObjectOutputStream oos, DataOutputStream dos) throws IOException{
            oos.writeObject(TPMServer.ids);
            oos.flush();
            dos.writeInt(0);
            dos.flush();
        }
        
        //Code 600, upload new TP
        //This is a very bad piece of code, i definetly need to make it better once i figure out why my ObjectOutputStream fails
        private void run600(DataOutputStream dos, DataInputStream dis) throws IOException, ClassNotFoundException{
           TexturePack received = null;
           
           String name = dis.readUTF();
           String author = dis.readUTF();
           String meta = dis.readUTF();
           long id = dis.readLong();
           float gameVer = dis.readFloat();
           String URL = dis.readUTF();
           double pSize = dis.readDouble();
           
           dos.writeInt(0);
           dos.flush();
           
           received = new TexturePack(name, author, meta, id, gameVer, new java.net.URL(URL), pSize);
           
           TPMServer.byName.put(received.getName(), received.getID());
           TPMServer.byIDs.put(received.getID(), received);
           TPMServer.ids.add(received.getID());
           
           ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(System.getProperty("user.dir")+"/files/"+received.getID()+".gdtp"));
           oos.writeObject(received);
           oos.flush();
           oos.close();
           
        }
        
    }
    
}

//Thread factory needed by CachedThreadPool
class LoggingThreadFactory implements ThreadFactory
{

    @Override
    public Thread newThread(Runnable r)
    {
        Thread t = new Thread(r);

        t.setUncaughtExceptionHandler((Thread t1, Throwable ex) -> {
            Logger.getLogger(t1.getName()).log(Level.SEVERE, null, ex);
        });

        return t;
    }
}
