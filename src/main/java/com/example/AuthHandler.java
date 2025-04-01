package com.example;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class AuthHandler implements Handler<RoutingContext> {

    private final PgPool dbClient;
    private final JWTAuth jwtAuth;

    public AuthHandler(Vertx vertx, PgPool dbClient) {
        this.dbClient = dbClient;
        this.jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setBuffer("your-256-bit-secret-here-must-be-at-least-32-chars")));
    }

    @Override
    public void handle(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        
        // Validate input
        if (body == null || 
            body.getString("full_name") == null || 
            body.getString("password") == null) {
            sendError(ctx, 400, "Missing credentials");
            return;
        }

        String fullName = body.getString("full_name").trim();
        String password = body.getString("password").trim();

        // Query database
        dbClient.preparedQuery("SELECT password, role FROM users WHERE full_name = $1")
            .execute(Tuple.of(fullName))
            .onComplete(res -> {
                if (res.failed()) {
                    sendError(ctx, 500, "Database error");
                    return;
                }

                if (res.result().size() == 0) {
                    sendError(ctx, 401, "Invalid credentials");
                    return;
                }

                Row row = res.result().iterator().next();
                String storedPassword = row.getString("password").trim();
                String role = row.getString("role");
                
                // Plain text comparison
                if (!password.equals(storedPassword)) {
                    sendError(ctx, 401, "Invalid credentials");
                    return;
                }

                // Generate JWT token
                String token = jwtAuth.generateToken(
                    new JsonObject()
                        .put("username", fullName)
                        .put("role", role),
                    new JWTOptions().setExpiresInMinutes(60)
                );

                processAuthentication(ctx, fullName, password, row);
                System.out.println("Database returned:");
                System.out.println("Stored password: " + row.getString("password"));
                System.out.println("Role: " + row.getString("role"));

                sendSuccess(ctx, token);
            });
    }


    private void processAuthentication(RoutingContext ctx, String fullName, String password, Row row) {
        String storedPassword = row.getString("password").trim();
        System.out.println("Stored password: " + storedPassword);
        String role = row.getString("role");
        
        // Direct comparison of passwords
        if (!password.equals(storedPassword)) {
            sendError(ctx, 401, "Invalid credentials");
            return;
        }

        // Generate JWT token
        String token = jwtAuth.generateToken(
            new JsonObject()
                .put("username", fullName)
                .put("role", role),
            new JWTOptions().setExpiresInMinutes(60)
        );

        sendSuccess(ctx, token);
    }

    private void sendError(RoutingContext ctx, int code, String message) {
        ctx.response()
            .setStatusCode(code)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", message).encode());
    }

    private void sendSuccess(RoutingContext ctx, String token) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("token", token)
                .put("expires_in", 3600)
                .encode());
    }
}
