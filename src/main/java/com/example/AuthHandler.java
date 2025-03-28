package com.example;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.RoutingContext;

public class AuthHandler implements Handler<RoutingContext> {

    private final Vertx vertx;
    private final JWTAuth jwtAuth;

    // Constructor to initialize JWTAuth with Vertx instance
    public AuthHandler(Vertx vertx) {
        this.vertx = vertx;

        // Set up JWT configuration using KeyStoreOptions for key storage
        KeyStoreOptions keyStoreOptions = new KeyStoreOptions()
            .setType("jks")  // Set the type to jks (Java KeyStore)
            .setPath("keystore.jks")  // Path to your keystore file
            .setPassword("123456");  // Password for the keystore

        JWTAuthOptions options = new JWTAuthOptions().setKeyStore(keyStoreOptions);

        // Initialize JWTAuth instance
        this.jwtAuth = JWTAuth.create(vertx, options);
    }

    @Override
    public void handle(RoutingContext context) {
        // Extract login data (username and password) from the request body
        JsonObject loginData = context.getBodyAsJson();
        String username = loginData.getString("username");
        String password = loginData.getString("password");

        // Validate credentials (this is a simple check; you can replace it with a database check)
        validateCredentials(username, password, res -> {
            if (res.succeeded()) {
                // If valid, generate JWT token with username and role
                String token = createJWTToken(username);

                // Return the token in the response
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("token", token).encode());
            } else {
                // If invalid credentials, return error
                context.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("error", "Invalid credentials").encode());
            }
        });
    }

    private void validateCredentials(String username, String password, Handler<Future<Void>> resultHandler) {
        // Dummy validation for demonstration purposes
        if ("admin".equalsIgnoreCase(username) && "password123".equals(password)) {
            // Replace this with your actual DB check (e.g., querying a database)
            resultHandler.handle(Future.succeededFuture());
        } else {
            resultHandler.handle(Future.failedFuture("Invalid credentials"));
        }
    }

    private String createJWTToken(String username) {
        // Generate JWT token with username and role (based on user type, here default is "Admin")
        JsonObject claims = new JsonObject()
            .put("username", username)  // Username as claim
            .put("role", "Admin");      // Role as claim (can be dynamic depending on the user)

        // Generate token with the claims and return it
        return jwtAuth.generateToken(claims);
    }
}
