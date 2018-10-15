import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;

public class BlockchainServer {

    public static void main(String[] args) {

        if (args.length != 3) {
            return;
        }

        int localPort = 0;
        int remotePort = 0;
        String remoteHost = null;

        try {
            localPort = Integer.parseInt(args[0]);
            remoteHost = args[1];
            remotePort = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            return;
        }

        if(remoteHost.equals("localhost")){
          remoteHost = "127.0.0.1";
        }

        Blockchain blockchain = new Blockchain();

        HashMap<ServerInfo, Date> serverStatus = new HashMap<ServerInfo, Date>();
        serverStatus.put(new ServerInfo(remoteHost, remotePort), new Date());
        
            Thread thread = new Thread(new CatchUpClientRunnable(new ServerInfo(remoteHost, remotePort), blockchain));
            thread.start();
            //thread.join();
        PeriodicCommitRunnable pcr = new PeriodicCommitRunnable(blockchain);
        Thread pct = new Thread(pcr);
        pct.start();

        Thread ppt = new Thread(new PeriodicPrinterRunnable(serverStatus));
        ppt.start();

        Thread paceMaker = new Thread(new PeriodicHeartBeatRunnable(serverStatus, localPort));
        paceMaker.start();

        Thread updater = new Thread(new PeriodicLatestBlockRunnable(serverStatus, localPort, blockchain));
        updater.start();

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(localPort);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new BlockchainServerRunnable(clientSocket, blockchain, serverStatus)).start();
            }
        } catch (IllegalArgumentException e) {
        } catch (IOException e) {
        } finally {
            try {
                pcr.setRunning(false);
                pct.join();
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
            } catch (InterruptedException e) {
            }
        }
    }
}
