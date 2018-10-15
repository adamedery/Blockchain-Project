import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Iterator;

public class PeriodicPrinterRunnable implements Runnable{


    private HashMap<ServerInfo, Date> serverStatus;

    public PeriodicPrinterRunnable(HashMap<ServerInfo, Date> serverStatus) {
        this.serverStatus = serverStatus;
    }

    @Override
    public void run() {
        while(true) {
            Iterator it = serverStatus.entrySet().iterator();

            while (it.hasNext()) {
                HashMap.Entry entry = (HashMap.Entry)it.next();
                if (new Date().getTime() - ((Date) entry.getValue()).getTime() > 6000) {
                    it.remove();
                }
                else{
                    System.out.print(((ServerInfo) entry.getKey()).getHost() + "|" + ((ServerInfo) entry.getKey()).getPort() + "-" + entry.getValue().toString() + " ");
                }
            }

            System.out.println();

            // sleep for two seconds
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }
        }
    }
}
