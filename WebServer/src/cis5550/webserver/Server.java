package cis5550.webserver;
import cis5550.tools.Logger;
import cis5550.webserver.Tasks.ExpireSessionTask;
import cis5550.webserver.Tasks.ProcessReqTask;
import cis5550.webserver.Tasks.RunServerTask;
import cis5550.webserver.dataclasses.HttpReqData;
import cis5550.webserver.dataclasses.RouteEntry;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final Logger logger = Logger.getLogger(Server.class);
    public static Server server = null;
    public static boolean serverStarted = false;
    public static int httpPort = 80;
    public static int httpsPort;
    public static String location;
    public static boolean routesDefined = false;
    public static HashMap<String, List<RouteEntry>> routes = new HashMap<>();
    public static ConcurrentHashMap<String, SessionImpl> sessionStates = new ConcurrentHashMap<>();


    public static void startServerThread() {
        try {
            if (httpPort != 0){
                RunServerTask runServerTask = new RunServerTask(httpPort, false, logger);
                Thread runServerThread = new Thread(runServerTask);
                runServerThread.start();
                logger.info("http Server started on Thread:" + runServerThread);
            }
            if (httpsPort != 0){
                RunServerTask runServerTask = new RunServerTask(httpsPort,true, logger);
                Thread runServerThread = new Thread(runServerTask);
                runServerThread.start();
                logger.info("httpS Server started on Thread:" + runServerThread);
            }
        } catch (Exception e) {
            logger.error("Something is wrong with running the Server thread");
        }
    }

    /**
     * Receiver: Accept client connections
     *
     */
    public static void clientAccepts(ServerSocket serverSocket) {
        try {
            while (true) {
                Socket clientSock = serverSocket.accept();
                System.out.println("Client Accepts: " + serverSocket);
                clientSock.setTcpNoDelay(true);
                logger.info("Server Accepts Client Connection from: " + clientSock.getRemoteSocketAddress());
                ProcessReqTask processReqTask = new ProcessReqTask(clientSock);
                Thread processReqThread = new Thread(processReqTask);
                processReqThread.start();
                logger.info("Starting thread processReqThread: " + processReqThread.getName());
                ExpireSessionTask expireSessionTask = new ExpireSessionTask();
                Thread expireSessionThread = new Thread(expireSessionTask);
                if(!expireSessionThread.isAlive()){
                    expireSessionThread.start();
                }
            }
        } catch (IOException e) {
            System.err.println("IOException: Client Socket Accept|" + e.getMessage());
            logger.error("IOException: Client Socket Accept|" + e.getMessage());
            e.printStackTrace();
        }
    }
    public void parseReqCookie(String cookies, HashMap<String, String> cookiesMap) {
        for(String cookie : cookies.split(";")){
            cookiesMap.put(cookie.split("=")[0], cookie.split("=")[1]);
        }
    }
    /**
     * Adds headers of each of the requests into a class object.
     * Then prints the parsed lines
     *
     * @param bOutputStream the output stream containing the accumulated bytes to process
     */
    public HttpReqData processHeaders(ByteArrayOutputStream bOutputStream, PrintWriter out){
        //Processes the accumulated byte array
        try{
            ByteArrayInputStream bInputStream = new ByteArrayInputStream(bOutputStream.toByteArray());
            InputStreamReader inputStreamReader = new InputStreamReader(bInputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            HttpReqData httpReqData = new HttpReqData();
            String line;
            //process startLine
            line = bufferedReader.readLine();
            httpReqData.startLine = line + "\r\n";
            System.out.println(line);
            HashMap<String, String> cookiesMap = new HashMap<>();
            //add headers to headersMap
            while ((line = bufferedReader.readLine()) != null){
                if (line.isEmpty()) break; //breaks out of header, last line will be the header's second CRLF in the doubleCRLF
                System.out.print(line + "\r\n");
                String [] headerLine = line.split(":");
                String headerName = headerLine[0].toLowerCase();
                String headerValue = headerLine[1].trim();
                if (headerName.equals("cookie")){
                    parseReqCookie(headerValue, cookiesMap);
                    httpReqData.cookiesMap = cookiesMap;
                }
                httpReqData.headers.put(headerName, headerValue);
            }
            //After processing the request, check for errors and set data if there are no errors.
            if (!checkForErrors(httpReqData, out)){
                httpReqData.method = httpReqData.startLine.split(" ")[0];
                httpReqData.url = httpReqData.startLine.split(" ")[1];
                httpReqData.protocol = httpReqData.startLine.split(" ")[2].trim();
            }

            return httpReqData;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean checkForErrors(HttpReqData httpReqData, PrintWriter out) {
        HashSet<String> validMethods = new HashSet<>(Arrays.asList("GET", "HEAD", "POST", "PUT", "get", "head", "post", "put"));

        if (httpReqData.startLine.split(" ").length < 3 || httpReqData.headers.get("host") == null) {
            out.print("HTTP/1.1 400 Bad Request\r\n");
            out.print("Content-Type: text/plain\r\n");
            out.print("Server: Duc's Server\r\n");
            out.print("Content-Length: " + "Error 400 (Bad Request)".length() + "\r\n\r\n");
            out.print("Error 400 (Bad Request)");
            out.flush();
            logger.error("Error 400: Start line is missing or malformed");
            return true;
        }
        String method = httpReqData.startLine.split(" ")[0]; //i.e GET
        String url = httpReqData.startLine.split(" ")[1]; //i.e /file1.txt
        String protocol = httpReqData.startLine.split(" ")[2].trim(); //i.e HTTP/1.1

        if (url.contains("..")){
            out.print("HTTP/1.1 403 Forbidden\r\n");
            out.print("Content-Type: text/plain\r\n");
            out.print("Server: Duc's Server\r\n");
            out.print("Content-Length: " + "Error 403 Forbidden".length() + "\r\n\r\n");
            out.print("Error 403 Forbidden");
            out.flush();
            logger.error("Error 403: URL contains '..', which is not allowed.");
            return true;
        }
        else if (!validMethods.contains(method)) {
            out.print("HTTP/1.1 501 Method Not Implemented\r\n");
            out.print("Content-Type: text/plain\r\n");
            out.print("Server: Duc's Server\r\n");
            out.print("Content-Length: " + "Error 501 (Method Not Implemented)".length() + "\r\n\r\n");
            out.print("Error 501 (Method Not Implemented)");
            out.flush();
            logger.error("Error 501: Method not implemented. This method has not been implemented in the server.");
            return true;
        }
        else if (!protocol.equalsIgnoreCase("http/1.1")){
            out.print("HTTP/1.1 505 HTTP Version Not Supported\r\n");
            out.print("Content-Type: text/plain\r\n");
            out.print("Server: Duc's Server\r\n");
            out.print("Content-Length: " + ("Error 505 (HTTP " + protocol + " Not Supported)").length() + "\r\n\r\n");
            out.print("Error 505 (HTTP " + protocol + " Not Supported)");
            out.flush();
            logger.error("Error 505: HTTP version not supported. Only HTTP/1.1 is supported.");
            return true;
        }
        return false;
    }
    public boolean staticFileCheckErrors(Path path, PrintWriter out, HttpReqData httpReqData){
        if (httpReqData.method.equalsIgnoreCase("POST") || httpReqData.method.equalsIgnoreCase("PUT")) {
            out.print("HTTP/1.1 405 Method Not Allowed\r\n");
            out.print("Content-Type: text/plain\r\n");
            out.print("Server: Duc's Server\r\n");
            out.print("Content-Length: " + "Error 405 (Method Not Allowed)".length() + "\r\n\r\n");
            out.print("Error 405 (Method Not Allowed)");
            out.flush();
            logger.error("Error 405: Method not allowed, either PUT or POST requests were received.");
            return true;
        }
        else if (!Files.exists(path)) {
            out.print("HTTP/1.1 404 Not Found\r\n");
            out.print("Content-Type: text/plain\r\n");
            out.print("Server: Duc's Server\r\n");
            out.print("Content-Length: " + "Error 404 (Not Found)".length() + "\r\n\r\n");
            out.print("Error 404 (Not Found)");
            out.flush();
            logger.error("Error 404: File not found in the directory " + Server.location);
            return true;
        }
        return false;
    }
    public Path createPath(String url) {
        if (url.charAt(0) == '/') {
            url = url.substring(1);
        }
        return Paths.get(Server.location, url).normalize().toAbsolutePath();
    }
    public void readUpToDoubleCRLF(ByteArrayInputStream bInputStream) {
        // Implementation for reading up to double CRLF
        try{
            int asciiValue;
            int prev = -1;
            int count = 0;
            //BufferReader reads every char into an int ascii value
            while ((asciiValue = bInputStream.read()) != -1) {
                if (asciiValue == 13){
                    prev = asciiValue;
                }
                else if (prev == 13 && asciiValue == 10){
                    count += 1;
                } else{
                    count = 0;
                }
                if (count == 2){
                    break;
                }
            }
        } catch (Exception e){
            System.err.println("IOException: readUpToDoubleCRLF|" + e.getMessage());
            logger.error("IOException: readUpToDoubleCRLF|" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reads a specified number of bytes from the provided input stream.
     * This method processes the input stream byte by byte, up to the specified content length.
     * Processes the accumulated bytes that we have already read from the connection
     *
     * @param bInputStream  the input stream from which bytes are read
     * @param contentLength the number of bytes to read from the input stream
     */
    public byte[] readBody(ByteArrayInputStream bInputStream, int contentLength) {
        byte[] body = new byte[contentLength];
        bInputStream.read(body, 0, contentLength);
        return body;
    }

    public int findDoubleCRLF(ByteArrayOutputStream bOutputStream) {
        byte [] doubleCRLF = {13, 10, 13, 10};
        byte[] bytes = bOutputStream.toByteArray();
        int matched = 0;
        for(int i = 0; i < bytes.length; i++){
            if(bytes[i] == doubleCRLF[matched]){
                matched++;
                if (matched == doubleCRLF.length){
                    return i + 1;
                }
            }else{
                if (bytes[i] == doubleCRLF[0]){
                    matched = 1;
                }else{
                    matched = 0;
                }
            }
        }
        return -1;
    }

    public void internalServerError(PrintWriter out){
        out.print("HTTP/1.1 500 Internal Server Error\r\n");
        out.print("Content-Type: text/plain\r\n");
        out.print("Server: Duc's Server\r\n");
        out.print("Content-Length: " + "Error 500 Internal Server Error".length() + "\r\n\r\n");
        out.print("Error 505 Internal Server Error");
        out.flush();
    }

    //**Spark Implementation
    public static class staticFiles {
        public static boolean locationCalled = false;
        public static void location(String location) {
            Server.location = Paths.get(location).toAbsolutePath().normalize().toString();
            locationCalled = true;
        }
    }

    public static void get(String path, Route route) {
        if (Server.server == null) {
            Server.server = new Server();
        }
        if (!Server.serverStarted) {
            Server.serverStarted = true;
            startServerThread();
        }
        //{"GET": (path pattern, Route)
        RouteEntry routeEntry = new RouteEntry("GET", path, route);
        //Adds key "GET" and an emptylist as value if get is not there, otherwise just add the routeentry.
        //key in lambda is used to check if GET is in the hashmap or not
        routes.computeIfAbsent("GET", key -> new ArrayList<>()).add(routeEntry);
        routesDefined = true;
    }

    public static void post(String path, Route route) {
        if (Server.server == null) {
            server = new Server();
        }
        if (!Server.serverStarted) {
            serverStarted = true;
            startServerThread();
        }
        RouteEntry routeEntry = new RouteEntry("POST", path, route);
        routes.computeIfAbsent("POST", key -> new ArrayList<>()).add(routeEntry);
        routesDefined = true;
    }

    public static void put(String path, Route route) {
        if (Server.server == null) {
            server = new Server();
        }
        if (!Server.serverStarted) {
            serverStarted = true;
            startServerThread();
        }
        RouteEntry routeEntry = new RouteEntry("PUT", path, route);
        routes.computeIfAbsent("PUT", key -> new ArrayList<>()).add(routeEntry);
        routesDefined = true;
    }

    public static void port(int httpPort) {
        Server.httpPort = httpPort;
    }
    public static void securePort(int httpsPort){
        if (!routesDefined){
            Server.httpsPort = httpsPort;
        }
    }
}
