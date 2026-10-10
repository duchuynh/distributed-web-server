package cis5550.webserver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

class ResponseImpl implements Response {

    //Default values
    int statusCode = 200;
    String reasonPhrase = "OK";
    Server server;
    PrintWriter out;
    Socket clientSock;
    OutputStream bOutputStream;

    //Gets Changed
    Map<String, String> headers = new HashMap<>();
    byte[] requestBodyRaw;
    byte[] responseBodyRaw;
    String protocol;
    boolean writeFlag = false;
    boolean setBodyFlag = false;

    public ResponseImpl(String protocol, byte[] requestBodyRaw, Server server, PrintWriter out, Socket clientSock) {
        this.headers.put("content-type", "text/html");
        this.requestBodyRaw = requestBodyRaw;
        this.server = server;
        this.out = out;
        this.protocol = protocol;
        this.clientSock = clientSock;
        try {
            bOutputStream = clientSock.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public ResponseImpl(String protocol, String body, Server server, PrintWriter out, Socket clientSock){
        this.headers.put("content-type", "text/html");
        this.requestBodyRaw = body.getBytes();
        this.server = server;
        this.out = out;
        this.protocol = protocol;
        this.clientSock = clientSock;
        try {
            bOutputStream = clientSock.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void header(String name, String value) {
        this.headers.put(name, value);
    }

    public boolean getWriteFlag(){
        return this.writeFlag;
    }
    public void setWriteFlag(){
        writeFlag = true;
    }
    public void body(String body) {
        this.responseBodyRaw = body.getBytes();
        this.setBodyFlag = true;
    }

    public void bodyAsBytes(byte bodyArg[]) {
        this.responseBodyRaw = bodyArg;
        this.setBodyFlag = true;
    }

    public void type(String contentType) {
        this.headers.put("content-type", contentType);
    }

    public void status(int statusCode, String reasonPhrase) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
    }
    public byte[] getResponseBodyRaw(){
        return responseBodyRaw;
    }
    public boolean getBodyFlag(){
        return setBodyFlag;
    }
    public Map<String, String> getHeaders(){
        return this.headers;
    }

    public void write(byte[] b) throws Exception {
        if (!writeFlag){
            out.print(protocol + " " + statusCode + " " + reasonPhrase + "\r\n");
            out.print(("Content-Type: " + this.headers.get("content-type") + "\r\n"));
            out.print("Server: Duc's Server\r\n");
            for (Map.Entry<String, String> entry : this.headers.entrySet()) {
                String header = entry.getKey();     //ignore content-type because already printed in line above
                if (header.equalsIgnoreCase("content-type") || header.equalsIgnoreCase("content-length"))
                    continue;
                String headerValue = entry.getValue();
                out.print(header + ": " + headerValue + "\r\n");
            }
            this.headers.put("Connection", "close");
            out.print("Connection: close\r\n\r\n");
            out.flush();
            //   * If none of write(), body(), and bodyAsBytes() have been called and Route.handle returns null,
            //     do not send a body in the response.
            //if (getBodyFlag()){
            bOutputStream.write(b, 0, b.length);
            //}
            setWriteFlag();
        }else{
            bOutputStream.write(b, 0, b.length);
        }
    }
    public void redirect(String url, int responseCode) {
    }
    public void halt(int statusCode, String reasonPhrase) {
    }
}
