package com.example;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
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
    private static final int TOKEN_EXPIRY_HOURS = 24; // Set your desired expiration time

    private final PgPool dbClient;
    private final JWTAuth jwtAuth;
    private static final Logger LOG = LoggerFactory.getLogger(AuthHandler.class);

    public AuthHandler(Vertx vertx, PgPool dbClient) {
        this.dbClient = dbClient;
        this.jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
            .addPubSecKey(new PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setBuffer("your-256-bit-secret-here-must-be-at-least-32-chars")));
    }
    @Override
    public void handle(RoutingContext context) {
        JsonObject loginData = context.getBodyAsJson();
        LOG.debug("Received login request: {}", loginData);
    
        String fullName = loginData.getString("full_name");
        String password = loginData.getString("password");
    
        validateCredentials(fullName, password).onComplete(res -> {
            if (res.failed()) {
                LOG.error("Login error", res.cause());
                sendErrorResponse(context, 500, "Server error");
    
            return;
        }

        String role = res.result();
        if (role == null) {
            LOG.warn("Failed login attempt for: {}", fullName);
            sendErrorResponse(context, 401, "Invalid credentials");
            return;
        }

        
        LOG.info("Successful login for: {} (Role: {})", fullName, role);
        String token = createJWTToken(fullName, role);
        sendSuccessResponse(context, token);
    });
}

    private Future<String> validateCredentials(String full_name, String password) {
        // Changed to query by email and use password_hash column
        return dbClient
            .preparedQuery("SELECT password, role FROM users WHERE email = $1")
            .execute(Tuple.of(full_name)) // Using email as username
            .map(rows -> {
                if (!rows.iterator().hasNext()) {
                    return null;
                }
                Row row = rows.iterator().next();
                String storedHash = row.getString("password");
                String role = row.getString("role");

                if (BCrypt.checkpw(password, storedHash)) {
                    return role;
                }
                return null;
            });
    }



    private String createJWTToken(String username, String role) {
        return jwtAuth.generateToken(new JsonObject()
            .put("username", username)
            .put("role", role)
            .put("iss", "your-issuer")
            .put("exp", Instant.now().plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS).getEpochSecond())
        );
    }

    private void sendSuccessResponse(RoutingContext context, String token) {
        context.response();




        JsonObject body = context.getBodyAsJson(); // Extract request body
        if (body == null) {
            sendErrorResponse(context, 400, "Invalid request body");
            return;
        }
        
        String fullName = body.getString("full_name", "").trim();
        String password = body.getString("password", "").trim();
        

        // Query database
        dbClient.preparedQuery("SELECT password, role FROM users WHERE full_name = $1")
            .execute(Tuple.of(fullName))
            .onComplete(res -> {
                if (res.failed()) {
                    sendErrorResponse(context, 500, "Database error");
                    return;
                }

                if (res.result().size() == 0) {
                    sendErrorResponse(context, 401, "Invalid credentials");
                    return;
                }

                Row row = res.result().iterator().next();
                processAuthentication(context, fullName, password, row);
            });
    }

    private void processAuthentication(RoutingContext ctx, String fullName, String password, Row row) {
        String storedPassword = row.getString("password").trim();
        String role = row.getString("role");
        
        // Direct comparison of passwords
        if (!password.equals(storedPassword)) {
            sendErrorResponse(ctx, 401, "Invalid credentials");
            return;
        }

        // Generate JWT token with user claims
        String token = jwtAuth.generateToken(
            new JsonObject()
                .put("sub", fullName)  // Standard JWT subject claim
                .put("role", role)     // Custom claim for role
                .put("iat", System.currentTimeMillis() / 1000), // Issued at
            new JWTOptions()
                .setExpiresInMinutes(1440)
                .setAlgorithm("HS256")
        );

        // Return token and user info
        JsonObject response = new JsonObject()
            .put("token", token)
            .put("token_type", "Bearer")
            .put("expires_in", 3600)
            .put("user", new JsonObject()
                .put("full_name", fullName)
                .put("role", role));

        sendSuccess(ctx, response);
    }

    private void sendErrorResponse(RoutingContext ctx, int code, String message) {
        ctx.response()
            .setStatusCode(code)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("error", message).encode());
    }

    private void sendSuccess(RoutingContext ctx, JsonObject response) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(response.encode());
    }
}