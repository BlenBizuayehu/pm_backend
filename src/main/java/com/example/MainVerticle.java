package com.example;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

public class MainVerticle extends AbstractVerticle {
    
    @Override
    public void start(Promise<Void> startPromise) {
        // 1. Configure PostgreSQL
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setPort(5432)
            .setHost("localhost")
            .setDatabase("project_management")
            .setUser("postgres")
            .setPassword("yourpassword")
            .setConnectTimeout(5000);

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        PgPool dbClient = PgPool.pool(vertx, connectOptions, poolOptions);

        // 2. Test connection
        dbClient.query("SELECT 1").execute()
            .onSuccess(res -> {
                System.out.println("✅ PostgreSQL connection verified");
                
                // 3. Create router and handlers
                Router router = Router.router(vertx);
                AuthHandler authHandler = new AuthHandler(vertx, dbClient);
                UserController userController = new UserController(dbClient);
                
                // 4. Configure CORS
                Set<String> allowedHeaders = new HashSet<>();
                allowedHeaders.add("x-requested-with");
                allowedHeaders.add("Access-Control-Allow-Origin");
                allowedHeaders.add("origin");
                allowedHeaders.add("Content-Type");
                allowedHeaders.add("accept");
                allowedHeaders.add("Authorization");

                Set<HttpMethod> allowedMethods = new HashSet<>();
                allowedMethods.add(HttpMethod.GET);
                allowedMethods.add(HttpMethod.POST);
                allowedMethods.add(HttpMethod.OPTIONS);
                allowedMethods.add(HttpMethod.DELETE);
                allowedMethods.add(HttpMethod.PATCH);
                allowedMethods.add(HttpMethod.PUT);

                List<String> allowedOrigins = Arrays.asList(
                    "http://localhost:4200"
                );

                router.route().handler(CorsHandler.create()
                    .addOrigins(allowedOrigins)
                    .allowedHeaders(allowedHeaders)
                    .allowedMethods(allowedMethods)
                    .allowCredentials(true));
                
                // 5. Configure routes
                router.route().handler(BodyHandler.create());
                
                // OPTIONS handler
                router.options("/login").handler(ctx -> {
                    ctx.response()
                        .putHeader("Access-Control-Allow-Origin", "http://localhost:4200")
                        .putHeader("Access-Control-Allow-Methods", "POST, OPTIONS")
                        .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
                        .end();
                });
                
                // Auth routes
                router.post("/login").handler(authHandler);
                
                // User management routes
                router.get("/api/users").handler(userController::getAllUsers);
                router.post("/api/users").handler(userController::createUser);
                router.put("/api/users/:user_id").handler(userController::updateUser);
                router.delete("/api/users/:user_id").handler(userController::deleteUser);
                // Health check
                router.get("/").handler(ctx -> ctx.response().end("Server is running"));
                
                // 6. Start server
                vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(8080)
                    .onSuccess(server -> {
                        System.out.println("🚀 Server running on http://localhost:8080");
                        startPromise.complete();
                    })
                    .onFailure(startPromise::fail);
            })
            .onFailure(err -> {
                System.err.println("❌ Database connection failed");
                startPromise.fail(err);
            });
    }
}
