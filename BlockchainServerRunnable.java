import java.io.*;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Arrays;

public class BlockchainServerRunnable implements Runnable{

    private Socket clientSocket;
    private Blockchain blockchain;
    private HashMap<ServerInfo, Date> serverStatus;

    public BlockchainServerRunnable(Socket clientSocket, Blockchain blockchain, HashMap<ServerInfo, Date> serverStatus) {
        this.clientSocket = clientSocket;
        this.blockchain = blockchain;
        this.serverStatus = serverStatus;
    }

    public void run() {
        try {
            serverHandler(clientSocket.getInputStream(), clientSocket.getOutputStream());
            clientSocket.close();
        } catch (IOException e) {
        }
    }

    public void serverHandler(InputStream clientInputStream, OutputStream clientOutputStream) {

        BufferedReader inputReader = new BufferedReader(new InputStreamReader(clientInputStream));
        PrintWriter outWriter = new PrintWriter(clientOutputStream, true);

        int localPort = clientSocket.getLocalPort();
        int remotePort = ((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getPort();
        String localIP = (((InetSocketAddress) clientSocket.getLocalSocketAddress()).getAddress()).toString().replace("/", "");
        String remoteIP = (((InetSocketAddress) clientSocket.getRemoteSocketAddress()).getAddress()).toString().replace("/", "");

        try {
            while (true) {
                String inputLine = inputReader.readLine();
                if (inputLine == null) break;

                String[] tokens = inputLine.split("\\|");
                switch (tokens[0]) {
                    case "tx":
                        if (blockchain.addTransaction(inputLine))
                            outWriter.print("Accepted\n\n");
                        else
                            outWriter.print("Rejected\n\n");
                        outWriter.flush();
                        break;
                    case "pb":
                        outWriter.print(blockchain.toString() + "\n");
                        outWriter.flush();
                        break;
                    case "cc":
                        return;
                    case "hb":
                        ServerInfo callingServerhb = new ServerInfo(remoteIP, Integer.parseInt(tokens[1]));
                        boolean newServerhb = true;

                        clearOldEntries();

                        Iterator it2hb = serverStatus.entrySet().iterator();
                        while (it2hb.hasNext()) {
                            HashMap.Entry entry = (HashMap.Entry)it2hb.next();
                            if(entry.getKey().equals(callingServerhb)){
                                it2hb.remove();
                                serverStatus.put(callingServerhb, new Date());
                                newServerhb = false;
                                break;
                            }
                        }

                        if(newServerhb){
                            ArrayList<Thread> threadArrayList = new ArrayList<>();
                            for(ServerInfo server : serverStatus.keySet()){
                                if (server.getPort() == localPort) continue;
                                Thread thread = new Thread(new HeartBeatClientRunnable(server, "si|" + localPort + "|" + remoteIP + "|" + Integer.parseInt(tokens[1])));
                                threadArrayList.add(thread);
                                thread.start();
                            }
                            for (Thread thread : threadArrayList) {
                                try {
                                    thread.join();
                                } catch (InterruptedException e) {}
                            }
                            serverStatus.put(callingServerhb, new Date());
                        }
                        break;
                    case "si":
                        if(tokens[2].equals("localhost")){
                            tokens[2] = "127.0.0.1";
                        }
                        ServerInfo callingServersi = new ServerInfo(remoteIP, Integer.parseInt(tokens[1]));
                        ServerInfo addedServersi = new ServerInfo(tokens[2], Integer.parseInt(tokens[3]));
                        boolean newServersi = true;

                        if(addedServersi.getPort() == localPort) break;

                        clearOldEntries();

                        Iterator it2si = serverStatus.entrySet().iterator();
                        while (it2si.hasNext()) {
                            HashMap.Entry entry = (HashMap.Entry)it2si.next();
                            if(entry.getKey().equals(addedServersi)){
                                newServersi = false;
                                break;
                            }
                        }

                        if(newServersi){
                            ArrayList<Thread> threadArrayList = new ArrayList<>();
                            for(ServerInfo server : serverStatus.keySet()){
                                if(server.equals(callingServersi) || server.getPort() == localPort) continue;
                                Thread thread = new Thread(new HeartBeatClientRunnable(server, "si|" + localPort + "|" + tokens[2] + "|" + tokens[3]));
                                threadArrayList.add(thread);
                                thread.start();
                            }
                            for (Thread thread : threadArrayList) {
                                try {
                                    thread.join();
                                } catch (InterruptedException e) {}
                            }
                            serverStatus.put(addedServersi, new Date());
                        }
                        break;
                    case "lb":
                        int localChainLength = this.blockchain.getLength();
                        int remoteChainLength = Integer.parseInt(tokens[2]);
                        byte[] remoteChainHash = Base64.getDecoder().decode(tokens[3]);

                        if(remoteChainLength > localChainLength || (remoteChainLength == localChainLength && compareHash(remoteChainHash, blockchain.getHead().calculateHash()) < 0)) {
                            try{
                                Thread thread = new Thread(new CatchUpClientRunnable(new ServerInfo(remoteIP, Integer.parseInt(tokens[1])), blockchain));
                                thread.start();
                                thread.join();
                            }
                            catch(InterruptedException e){
                            }
                        }
                        break;
                    case "cu":
                        ObjectOutputStream objWriter = new ObjectOutputStream(clientOutputStream);
                        if(tokens.length == 1){
                            if(blockchain.getHead() != null){
                                objWriter.writeObject(blockchain.getHead());
                                objWriter.flush();
                            }
                        }
                        else{
                            if(blockchain.getHead() != null){
                                byte[] remoteBlockHash = Base64.getDecoder().decode(tokens[1]);
                                Block requestedBlock = blockchain.getHead();
                                while(compareHash(requestedBlock.calculateHash(), remoteBlockHash) != 0){
                                    requestedBlock = requestedBlock.getPreviousBlock();
                                    if(requestedBlock == null) break;
                                }
                                if(requestedBlock != null){
                                    objWriter.writeObject(requestedBlock);
                                    objWriter.flush();
                                }
                            }
                        }
                        break;
                    default:
                        outWriter.print("Error\n\n");
                        outWriter.flush();
                }
            }
        } catch (IOException e) {
        } //catch (InterruptedException e) {}
    }

    //returns true if the first byte array is smaller, false otherwise
    public int compareHash(byte[] first, byte[] second){
        if(first.length != second.length){
            return 2;
        }
        else{
            for(int i = 0; i < first.length; i++){
                if(first[i] > second[i]){
                    return 1;
                }
                if(first[i] < second[i]){
                    return -1;
                }
            }
            return 0;
        }
    }

    public void clearOldEntries(){
      Iterator it = serverStatus.entrySet().iterator();

      while (it.hasNext()) {
          HashMap.Entry entry = (HashMap.Entry)it.next();
          if (new Date().getTime() - ((Date) entry.getValue()).getTime() > 6000) {
              it.remove();
          }
      }
    }
}
