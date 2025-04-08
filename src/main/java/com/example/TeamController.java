package com.example;

import java.util.Arrays;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;

public class TeamController {
    private final PgPool dbClient;

    public TeamController(PgPool dbClient) {
        this.dbClient = dbClient;
    }

    public void getTeamProjects(RoutingContext ctx) {
        // 1. Enable detailed request logging
        System.out.println("\n=== NEW REQUEST ===");
        System.out.println("Method: " + ctx.request().method());
        System.out.println("Path: " + ctx.request().path());
        System.out.println("Full URI: " + ctx.request().absoluteURI());
        System.out.println("Query Params: " + ctx.request().params().entries());
    
        try {
            // 2. Validate and parse parameters
            String teamIdsParam = ctx.request().getParam("teamIds");
            System.out.println("Raw teamIds: '" + teamIdsParam + "'");
    
            if (teamIdsParam == null || teamIdsParam.isEmpty()) {
                System.err.println("ERROR: Missing teamIds parameter");
                sendError(ctx, 400, "Missing teamIds parameter");
                return;
            }
    
            // 3. Convert to PostgreSQL compatible array
            Integer[] teamIds = Arrays.stream(teamIdsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .toArray(Integer[]::new);
    
            System.out.println("Parsed teamIds: " + Arrays.toString(teamIds));
    
            // 4. Debug the SQL query
            String sql = """
                SELECT t.team_id, p.name as project_name
                FROM teams t
                LEFT JOIN projects p ON t.project_id = p.project_id
                WHERE t.team_id = ANY($1)
                """;
            System.out.println("Executing SQL:\n" + sql);
            System.out.println("With parameters: " + Arrays.toString(teamIds));
    
            // 5. Execute query with enhanced logging
            dbClient.preparedQuery(sql)
                .execute(Tuple.of(teamIds))
                .onSuccess(rows -> {
                    System.out.println("Query succeeded. Found " + rows.size() + " rows");
                    
                    JsonArray result = new JsonArray();
                    rows.forEach(row -> {
                        JsonObject item = new JsonObject()
                            .put("team_id", row.getInteger("team_id"))
                            .put("project", row.getString("project_name"));
                        result.add(item);
                        
                        // Debug each row
                        System.out.println("Row: " + item.encode());
                    });
    
                    System.out.println("Final response: " + result.encode());
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(result.encode());
                })
                .onFailure(err -> {
                    System.err.println("DATABASE ERROR: " + err.getMessage());
                    err.printStackTrace();
                    sendError(ctx, 500, "Database error: " + err.getMessage());
                });
    
        } catch (Exception e) {
            System.err.println("UNEXPECTED ERROR: " + e.getMessage());
            e.printStackTrace();
            sendError(ctx, 500, "Internal server error");
        }
    }

    public void getAllTeams(RoutingContext ctx) {
        dbClient.query("SELECT team_id, name, description, project_id, created_at FROM teams ORDER BY team_id")
            .execute()
            .onSuccess(rows -> {
                JsonArray teams = new JsonArray();
                rows.forEach(row -> teams.add(row.toJson()));
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(teams.encode());
            })
            .onFailure(err -> {
                System.err.println("Database error: " + err.getMessage());
                ctx.fail(500, err);
            });
    }

    public void createTeam(RoutingContext ctx) {
        JsonObject team = ctx.getBodyAsJson();
        
        // Validate required fields
        if (team.getString("name") == null) {
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Name is required").encode());
            return;
        }
        
        // Handle project_id - either get it or use null
        Integer projectId = team.containsKey("project_id") ? team.getInteger("project_id") : null;
        
        dbClient.preparedQuery(
            "INSERT INTO teams (name, description, project_id) " +
            "VALUES ($1, $2, $3) " +
            "RETURNING team_id, name, description, project_id, created_at")
            .execute(Tuple.of(
                team.getString("name"),
                team.getString("description", ""),
                projectId))  // Use the nullable projectId
            .onSuccess(rows -> {
                ctx.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(rows.iterator().next().toJson().encode());
            })
            .onFailure(err -> {
                System.err.println("Create team error: " + err.getMessage());
                ctx.fail(500, err);
            });
    }

    public void updateTeam(RoutingContext ctx) {
        String teamIdParam = ctx.pathParam("team_id");
        JsonObject team = ctx.getBodyAsJson();
    
        if (teamIdParam == null || teamIdParam.isEmpty()) {
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Missing team_id").encode());
            return;
        }
    
        try {
            int teamId = Integer.parseInt(teamIdParam);
    
            dbClient.preparedQuery(
                "UPDATE teams SET " +
                "name = $1, description = $2, project_id = $3 " +
                "WHERE team_id = $4 " +
                "RETURNING team_id, name, description, project_id, created_at")
                .execute(Tuple.of(
                    team.getString("name"),
                    team.getString("description", ""),
                    team.getInteger("project_id"),
                    teamId))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        ctx.response()
                            .setStatusCode(404)
                            .end(new JsonObject().put("error", "Team not found").encode());
                    } else {
                        ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(rows.iterator().next().toJson().encode());
                    }
                })
                .onFailure(err -> {
                    ctx.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                            .put("error", "Database error")
                            .put("details", err.getMessage())
                            .encode());
                });
        } catch (NumberFormatException e) {
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "team_id must be a number")
                    .encode());
        }
    }

    public void deleteTeam(RoutingContext ctx) {
        String teamIdParam = ctx.pathParam("team_id");
        
        if (teamIdParam == null || teamIdParam.isEmpty()) {
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Missing team_id")
                    .encode());
            return;
        }

        try {
            int teamId = Integer.parseInt(teamIdParam);
            
            dbClient.preparedQuery("DELETE FROM teams WHERE team_id = $1 RETURNING team_id")
                .execute(Tuple.of(teamId))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        ctx.response()
                            .setStatusCode(404)
                            .end(new JsonObject()
                                .put("error", "Team not found")
                                .encode());
                    } else {
                        ctx.response()
                            .setStatusCode(204)
                            .end();
                    }
                })
                .onFailure(err -> {
                    ctx.response()
                        .setStatusCode(500)
                        .end(new JsonObject()
                            .put("error", "Database error")
                            .put("details", err.getMessage())
                            .encode());
                });
        } catch (NumberFormatException e) {
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Invalid team ID format")
                    .encode());
        }
    }

    public void getTeamDashboard(RoutingContext ctx) {
        // Get user from session
        JsonObject user = ctx.session().get("user");
        Integer userId = user.getInteger("user_id");
        
        dbClient.preparedQuery(
            "SELECT t.team_id, t.team_name, " +
            "COUNT(DISTINCT tm.user_id) as member_count, " +
            "COUNT(DISTINCT p.project_id) as project_count " +
            "FROM teams t " +
            "LEFT JOIN team_members tm ON t.team_id = tm.team_id " +
            "LEFT JOIN projects p ON t.team_id = p.team_id " +
            "WHERE tm.user_id = $1 " +
            "GROUP BY t.team_id")
            .execute(Tuple.of(userId))
            .onSuccess(rows -> {
                JsonArray result = new JsonArray();
                rows.forEach(row -> result.add(row.toJson()));
                ctx.response()
                   .putHeader("Content-Type", "application/json")
                   .end(result.encode());
            })
            .onFailure(err -> {
                ctx.fail(500, err);
            });
    }

    // Team Members Endpoints
    

    public void addTeamMember(RoutingContext ctx) {
        String teamIdParam = ctx.pathParam("team_id");
        JsonObject member = ctx.getBodyAsJson();
        
        if (teamIdParam == null || teamIdParam.isEmpty()) {
            ctx.response().setStatusCode(400).end("Missing team_id");
            return;
        }

        try {
            int teamId = Integer.parseInt(teamIdParam);
            
            dbClient.preparedQuery(
                "INSERT INTO team_members (team_id, user_id, role) " +
                "VALUES ($1, $2, $3) " +
                "RETURNING team_id, user_id, role")
                .execute(Tuple.of(
                    teamId,
                    member.getInteger("user_id"),
                    member.getString("role", "Member")))
                .onSuccess(rows -> {
                    ctx.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(rows.iterator().next().toJson().encode());
                })
                .onFailure(err -> {
                    System.err.println("Add member error: " + err.getMessage());
                    ctx.fail(500, err);
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("team_id must be a number");
        }
    }

    public void updateTeamMemberRole(RoutingContext ctx) {
        String teamIdParam = ctx.pathParam("team_id");
        String userIdParam = ctx.pathParam("user_id");
        JsonObject member = ctx.getBodyAsJson();
        
        if (teamIdParam == null || userIdParam == null) {
            ctx.response().setStatusCode(400).end("Missing team_id or user_id");
            return;
        }

        try {
            int teamId = Integer.parseInt(teamIdParam);
            int userId = Integer.parseInt(userIdParam);
            
            dbClient.preparedQuery(
                "UPDATE team_members SET role = $1 " +
                "WHERE team_id = $2 AND user_id = $3 " +
                "RETURNING team_id, user_id, role")
                .execute(Tuple.of(
                    member.getString("role"),
                    teamId,
                    userId))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        ctx.response()
                            .setStatusCode(404)
                            .end(new JsonObject().put("error", "Member not found").encode());
                    } else {
                        ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(rows.iterator().next().toJson().encode());
                    }
                })
                .onFailure(err -> {
                    ctx.fail(500, err);
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("team_id and user_id must be numbers");
        }
    }

    public void removeTeamMember(RoutingContext ctx) {
        String teamIdParam = ctx.pathParam("team_id");
        String userIdParam = ctx.pathParam("user_id");
        
        if (teamIdParam == null || userIdParam == null) {
            ctx.response().setStatusCode(400).end("Missing team_id or user_id");
            return;
        }

        try {
            int teamId = Integer.parseInt(teamIdParam);
            int userId = Integer.parseInt(userIdParam);
            
            dbClient.preparedQuery(
                "DELETE FROM team_members " +
                "WHERE team_id = $1 AND user_id = $2 " +
                "RETURNING team_id, user_id")
                .execute(Tuple.of(teamId, userId))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        ctx.response()
                            .setStatusCode(404)
                            .end(new JsonObject().put("error", "Member not found").encode());
                    } else {
                        ctx.response()
                            .setStatusCode(204)
                            .end();
                    }
                })
                .onFailure(err -> {
                    ctx.fail(500, err);
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("team_id and user_id must be numbers");
        }
    }

    // Get teams by project
    public void getTeamsByProject(RoutingContext ctx) {
        String projectIdParam = ctx.pathParam("project_id");

        if (projectIdParam == null || projectIdParam.isEmpty()) {
            ctx.response().setStatusCode(400).end("Missing project_id");
            return;
        }

        try {
            int projectId = Integer.parseInt(projectIdParam);
            
            dbClient.preparedQuery(
                "SELECT team_id, name, description FROM teams " +
                "WHERE project_id = $1 ORDER BY name")
                .execute(Tuple.of(projectId))
                .onSuccess(rows -> {
                    JsonArray teams = new JsonArray();
                    rows.forEach(row -> teams.add(row.toJson()));
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(teams.encode());
                })
                .onFailure(err -> {
                    ctx.fail(500, err);
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("project_id must be a number");
        }
    }

    public void getUserTeams(RoutingContext ctx) {
        System.out.println("--- START getUserTeams ---");
        String username = ctx.request().getParam("username");
        System.out.println("Username parameter: " + username);
        
        if (username == null || username.isEmpty()) {
            System.out.println("Username parameter missing");
            ctx.response().setStatusCode(400).end("Username parameter is required");
            return;
        }
    
        System.out.println("Executing user query...");
        dbClient.preparedQuery("SELECT user_id FROM users WHERE full_name = $1")
            .execute(Tuple.of(username))
            .onSuccess(userRes -> {
                System.out.println("User query completed. Rows: " + userRes.size());
                
                if (userRes.size() == 0) {
                    System.out.println("No user found with username: " + username);
                    ctx.response().setStatusCode(404).end("User not found");
                    return;
                }
                
                int userId = userRes.iterator().next().getInteger("user_id");
                System.out.println("Found user_id: " + userId);
                
                System.out.println("Executing teams query...");
                dbClient.preparedQuery(
                    "SELECT t.team_id, t.name, t.description, tm.role " +
                    "FROM teams t JOIN team_members tm ON t.team_id = tm.team_id " +
                    "WHERE tm.user_id = $1")
                    .execute(Tuple.of(userId))
                    .onSuccess(teamRes -> {
                        System.out.println("Teams query completed. Rows: " + teamRes.size());
                        JsonArray teams = new JsonArray();
                        teamRes.forEach(row -> teams.add(row.toJson()));
                        ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(teams.encode());
                    })
                    .onFailure(err -> {
                        System.err.println("Teams query failed: " + err.getMessage());
                        ctx.response().setStatusCode(500).end("Database error");
                    });
            })
            .onFailure(err -> {
                System.err.println("User query failed: " + err.getMessage());
                ctx.response().setStatusCode(500).end("Database error");
            });
    }

    // Add this new handler to your router

    public void getTeamMembers(RoutingContext ctx) {
        // Debug incoming request
        System.out.println("Received request for team members. Path params: " + ctx.pathParams());
        
        try {
            // 1. Validate team_id
            String teamIdParam = ctx.pathParam("team_id");
            System.out.println("Raw team_id parameter: " + teamIdParam);
            
            if (teamIdParam == null || teamIdParam.isEmpty()) {
                System.err.println("Missing team_id parameter");
                sendError(ctx, 400, "Missing team_id parameter");
                return;
            }
    
            // 2. Parse team_id
            int teamId;
            try {
                teamId = Integer.parseInt(teamIdParam);
                System.out.println("Parsed team_id: " + teamId);
            } catch (NumberFormatException e) {
                System.err.println("Invalid team_id format: " + teamIdParam);
                sendError(ctx, 400, "Invalid team_id. Must be a number.");
                return;
            }
    
            // 3. Query database
            System.out.println("Querying database for team_id: " + teamId);
            dbClient.preparedQuery("""
                SELECT 
                    u.user_id as id,
                    u.full_name as name,
                    u.email,
                    tm.role
                FROM team_members tm
                JOIN users u ON tm.user_id = u.user_id
                WHERE tm.team_id = $1
                ORDER BY  tm.role
                """)
                .execute(Tuple.of(teamId))
                .onSuccess(rows -> {
                    System.out.println("Found " + rows.size() + " members");
                    
                    if (rows.size() == 0) {
                        System.out.println("No members found for team_id: " + teamId);
                        ctx.response()
                            .setStatusCode(404)
                            .end(new JsonObject()
                                .put("message", "No members found for this team")
                                .encode());
                        return;
                    }
    
                    JsonArray members = new JsonArray();
                    rows.forEach(row -> {
                        members.add(new JsonObject()
                            .put("id", row.getInteger("id"))
                            .put("name", row.getString("name"))
                            .put("email", row.getString("email"))
                            .put("role", row.getString("role")));
                    });
                    
                    System.out.println("Sending response for team_id: " + teamId);
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(members.encode());
                })
                .onFailure(err -> {
                    System.err.println("Database error for team_id " + teamId + ": " + err.getMessage());
                    sendError(ctx, 500, "Database error: " + err.getMessage());
                });
    
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            sendError(ctx, 500, "Internal server error");
        }
    }
    
    private void sendError(RoutingContext ctx, int statusCode, String message) {
        System.err.println("Sending error: " + statusCode + " - " + message);
        ctx.response()
            .setStatusCode(statusCode)
            .end(new JsonObject().put("error", message).encode());
    }
    
}