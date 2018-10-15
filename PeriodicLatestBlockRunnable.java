import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Base64;
import java.util.Arrays;

public class PeriodicLatestBlockRunnable implements Runnable {

    private HashMap<ServerInfo, Date> serverStatus;
    private int localPort;
    private Blockchain blockchain;

    public PeriodicLatestBlockRunnable(HashMap<ServerInfo, Date> serverStatus, int localPort, Blockchain blockchain) {
        this.serverStatus = serverStatus;
        this.localPort = localPort;
        this.blockchain = blockchain;
    }

    @Override
    public void run() {
        while(true) {
            // broadcast HeartBeat message to all peers
            ArrayList<Thread> threadArrayList = new ArrayList<>();

            int numServers = serverStatus.size();
            int temp;

            if(blockchain.getHead() == null || blockchain.getLength() == 0){
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e){}
                continue;
            }

            //if(numServers > 5){
            if(false){
                int[] selected = new int[5];
                for(int i = 0; i < 5; i++){
                    temp = (int) (Math.random() * numServers);
                    for(int j = 0; j < i; j++){
                        if(temp == selected[j]){
                            temp = (int) (Math.random() * numServers);
                            j = -1;
                        }
                    }
                    selected[i] = temp;
                }

                int count = -1;
                for (ServerInfo server : serverStatus.keySet()) {
                    count++;
                    if(!Arrays.asList(selected).contains(count)) continue;
                    Thread thread = new Thread(new HeartBeatClientRunnable(server, "lb|" + localPort + "|" + blockchain.getLength() + "|" + Base64.getEncoder().encodeToString(blockchain.getHead().calculateHash())));
                    //System.out.print("lb|" + localPort + "|" + blockchain.getLength() + "|" + Base64.getDecoder().decode(blockchain.getHead().calculateHash()));
                    threadArrayList.add(thread);
                    thread.start();
                }

                for (Thread thread : threadArrayList) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                    }
                }
            }
            else{
                for (ServerInfo server : serverStatus.keySet()) {
                    Thread thread = new Thread(new HeartBeatClientRunnable(server, "lb|" + localPort + "|" + blockchain.getLength() + "|" + Base64.getEncoder().encodeToString(blockchain.getHead().calculateHash())));
                    //System.out.print("lb|" + localPort + "|" + blockchain.getLength() + "|" + Base64.getDecoder().decode(blockchain.getHead().calculateHash()));
                    threadArrayList.add(thread);
                    thread.start();
                }

                for (Thread thread : threadArrayList) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                    }
                }
            }

            // sleep for two seconds
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {}
        }
    }
}
