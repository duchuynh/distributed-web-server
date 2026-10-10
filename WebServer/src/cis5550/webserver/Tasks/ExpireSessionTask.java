package cis5550.webserver.Tasks;

import cis5550.webserver.Server;
import cis5550.webserver.SessionImpl;

import java.util.HashMap;
import java.util.Map;

public class ExpireSessionTask implements Runnable {

    @Override
    public void run() {
        while(true){
            for(Map.Entry<String, SessionImpl> session: Server.sessionStates.entrySet()){
                String sessionID = session.getKey();
                SessionImpl sessionState = session.getValue();
                long accessedInterval = System.currentTimeMillis() - sessionState.lastAccessedTime;
                if ((accessedInterval > sessionState.maxActiveInterval*1000L) || (sessionState.expired)){
                    Server.sessionStates.remove(sessionID);
                }
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                System.err.println("ExpireSessionTask Error: " + Thread.currentThread().getName() + " | " + e.getMessage());
                throw new RuntimeException(e);

            }
        }
    }
}
