import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Base64;
import java.util.ArrayList;

public class CatchUpClientRunnable implements Runnable{

    private ServerInfo server;
    private Blockchain blockchain;

    public CatchUpClientRunnable(ServerInfo server, Blockchain blockchain) {
        this.server = server;
        this.blockchain = blockchain;
    }

    @Override
    public void run() {
        try {
            String message = "cu";
            ArrayList<Block> blocks = new ArrayList<Block>();
            Block matchingBlock = null;

            while(true){
                Socket toServer = new Socket();
                toServer.connect(new InetSocketAddress(server.getHost(), server.getPort()), 2000);
                PrintWriter printWriter = new PrintWriter(toServer.getOutputStream(), true);

                printWriter.println(message);
                printWriter.flush();

                ObjectInputStream objReader = new ObjectInputStream(toServer.getInputStream());

                Thread.sleep(20);
                Block inputBlock = (Block) objReader.readObject();
                if (inputBlock == null) {
                    printWriter.print("cc");
                    printWriter.flush();

                    printWriter.close();
                    objReader.close();
                    toServer.close();
                    continue;
                }

                blocks.add(inputBlock);

                byte[] previousBlockHash = inputBlock.getPreviousHash();

                //reached the end of remote chain, break loop
                if(compareHash(previousBlockHash, new byte[32]) == 0){
                    printWriter.print("cc");
                    printWriter.flush();

                    printWriter.close();
                    objReader.close();
                    toServer.close();
                    break;
                }

                matchingBlock = blockchain.getHead();
                if(matchingBlock != null){
                    while(compareHash(matchingBlock.calculateHash(), previousBlockHash) != 0){
                        matchingBlock = matchingBlock.getPreviousBlock();
                        if(matchingBlock == null) break;
                    }
                    if(matchingBlock != null){
                        printWriter.print("cc");
                        printWriter.flush();

                        printWriter.close();
                        objReader.close();
                        toServer.close();
                        break;
                    }
                }

                message = "cu|" + Base64.getEncoder().encodeToString(previousBlockHash);

                printWriter.print("cc");
                printWriter.flush();

                printWriter.close();
                objReader.close();
                toServer.close();
            }


            if(matchingBlock != null){
                blocks.get(blocks.size() - 1).setPreviousBlock(matchingBlock);
                blocks.get(blocks.size() - 1).setPreviousHash(matchingBlock.calculateHash());
                blocks.get(blocks.size() - 1).calculateHash();
            }
            else{
                blocks.get(blocks.size() - 1).setPreviousBlock(null);
                blocks.get(blocks.size() - 1).setPreviousHash(new byte[32]);
                blocks.get(blocks.size() - 1).calculateHash();
            }

            for(int i = 0; i < blocks.size() - 1; i++){
                blocks.get(i).setPreviousBlock(blocks.get(i + 1));
                blocks.get(i).setPreviousHash(blocks.get(i + 1).calculateHash());
                blocks.get(i).calculateHash();
            }

            Block temp = blocks.get(0);
            int count = 1;
            while(compareHash(temp.getPreviousHash(), new byte[32]) != 0){
                temp = temp.getPreviousBlock();
                count++;
            }

            blockchain.setLength(count);
            blockchain.setHead(blocks.get(0));

            return;
        } catch (IOException e) {}
        catch (ClassNotFoundException e) {}
        catch (InterruptedException e) {}
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
