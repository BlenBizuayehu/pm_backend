package com.example;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

public class Main extends AbstractVerticle {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Main());
    }

    // Moved getAllowedMethods() to class level
    private Set<HttpMethod> getAllowedMethods() {
        Set<HttpMethod> allowedMethods = new HashSet<>();
        allowedMethods.add(HttpMethod.GET);
        allowedMethods.add(HttpMethod.POST);
        allowedMethods.add(HttpMethod.PUT);
        allowedMethods.add(HttpMethod.DELETE);
        allowedMethods.add(HttpMethod.OPTIONS);
        return allowedMethods;
    }

    @Override
    public void start() throws Exception {
        Router router = Router.router(vertx);

        // 1. Database Connection Setup
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setHost("127.0.0.1")
            .setPort(5432)
            .setDatabase("project_management")
            .setUser("postgres")
            .setProperties(Map.of(
            "DateStyle", "ISO", // Use ISO date format
            "IntervalStyle", "iso_8601" // Standard interval format
        ));

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        PgPool dbClient = PgPool.pool(vertx, connectOptions, poolOptions);

        // Test database connection
        dbClient.query("SELECT 1").execute()
            .onSuccess(res -> System.out.println("✅ Database connected successfully!"))
            .onFailure(err -> {
                System.err.println("❌ Database connection failed:");
                err.printStackTrace();
            });

        // 2. JWT Authentication Setup
        JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
            .setKeyStore(new KeyStoreOptions()
                .setType("jks")
                .setPath("keystore.jks")
                .setPassword("123456")));

        // 3. Route Handlers
        router.route().handler(BodyHandler.create());
        
        Set<String> allowedHeaders = new HashSet<>();
allowedHeaders.add("Content-Type");
allowedHeaders.add("Authorization");
        // CORS Configuration
        router.route().handler(CorsHandler.create()
            .addOrigin("http://localhost:4200")
            .allowedMethods(getAllowedMethods())
            .allowedHeaders(allowedHeaders)            
            .allowCredentials(true));

        // Login endpoint
        router.post("/login").handler(ctx -> {
            JsonObject authInfo = ctx.getBodyAsJson();
            if (authenticateUser(authInfo)) {
                String token = jwtAuth.generateToken(new JsonObject()
                    .put("sub", "user123")
                    .put("role", "user"));
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject().put("token", token).encode());
            } else {
                ctx.response().setStatusCode(401).end();
            }
        });

        // Protected endpoint example
        router.get("/protected").handler(JWTAuthHandler.create(jwtAuth))
            .handler(ctx -> {
                ctx.response().end("Protected resource accessed!");
            });

        // 4. Start Server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8080)
            .onSuccess(server -> System.out.println("Server running on port 8080"))
            .onFailure(err -> {
                System.err.println("Failed to start server:");
                err.printStackTrace();
            });
    }

    private boolean authenticateUser(JsonObject authInfo) {
        return "admin".equals(authInfo.getString("username")) 
            && "password".equals(authInfo.getString("password"));
    }
}