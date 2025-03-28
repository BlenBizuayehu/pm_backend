package com.example;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

public class Main {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);

        // Enable BodyHandler to handle POST request bodies
        router.route().handler(BodyHandler.create());

        // Enable CORS for the frontend (localhost:4200 in this case)
        router.route().handler(CorsHandler.create("http://localhost:4200")
            .allowedMethod(HttpMethod.GET)   // Allow GET method
            .allowedMethod(HttpMethod.POST)  // Allow POST method
            .allowedMethod(HttpMethod.PUT)   // Allow PUT method
            .allowedMethod(HttpMethod.DELETE) // Allow DELETE method
            .allowedHeader("Access-Control-Allow-Origin") // Allow the 'Access-Control-Allow-Origin' header
            .allowedHeader("Content-Type")  // Allow 'Content-Type' header
            .allowedHeader("Authorization") // Allow 'Authorization' header (for passing JWT tokens)
            .allowCredentials(true));       // Allow sending credentials (cookies, JWTs)

        // Add a specific route to handle OPTIONS (preflight) requests
        router.options("/login").handler(ctx -> {
            ctx.response()
                .putHeader("Access-Control-Allow-Origin", "http://localhost:4200")
                .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE")
                .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .end();
        });

        // Add your login handler
        router.post("/login").handler(new AuthHandler(vertx));

        // Start HTTP server on port 8080
        vertx.createHttpServer().requestHandler(router).listen(8080, http -> {
            if (http.succeeded()) {
                System.out.println("Server is now running on port 8080");
            } else {
                System.out.println("Failed to launch the server: " + http.cause().getMessage());
            }
        });
    }
}
