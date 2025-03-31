package com.example;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class AuthHandler implements io.vertx.core.Handler<RoutingContext> {

    private static final Logger LOG = LoggerFactory.getLogger(AuthHandler.class);
    private static final int TOKEN_EXPIRY_HOURS = 24;

    private final PgPool dbClient;
    private final JWTAuth jwtAuth;

    public AuthHandler(PgPool dbClient, JWTAuth jwtAuth) {
        this.dbClient = dbClient;
        this.jwtAuth = jwtAuth;
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
            .preparedQuery("SELECT password_hash, role FROM users WHERE email = $1")
            .execute(Tuple.of(full_name)) // Using email as username
            .map(rows -> {
                if (!rows.iterator().hasNext()) {
                    return null;
                }
                Row row = rows.iterator().next();
                String storedHash = row.getString("password_hash");
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
        context.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("token", token)
                .put("expires_in", TOKEN_EXPIRY_HOURS * 3600)
                .encode());
    }

    private void sendErrorResponse(RoutingContext context, int statusCode, String message) {
        context.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("error", message)
                .encode());
    }
}