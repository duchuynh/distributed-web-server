package cis5550.webserver.Tasks;

import cis5550.tools.Logger;
import cis5550.webserver.Server;

import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;

public class RunServerTask implements Runnable {
    
    boolean secure;
    int port;
    Logger logger;
    public RunServerTask(int port, boolean secure, Logger logger) {
        this.port = port;
        this.secure = secure;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            if (secure){
                String pwd = "secret";
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(new FileInputStream(Paths.get("keystore.jks").toAbsolutePath().normalize().toFile()), pwd.toCharArray());
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
                keyManagerFactory.init(keyStore, pwd.toCharArray());
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
                ServerSocketFactory factory = sslContext.getServerSocketFactory();
                ServerSocket serverSocketTLS = factory.createServerSocket(port);
                System.out.println("httpS serverSocket is listening on port: " + port);
                logger.info("httpS serverSocket is listening on port: " + port);
                Server.clientAccepts(serverSocketTLS);
                serverSocketTLS.close();
            }
            else{
                ServerSocket serverSocket = new ServerSocket(port);
                System.out.println("http serverSocket is listening on port: " + port);
                logger.info("http serverSocket is listening on port: " + port);
                Server.clientAccepts(serverSocket);
                serverSocket.close();
            }
            }catch (Exception e) {
                    System.err.println("IOException: Server Socket| " + e.getMessage());
                    logger.error("IOException: Server Socket|" + e.getMessage());
                    e.printStackTrace();
            }
    }
}
