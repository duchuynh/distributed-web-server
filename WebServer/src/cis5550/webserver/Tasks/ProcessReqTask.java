package cis5550.webserver.Tasks;

import cis5550.tools.Logger;
import cis5550.webserver.Request;
import cis5550.webserver.Response;
import cis5550.webserver.Server;
import cis5550.webserver.dataclasses.HttpReqData;
import cis5550.webserver.dataclasses.RouteEntry;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cis5550.webserver.Server.server;
import static cis5550.webserver.Server.staticFiles;

public class ProcessReqTask implements Runnable{
    private final Socket clientSock;
    public PrintWriter out;
    private static final Logger logger = Logger.getLogger(ProcessReqTask.class);
    HashMap<String, List<RouteEntry>> routes;
    OutputStream bOutputStream;

    public ProcessReqTask(Socket clientSock){
        this.clientSock = clientSock;
        this.routes = Server.routes;
        try {
            bOutputStream = clientSock.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
    @Override
    public void run() {
        try{

            //** the inputstream is a consistent stream that keeps reading data in a 1-dimension way. The data can be partial requests, or
            //full requests. Ex. POST HTTP/1.1\r\nHost: x\r\nContent-Length: 4\r\n\r\nDATAGET HTTP/1.1\r\nHost: x\r\n\r\n
            //It could read up to DATAGET for one chunk, then the rest.
            InputStream clientInputStream = clientSock.getInputStream();
            if (out == null){
                out = new PrintWriter(clientSock.getOutputStream(), true);
            }
            ByteArrayOutputStream bOutputStream = new ByteArrayOutputStream(); // accumulates bytes to output later
            //Iterate through all the chunks (full and partial packets) of the client's request
            int n;
            byte[] readBuf = new byte[1000];
            while ((n = clientInputStream.read(readBuf)) != -1) { //Reads multiple requests from client
                //collects the bytes from a single "chunk"
                bOutputStream.write(readBuf, 0, n);
                //Finds the index right after CRLFCRLF
                int idxHeaderEnd = server.findDoubleCRLF(bOutputStream);
                if (idxHeaderEnd < 0) continue;
                //Processes all the headers into a class object
                int contentLength = 0;
                HttpReqData httpReqData = server.processHeaders(bOutputStream, out);
                //Head just wants the headers without reading body
                if (httpReqData.method.equalsIgnoreCase("head")){
                    continue;
                }
                if (httpReqData.headers.containsKey("content-length")) {
                    contentLength = Integer.parseInt(httpReqData.headers.get("content-length").trim());
                }
                //If outputstream is < size of header + size of body, read again, to make sure all the chunks
                //Of partial reads are collected into the boutputstream
                if (bOutputStream.size() < idxHeaderEnd + contentLength) continue;
                //headerAndBody = array of accumulated bytes
                //if head occurs, just return the object without reading

                byte [] accumulatedBytes = bOutputStream.toByteArray();
                byte [] body = new byte[contentLength];
                //copies just the body part of the accumulated array into the body array and sets it
                System.arraycopy(accumulatedBytes, idxHeaderEnd, body, 0, contentLength);
                httpReqData.body = body;
                int idxRequestEnd = idxHeaderEnd + contentLength;
                bOutputStream.reset();
                //Collect the next REQ's data just in case the stream reads and receives data past the body of our first REQ.
                bOutputStream.write(accumulatedBytes, idxRequestEnd, accumulatedBytes.length - idxRequestEnd);
                //Every request gets sent to match the routes
                matchRouteForRequest(httpReqData);
            }
            clientSock.close();
            logger.info("Client Connection Closed");
        } catch (IOException e) {
            System.err.println("ProcessReqTaskError " + Thread.currentThread().getName() + " | " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     *Handles the matching route, builds the request/response objects, invokes the handler, sends either streamed writes or returned/body data, falls back to static files when no route matches, and converts route exceptions into 500 responses unless the response was already committed.
     * @param httpReqData
     */
    public void matchRouteForRequest(HttpReqData httpReqData) {

            boolean routeMatched = false;
            List<RouteEntry> methodRoutes = routes.getOrDefault(httpReqData.method, java.util.Collections.emptyList());
            if ((methodRoutes.isEmpty()) && staticFiles.locationCalled) serveStaticFile(httpReqData);

            for (RouteEntry routeEntry: methodRoutes){
                //process path params
                HashMap<String, String> pathParams;
                matchedBoolAndPathParams matchedBoolAndPathParams = matched(routeEntry.pathPattern, httpReqData.url);
                if (matchedBoolAndPathParams.matched){
                    routeMatched = true;
                    pathParams = matchedBoolAndPathParams.params;
                }else{
                    continue;
                }
                //process queryparams
                HashMap<String, String> queryParams = new HashMap<>();
                if(httpReqData.url.contains("?")){
                    processQueryParams(queryParams, httpReqData);
                }
                handleReqRes(httpReqData, routeEntry, pathParams, queryParams);
            }
        //If there are no matching routes, this means we serve the static file.
        // If there are no matching routes and the file does not exist, throw error
        if (!routeMatched && staticFiles.locationCalled){
            serveStaticFile(httpReqData);
        }
    }

    public void handleReqRes(HttpReqData httpReqData, RouteEntry routeEntry, HashMap<String, String> pathParams, HashMap<String, String> queryParams){
        Response response = null;
        Request request;
        try {
            //client attributes
            String clientHostName = clientSock.getInetAddress().getHostName();
            int clientPort = clientSock.getPort();
            InetSocketAddress remoteAddr = new InetSocketAddress(clientHostName, clientPort);
            //create req and response
            request = Request.createRequest(httpReqData.method, httpReqData.url, httpReqData.protocol,
                    httpReqData.headers, queryParams, pathParams, remoteAddr, httpReqData.body, server, null);
            if (httpReqData.headers.containsKey("cookie")){
                request.addCookies(httpReqData.cookiesMap);
            }
            response = Response.createResponse(httpReqData.protocol, httpReqData.body, server, out, clientSock);

            Object routeHandler = routeEntry.routeHandler.handle(request, response);
            //add set-cookie header to response of the new sessionId
            handleSessions(request, response);

            // If write() has been called, ignore both the return value of Route.handle() and
            // any calls to body() and bodyAsBytes().
            //Translation: When we use the API with write, the socket stays open as the client waits for more data
            //then, Close the socket when all writes are completed so that the client-side socket does not time out.
            if (response.getWriteFlag()){
                clientSock.close();
                return;
            }
            else{
                // If write() has not been called and Route.handle() returns something other than null,
                // call the toString() method on that object and send the result.
                //If write has not been called, and the route returns something, just send the returned object as a string
                if (routeHandler != null){ //Check!
                    byte[] routeBytes = routeHandler.toString().getBytes();
                    serverResponseWriteHeaders(response);
                    out.print("Content-Length: " + routeBytes.length + "\r\n\r\n");
                    out.flush();
                    bOutputStream.write(routeBytes, 0, routeBytes.length);
                }else{
                    // If write() has not been called and Route.handle returns null(), use the value from
                    // the most recent body() or bodyAsBytes() call.
                    //Translation: If write has not been called, just return the body
                    //check!
                    if (response.getBodyFlag()){
                        bOutputStream.write(response.getResponseBodyRaw(), 0, response.getResponseBodyRaw().length);
                    }
                    // If none of write(), body(), and bodyAsBytes() have been called and Route.handle returns null,
                    // do not send a body in the response. Check!
                    //Translation: just send headers
                    else{
                        serverResponseWriteHeaders(response);
                        out.print("Content-length: " + 0 + "\r\n\r\n");
                        out.flush();
                    }
                }
                //return;
            }
        }catch(Exception e){
            if (response != null) {
                if (!response.getWriteFlag()) {
                    server.internalServerError(out);
                }else{
                    {
                        try {
                            clientSock.close();
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }
            logger.error("Error at Thread:" + Thread.currentThread().getName() + " There is an issue with the routing logic.");
            e.printStackTrace();
        }
    }

    private void handleSessions(Request request, Response response) {
        if (request.getNewSessionFlag()){
            response.getHeaders().put("set-cookie", "SessionID=" + request.getSession().id());
        }
    }

    public void serveStaticFile(HttpReqData httpReqData)
    {
        Path path;
        path = server.createPath(httpReqData.url);
        if (!server.staticFileCheckErrors(path, out, httpReqData)) {
            readFile(httpReqData, clientSock, out);
        }
    }

    /**
     * Writes the response's headers all the way down to Duc's Server. Need to write content-length after
     * @param response
     */
    public void serverResponseWriteHeaders(Response response){
        out.print("HTTP/1.1 200 OK\r\n");
        for(Map.Entry<String, String> header: response.getHeaders().entrySet()){
            out.print(header.getKey() + ": " + header.getValue() + "\r\n");
        }
        out.print("Server: Duc's Server\r\n");
        //Remember content-length!
    }

    /**
     * Collect our request's query parameters into queryParams map
     * @param queryParams
     * @param httpReqData
     */
    private void processQueryParams(HashMap<String, String> queryParams, HttpReqData httpReqData) {
        String urlAfterQMark = httpReqData.url.substring(httpReqData.url.indexOf("?") + 1);
        String[] urlArr = urlAfterQMark.split("&");
        for (String query: urlArr){
            String[] equalsSplit = query.split("=");
            queryParams.put(java.net.URLDecoder.decode(equalsSplit[0]), java.net.URLDecoder.decode(equalsSplit[1]));
        }

        if (httpReqData.headers.containsKey("content-type")
                && httpReqData.headers.get("content-type").equalsIgnoreCase("application/x-www-form-urlencoded")){
            String body = new String(httpReqData.body);
            String[] bodyArr = body.split("&");
            for (String query: bodyArr){
                String[] equalsSplit = query.split("=");
                queryParams.put(java.net.URLDecoder.decode(equalsSplit[0]), java.net.URLDecoder.decode(equalsSplit[1]));
            }
        }
    }

    //Returns a boolean if the url contains values that could be stored as key value pairs, and the path pattern
    // Processes the params of the request
    public record matchedBoolAndPathParams(boolean matched, HashMap<String, String> params){}

    private matchedBoolAndPathParams matched(String routePathPattern, String reqUrl) {
        String[] routePathPatternArr = routePathPattern.split("/");
        int idxQMark;
        if (reqUrl.contains("?")){
            idxQMark = reqUrl.indexOf("?");
            reqUrl = reqUrl.substring(0, idxQMark);
        }
        String[] reqUrlArr = reqUrl.split("/");

        HashMap<String, String> params = new HashMap<>();
        boolean matched = true;
        if ((routePathPatternArr.length != reqUrlArr.length)){
            matched = false;
            return new matchedBoolAndPathParams (matched, params);
        }
        for(int i = 0; i < routePathPatternArr.length; i++){
            String pathPatternPiece = routePathPatternArr[i];
            String reqUrlPiece = reqUrlArr[i];
            if(pathPatternPiece.startsWith(":")){
                params.put(pathPatternPiece.substring(1), reqUrlPiece);
            }else if (!pathPatternPiece.startsWith(":")){

                if (!routePathPatternArr[i].equals(reqUrlArr[i])){
                    matched = false;
                }
            }
        }
        return new matchedBoolAndPathParams(matched, params);
    }

    /**
     * Reads a file specified in the HTTP request, retrieves its contents, and writes the file data
     * through the output stream to the client. This includes correctly formatting the HTTP response
     * header with the file's content length before transmitting the file data.
     *
     * @param httpReqData the HTTP request data, including the URL of the requested file
     * @param clientSock  the client socket used for communication, which provides an output stream
     * @param out         the `PrintWriter` for writing HTTP headers and responses to the client
     */
    public void readFile(HttpReqData httpReqData, Socket clientSock, PrintWriter out) {

        String url = httpReqData.startLine.split(" ")[1]; //i.e /file1.txt
        Path absoluteFilePath = Paths.get(Server.location, url);
        try (FileInputStream inputStream = new FileInputStream(absoluteFilePath.toString())) {
            //Conclude Header
            long contentLength = Files.size(absoluteFilePath);
            out.print("HTTP/1.1 200 OK\r\n");
            out.print("Content-Type: text/plain\r\n");
            out.print("Server: Duc's Server\r\n");
            out.print("Content-Length: " + contentLength + "\r\n\r\n");
            out.flush();

            //Set open the output stream from the socket
            OutputStream outputStream = clientSock.getOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;

            //Send over bytes of the file
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
            }
            outputStream.flush();
        } catch (Exception e) {
            logger.error("Error readFile: " + absoluteFilePath);
            System.err.println("Error readFile: " + absoluteFilePath);
            e.printStackTrace();
        }
    }

}
