package com.example;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class AuthHandler implements Handler<RoutingContext> {

    private final PgPool dbClient;

    public AuthHandler(Vertx vertx, PgPool dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            System.out.println("\n=== NEW LOGIN REQUEST ===");
            System.out.println("Headers: " + ctx.request().headers());
            System.out.println("Raw body: " + ctx.getBodyAsString());
            
            JsonObject body = ctx.getBodyAsJson();

            // 1. Validate input
            if (body == null || 
                body.getString("full_name") == null || 
                body.getString("password") == null) {
                sendError(ctx, 400, "Missing credentials");
                return;
            }

            String fullName = body.getString("full_name");
            String password = body.getString("password");

            System.out.println("Extracted username: " + fullName);
            System.out.println("Extracted password: " + (password != null ? "[hidden]" : "null"));

            if (fullName == null || fullName.isBlank()) {
                sendError(ctx, 400, "Username is required");
                return;
            }

            if (password == null || password.isBlank()) {
                sendError(ctx, 400, "Password is required");
                return;
            }

            // 2. Query database
            dbClient.preparedQuery("SELECT user_id, password, role FROM users WHERE full_name = $1")
                .execute(Tuple.of(fullName))
                .onComplete(res -> {
                    if (res.failed()) {
                        System.err.println("Database error: " + res.cause().getMessage());
                        sendError(ctx, 500, "Database error");
                        return;
                    }

                    if (res.result().size() == 0) {
                        sendError(ctx, 401, "Invalid credentials");
                        return;
                    }

                    Row row = res.result().iterator().next();
                    processAuthentication(ctx, fullName, password, row);
                });
        } catch (Exception e) {
            System.err.println("Unexpected error in AuthHandler: " + e.getMessage());
            sendError(ctx, 500, "Internal server error");
        }
    }

    private void processAuthentication(RoutingContext ctx, String fullName, String password, Row row) {
        try {
            String storedPassword = row.getString("password").trim();
            String role = row.getString("role");
            Integer userId = row.getInteger("user_id");
            
            System.out.println("Database returned:");
            System.out.println("Stored password: " + storedPassword);
            System.out.println("Role: " + role);

            // Direct comparison of passwords
            if (!password.equals(storedPassword)) {
                sendError(ctx, 401, "Invalid credentials");
                return;
            }

            // Create response data
            JsonObject responseData = new JsonObject()
                .put("user_id", userId)
                .put("username", fullName)
                .put("role", role);

            // Only use session if it exists
           

            // Return success with user info
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(responseData.encode());
                
        } catch (Exception e) {
            System.err.println("Error in processAuthentication: " + e.getMessage());
            sendError(ctx, 500, "Internal server error");
        }
    }

    private void sendError(RoutingContext ctx, int code, String message) {
        System.err.println("Sending error: " + code + " - " + message);
        ctx.response()
            .setStatusCode(code)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", message).encode());
    }
}