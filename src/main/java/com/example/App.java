package com.example;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

public class App extends AbstractVerticle {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new App());
    }

    @Override
    public void start() {
        Router router = Router.router(vertx);
        router.get("/").handler(ctx -> ctx.response().end("Hello from Vert.x!"));

        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8080, res -> {
                if (res.succeeded()) {
                    System.out.println("Server started on port 8080");
                } else {
                    System.out.println("Failed to start server: " + res.cause());
                }
            });
    }
}
