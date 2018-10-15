import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Base64;
import java.util.ArrayList;

public class CatchUpClientRunnable implements Runnable{

    private ServerInfo server;
    private int localPort;
    private Blockchain blockchain;

    public CatchUpClientRunnable(ServerInfo server, int localPort, Blockchain blockchain) {
        this.server = server;
        this.localPort = localPort;
        this.blockchain = blockchain;
    }

    @Override
    public void run() {
        try {
            // create socket with a timeout of 2 seconds
            Socket toServer = new Socket();
            toServer.connect(new InetSocketAddress(server.getHost(), server.getPort()), 2000);
            PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(toServer.getInputStream()));
            ObjectInputStream objReader = new ObjectInputStream(toServer.getInputStream());

            printWriter.print("cu");
            printWriter.flush();
            ArrayList<Block> blocks = new ArrayList<Block>();
            Transaction tx;
            Block matchingBlock = null;

            while (true) {
                //Thread.sleep(50);
                Block inputBlock = (Block) objReader.readObject();
                if (inputBlock == null) {
                    break;
                }

                blocks.add(inputBlock);

                byte[] previousBlockHash = inputBlock.getPreviousHash();

                if(compareHash(previousBlockHash, new byte[32]) == 0) break;

                matchingBlock = blockchain.getHead();
                if(matchingBlock != null){
                    while(compareHash(matchingBlock.calculateHash(), previousBlockHash) != 0){
                        matchingBlock = matchingBlock.getPreviousBlock();
                        if(matchingBlock == null) break;
                    }
                    if(matchingBlock != null){
                        break;
                    }
                }
                printWriter.print("cu|" + Base64.getEncoder().encodeToString(inputBlock.getPreviousHash()));
                printWriter.flush();
            }

            printWriter.print("cc");
            printWriter.flush();

            if(matchingBlock != null){
                blocks.get(blocks.size() - 1).setPreviousBlock(matchingBlock);
                blocks.get(blocks.size() - 1).setPreviousHash(matchingBlock.calculateHash());
            }
            else{
                blocks.get(blocks.size() - 1).setPreviousHash(new byte[32]);
            }

            Block temp = blocks.get(0);
            int count = 1;
            while(compareHash(temp.getPreviousHash(), new byte[32]) != 0){
                temp = temp.getPreviousBlock();
                count++;
            }

            blockchain.setLength(count);
            blockchain.setHead(blocks.get(0));

            // close printWriter and socket
            inputReader.close();
            printWriter.close();
            objReader.close();
            toServer.close();
        } catch (IOException e) {}
        catch (ClassNotFoundException e) {}
        //catch (InterruptedException e) {}
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
}
