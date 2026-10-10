package cis5550.webserver;

import java.util.HashMap;

public class SessionImpl implements Session {
    String id;
    long creationTime;
    public long lastAccessedTime;
    public long maxActiveInterval = 300;
    HashMap<String, Object> attributes;
    public boolean expired = false;

    public SessionImpl(String id) {
        this.id = id;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = System.currentTimeMillis();
        this.attributes = new HashMap<>();
    }

    public String id() {
        return this.id;
    }

    public long creationTime() {
        return this.creationTime;
    }

    public long lastAccessedTime() {
        return this.lastAccessedTime;
    }

    public void maxActiveInterval(int seconds) {
        this.maxActiveInterval = seconds;
    }

    public void invalidate() {
        //TODO complete this
    }

    public Object attribute(String name) {
        return attributes.getOrDefault(name, null);
    }

    public void attribute(String name, Object value) {
        attributes.put(name, value);
    }
}
