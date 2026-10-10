package cis5550.webserver;

import static cis5550.webserver.Server.*;

public class HelloWorldApp {
    public static void main(String[] args) {
        port(8080);
        securePort(443);
        get("/", (req,res) -> { return "Hello World - this is Duc Huynh"; });
    }
}
