package cis5550.webserver.dataclasses;

import cis5550.webserver.Route;

public class RouteEntry {
    public String method;
    public String pathPattern; // /users/:userId/orders/:orderId
    public Route routeHandler;

    public RouteEntry(String method, String pathPattern, Route routeHandler){
        this.method = method;
        this.pathPattern = pathPattern;
        this.routeHandler = routeHandler;
    }
}
