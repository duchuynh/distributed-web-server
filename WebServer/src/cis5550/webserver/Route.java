package cis5550.webserver;

import cis5550.webserver.Request;
import cis5550.webserver.Response;

@FunctionalInterface
public interface Route {
  Object handle(Request request, Response response) throws Exception;
}
