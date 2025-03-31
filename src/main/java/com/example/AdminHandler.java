package com.example;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

public class AdminHandler implements Handler<RoutingContext> {
    private final Pool dbPool;
    private final Vertx vertx;

      public AdminHandler(Vertx vertx, Pool dbPool) {
        this.vertx = vertx;
        this.dbPool = dbPool;
    }

    public void handle(RoutingContext ctx) {
        String path = ctx.request().path();

        switch (path) {
            case "/admin/overview":
                getSystemOverview(ctx);
                break;
            case "/admin/users":
                getUsers(ctx);
                break;
            case "/admin/users/:id":
                updateUser(ctx);
                break;
            case "/admin/reports/users":
                getUserReports(ctx);
                break;
            case "/admin/reports/projects":
                getProjectReports(ctx);
                break;
            default:
                ctx.response().setStatusCode(404).end("Not Found");
                break;
        }
    }

    public void getSystemOverview(RoutingContext ctx) {
        String query = "SELECT " +
                "(SELECT COUNT(*) FROM users) AS totalUsers, " +
                "(SELECT COUNT(*) FROM projects) AS totalProjects, " +
                "(SELECT COUNT(*) FROM tasks) AS totalTasks, " +
                "(SELECT COUNT(*) FROM users WHERE active = 1) AS activeUsers";

        dbPool.query(query).execute(ar -> {
            if (ar.succeeded()) {
                JsonObject result = new JsonObject();
                ar.result().forEach(row -> {
                    result.put("totalUsers", row.getInteger("totalusers"))
                          .put("totalProjects", row.getInteger("totalprojects"))
                          .put("totalTasks", row.getInteger("totaltasks"))
                          .put("activeUsers", row.getInteger("activeusers"));
                });
                ctx.response().putHeader("Content-Type", "application/json").end(result.encodePrettily());
            } else {
                ctx.fail(500);
            }
        });
    }

    public void getUsers(RoutingContext ctx) {
        String query = "SELECT id, username, email, role FROM users";
        dbPool.query(query).execute(ar -> {
            if (ar.succeeded()) {
                JsonArray users = new JsonArray();
                ar.result().forEach(row -> {
                    users.add(new JsonObject()
                            .put("id", row.getInteger("id"))
                            .put("username", row.getString("username"))
                            .put("email", row.getString("email"))
                            .put("role", row.getString("role")));
                });
                ctx.response().putHeader("Content-Type", "application/json").end(users.encodePrettily());
            } else {
                ctx.fail(500);
            }
        });
    }

    public void addUser(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        String query = "INSERT INTO users (username, email, role, password) VALUES ($1, $2, $3, $4)";

        dbPool.preparedQuery(query).execute(Tuple.of(
                body.getString("username"),
                body.getString("email"),
                body.getString("role"),
                hashPassword(body.getString("password"))
        ), ar -> {
            if (ar.succeeded()) {
                ctx.response().end(new JsonObject().put("message", "User added successfully").encode());
            } else {
                ctx.fail(500);
            }
        });
    }

    public void deleteUser(RoutingContext ctx) {
        String query = "DELETE FROM users WHERE id = $1";
        dbPool.preparedQuery(query).execute(Tuple.of(Integer.parseInt(ctx.pathParam("id"))), ar -> {
            if (ar.succeeded()) {
                ctx.response().end(new JsonObject().put("message", "User deleted successfully").encode());
            } else {
                ctx.fail(500);
            }
        });
    }

    public void updateUser(RoutingContext ctx) {
        String query = "UPDATE users SET role = $1 WHERE id = $2";
        JsonObject body = ctx.getBodyAsJson();
        dbPool.preparedQuery(query).execute(Tuple.of(body.getString("role"), Integer.parseInt(ctx.pathParam("id"))), ar -> {
            if (ar.succeeded()) {
                ctx.response().end("User updated successfully");
            } else {
                ctx.fail(500);
            }
        });
    }

    public void getUserReports(RoutingContext ctx) {
        String query = "SELECT * FROM user_reports ORDER BY report_date DESC LIMIT 10";
        dbPool.query(query).execute(ar -> {
            if (ar.succeeded()) {
                JsonArray reports = new JsonArray();
                ar.result().forEach(row -> {
                    reports.add(new JsonObject()
                            .put("reportId", row.getInteger("report_id"))
                            .put("reportDate", row.getString("report_date"))
                            .put("details", row.getString("details")));
                });
                ctx.response().putHeader("Content-Type", "application/json").end(reports.encodePrettily());
            } else {
                ctx.fail(500);
            }
        });
    }

    public void getProjectReports(RoutingContext ctx) {
        String query = "SELECT * FROM project_reports ORDER BY report_date DESC LIMIT 10";
        dbPool.query(query).execute(ar -> {
            if (ar.succeeded()) {
                JsonArray reports = new JsonArray();
                ar.result().forEach(row -> {
                    reports.add(new JsonObject()
                            .put("reportId", row.getInteger("report_id"))
                            .put("reportDate", row.getString("report_date"))
                            .put("details", row.getString("details")));
                });
                ctx.response().putHeader("Content-Type", "application/json").end(reports.encodePrettily());
            } else {
                ctx.fail(500);
            }
        });
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
}
