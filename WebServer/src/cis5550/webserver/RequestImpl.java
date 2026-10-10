package cis5550.webserver;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// Provided as part of the framework code

class RequestImpl implements Request {
  String method;
  String url;
  String protocol;
  InetSocketAddress remoteAddr;
  Map<String,String> headers;
  Map<String,String> queryParams;
  Map<String,String> params;
  Map<String, String> cookiesMap;
  byte bodyRaw[];
  Server server;
  Session session;
  boolean newSession = false;

  public RequestImpl(String methodArg, String urlArg, String protocolArg, Map<String, String> headersArg, Map<String, String> queryParamsArg, Map<String, String> paramsArg,
                     InetSocketAddress remoteAddrArg, byte bodyRawArg[], Server serverArg) {
    method = methodArg;
    url = urlArg;
    remoteAddr = remoteAddrArg;
    protocol = protocolArg;
    headers = headersArg;
    queryParams = queryParamsArg;
    params = paramsArg;
    bodyRaw = bodyRawArg;
    server = serverArg;
  }

  public String requestMethod() {
  	return method;
  }
  public void setParams(Map<String,String> paramsArg) {
    params = paramsArg;
  }
  public int port() {
  	return remoteAddr.getPort();
  }
  public String url() {
  	return url;
  }
  public String protocol() {
  	return protocol;
  }
  public String contentType() {
  	return headers.get("content-type");
  }
  public String ip() {
  	return remoteAddr.getAddress().getHostAddress();
  }
  public String body() {
    return new String(bodyRaw, StandardCharsets.UTF_8);
  }
  public byte[] bodyAsBytes() {
  	return bodyRaw;
  }
  public int contentLength() {
  	return bodyRaw.length;
  }
  public String headers(String name) {
  	return headers.get(name.toLowerCase());
  }
  public Set<String> headers() {
  	return headers.keySet();
  }
  public String queryParams(String param) {
  	return queryParams.get(param);
  }
  public Set<String> queryParams() {
  	return queryParams.keySet();
  }
  public String params(String param) {
    return params.get(param);
  }
  public Map<String, String> getCookies (){return cookiesMap;};
  public void addCookies(HashMap<String, String> cookies) {this.cookiesMap = cookies;}
  public boolean getNewSessionFlag(){return this.newSession;}

  @Override
  public Session session() {
    String sessionID = null;
    //process the cookie string
    if (this.headers.containsKey("cookie")){
      sessionID = cookiesMap.getOrDefault("SessionID", null);
    }
    //if the server has the same session saved, and our req has the sessionID
    if(sessionID != null && Server.sessionStates.containsKey(sessionID)){
      SessionImpl sessionState = Server.sessionStates.get(sessionID);
      long accessedInterval = System.currentTimeMillis() - sessionState.lastAccessedTime;
      if (accessedInterval > sessionState.maxActiveInterval*1000L && !sessionState.expired){
        sessionState.expired = true;
      }else if (accessedInterval <= sessionState.maxActiveInterval*1000L && !sessionState.expired){
        sessionState.lastAccessedTime = System.currentTimeMillis();
      }
      this.session = sessionState;
      return sessionState;
      //If the req does not have the session id and we do not have a session saved in the server.
    }else if (sessionID == null || !Server.sessionStates.containsKey(sessionID)){
      String newId = randomizeSessionID();
      SessionImpl sessionState = new SessionImpl(newId);
      this.session = sessionState;
      this.newSession = true;
      Server.sessionStates.put(newId, sessionState);
      return sessionState;
    }
    return session;
  }

  public Map<String,String> params() {
    return params;
  }

  private String randomizeSessionID(){
    String characterSet = "abcdefghijklmnopqrstuvwxyz" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ" + "0123456789" + "!@";
    int sessionIdLength = 20;
    Random random = new Random();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < sessionIdLength; i++){
      int randomIdx = random.nextInt(characterSet.length());
      sb.append(characterSet.charAt(randomIdx));
    }
      return sb.toString();
  }

  @Override
  public Session getSession() {
    return session;
  }
}
