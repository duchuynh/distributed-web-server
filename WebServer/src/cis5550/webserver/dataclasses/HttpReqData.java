package cis5550.webserver.dataclasses;

import java.util.HashMap;

public class HttpReqData {

    public String startLine;
    public HashMap<String, String> headers = new HashMap<>();
    public String method;
    public String url;
    public String protocol;
    public byte[] body;
    public HashMap<String, String> cookiesMap;
    public HttpReqData() {}
}
