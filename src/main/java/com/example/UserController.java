    package com.example;

    import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;


    public class UserController {
    private final PgPool dbClient;

    public UserController(PgPool dbClient) {
    this.dbClient = dbClient;
    }

    public void getAllUsers(RoutingContext ctx) {
        // Get role parameter from query
        String role = ctx.request().getParam("role");
        
        String query;
        Tuple parameters;
        
        if (role != null && !role.isEmpty()) {
            // Filter by role if parameter exists
            query = "SELECT user_id, full_name, email, role FROM users WHERE role = $1 ORDER BY user_id";
            parameters = Tuple.of(role);
        } else {
            // Get all users if no filter
            query = "SELECT user_id, full_name, email, role FROM users ORDER BY user_id";
            parameters = Tuple.tuple();
        }
        
        dbClient.preparedQuery(query)
            .execute(parameters)
            .onSuccess(rows -> {
                JsonArray users = new JsonArray();
                rows.forEach(row -> users.add(row.toJson()));
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(users.encode());
            })
            .onFailure(err -> {
                System.err.println("Database error: " + err.getMessage());
                ctx.fail(500, err);
            });
    }

    public void createUser(RoutingContext ctx) {
    JsonObject user = ctx.getBodyAsJson();
    dbClient.preparedQuery(
        "INSERT INTO users (full_name, email, password, role) VALUES ($1, $2, $3, $4) RETURNING user_id, full_name, email, role")
        .execute(Tuple.of(
            user.getString("full_name"),
            user.getString("email"),
            user.getString("password"),
            user.getString("role")))
        .onSuccess(rows -> {
            ctx.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(rows.iterator().next().toJson().encode());
        })
        .onFailure(err -> ctx.fail(500, err));
    }

    public void updateUser(RoutingContext ctx) {
    String userIdParam = ctx.pathParam("user_id"); // Get as String first
    JsonObject user = ctx.getBodyAsJson();

    if (userIdParam == null || userIdParam.isEmpty()) {
        ctx.response().setStatusCode(400).end("Missing user_id");
        return;
    }

    try {
        // Convert String to Integer (or Long if needed)
        Integer userId = Integer.parseInt(userIdParam);
        
        dbClient.preparedQuery(
            "UPDATE users SET full_name = $1, email = $2, role = $3 WHERE user_id = $4 RETURNING user_id, full_name, email, role")
            .execute(Tuple.of(
                user.getString("full_name"),
                user.getString("email"),
                user.getString("role"),
                userId))  // Now passing a Number (Integer)
            .onSuccess(rows -> {
                if (rows.size() == 0) {
                    ctx.response().setStatusCode(404).end("User not found");
                } else {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(rows.iterator().next().toJson().encode());
                }
            })
            .onFailure(err -> {
                ctx.response().setStatusCode(500).end("Database error: " + err.getMessage());
            });
    } catch (NumberFormatException e) {
        ctx.response().setStatusCode(400).end("user_id must be a number");
    }
    }
    public void deleteUser(RoutingContext ctx) {
    String userIdParam = ctx.pathParam("user_id");

    // Validate input
    if (userIdParam == null || userIdParam.isEmpty()) {
        ctx.response()
            .setStatusCode(400)
            .end("Missing user_id parameter");
        return;
    }

    try {
        // Convert to number
        int userId = Integer.parseInt(userIdParam);
        
        dbClient.preparedQuery("DELETE FROM users WHERE user_id = $1")
            .execute(Tuple.of(userId))
            .onSuccess(rows -> {
                if (rows.rowCount() == 0) {
                    ctx.response()
                        .setStatusCode(404)
                        .end("User not found");
                } else {
                    ctx.response()
                        .setStatusCode(204)
                        .end();
                }
            })
            .onFailure(err -> {
                System.err.println("Database error: " + err.getMessage()); // Log the actual error
                ctx.response()
                    .setStatusCode(500)
                    .end("Database operation failed: " + err.getMessage()); // Return specific error
            });
    } catch (NumberFormatException e) {
        ctx.response()
            .setStatusCode(400)
            .end("Invalid user_id format - must be a number");
    }
    }}