package com.example;

import java.time.LocalDate;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;

public class ProjectController {
    private final PgPool dbClient;

    public ProjectController(PgPool dbClient) {
        this.dbClient = dbClient;
    }

    public void getAllProjects(RoutingContext ctx) {
        dbClient.query("SELECT project_id, name, description, status, deadline, project_manager_id, created_at FROM projects ORDER BY project_id")
            .execute()
            .onSuccess(rows -> {
                JsonArray projects = new JsonArray();
                rows.forEach(row -> projects.add(row.toJson()));
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(projects.encode());
            })
            .onFailure(err -> {
                System.err.println("Database error: " + err.getMessage());
                ctx.fail(500, err);
            });
    }

    public void createProject(RoutingContext ctx) {
        JsonObject project = ctx.getBodyAsJson();
                    // Parse deadline from String to LocalDate if present
                    LocalDate deadline = project.getString("deadline") != null ? 
                        LocalDate.parse(project.getString("deadline")) : 
                        null;
                    
        
        dbClient.preparedQuery(
            "INSERT INTO projects (name, description, status, deadline, project_manager_id) " +
            "VALUES ($1, $2, $3, $4, $5) " +
            "RETURNING project_id, name, description, status, deadline, project_manager_id, created_at")
            .execute(Tuple.of(
                project.getString("name"),
                project.getString("description", ""),
                project.getString("status", "Active"),
                deadline,
                project.getInteger("project_manager_id"))) // Accepts null
            .onSuccess(rows -> {
                ctx.response()
                    .setStatusCode(201)
                    .putHeader("Content-Type", "application/json")
                    .end(rows.iterator().next().toJson().encode());
            })
            .onFailure(err -> {
                System.err.println("Create project error: " + err.getMessage());
                ctx.fail(500, err);
            });
    }

    public void updateProject(RoutingContext ctx) {
        String projectIdParam = ctx.pathParam("project_id");
        JsonObject project = ctx.getBodyAsJson();

        if (projectIdParam == null || projectIdParam.isEmpty()) {
            ctx.response().setStatusCode(400).end("Missing project_id");
            return;
        }

        try {

            int projectId = Integer.parseInt(projectIdParam);
            LocalDate deadline = project.getString("deadline") != null ? 
                LocalDate.parse(project.getString("deadline")) : 
                null;
            
            dbClient.preparedQuery(
                "UPDATE projects SET " +
                "name = $1, description = $2, status = $3, " +
                "deadline = $4, project_manager_id = $5 " +
                "WHERE project_id = $6 " +
                "RETURNING project_id, name, description, status, deadline, project_manager_id, created_at")
                .execute(Tuple.of(
                    project.getString("name"),
                    project.getString("description", ""),
                    project.getString("status", "Active"),
                    deadline,
                    project.getInteger("project_manager_id"),
                    projectId))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        ctx.response().setStatusCode(404).end("Project not found");
                    } else {
                        ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(rows.iterator().next().toJson().encode());
                    }
                })
                .onFailure(err -> {
                    ctx.response()
                        .setStatusCode(500)
                        .end("Database error: " + err.getMessage());
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("project_id must be a number");
        }
    }
    public void handleProjectStatus(RoutingContext ctx) {
        dbClient.preparedQuery("SELECT status, COUNT(*) as count FROM projects GROUP BY status")
            .execute()
            .onSuccess(rows -> {
                JsonObject result = new JsonObject();
                for (Row row : rows) {
                    String status = row.getString("status");
                    Integer count = row.getInteger("count");
                    if (status != null && count != null) {
                        result.put(status, count);
                    }
                }
                ctx.response()
                   .putHeader("Content-Type", "application/json")
                   .end(result.encode());
            })
            .onFailure(err -> {
                ctx.response()
                   .setStatusCode(500)
                   .putHeader("Content-Type", "application/json")
                   .end(new JsonObject()
                       .put("error", "Failed to fetch project status")
                       .put("details", err.getMessage())
                       .encode());
            });
    }
    public void deleteProject(RoutingContext ctx) {
        String projectIdParam = ctx.pathParam("project_id");

        if (projectIdParam == null || projectIdParam.isEmpty()) {
            ctx.response().setStatusCode(400).end("Missing project_id");
            return;
        }

        try {
            int projectId = Integer.parseInt(projectIdParam);
            
            dbClient.preparedQuery("DELETE FROM projects WHERE project_id = $1")
                .execute(Tuple.of(projectId))
                .onSuccess(rows -> {
                    if (rows.rowCount() == 0) {
                        ctx.response().setStatusCode(404).end("Project not found");
                    } else {
                        ctx.response().setStatusCode(204).end();
                    }
                })
                .onFailure(err -> {
                    ctx.response()
                        .setStatusCode(500)
                        .end("Database error: " + err.getMessage());
                });
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("project_id must be a number");
        }
    }

    public void getProjectsByPm(RoutingContext ctx) {
        try {
            int pmId = Integer.parseInt(ctx.request().getParam("pm"));
            
            String query = """
                SELECT p.*, 
                       COUNT(t.task_id) as total_tasks,
                       SUM(CASE WHEN t.status = 'Done' THEN 1 ELSE 0 END) as completed_tasks,
                       (SELECT COUNT(*) FROM project_teams WHERE project_id = p.project_id) as team_count,
                       (p.deadline - CURRENT_DATE) as days_remaining,
                       (CASE 
                          WHEN p.status = 'Completed' THEN 100
                          WHEN COUNT(t.task_id) = 0 THEN 0
                          ELSE (SUM(CASE WHEN t.status = 'Done' THEN 1 ELSE 0 END) * 100.0 / COUNT(t.task_id))
                        END) as completion_percentage,
                       EXTRACT(DAY FROM (p.deadline - p.created_at)) as planned_duration,
                       EXTRACT(DAY FROM (CURRENT_DATE - p.created_at)) as elapsed_days
                FROM projects p
                LEFT JOIN tasks t ON p.project_id = t.project_id
                WHERE p.project_manager_id = ?
                GROUP BY p.project_id
                ORDER BY p.created_at DESC
                """;
            
            dbClient.preparedQuery(query)
                .execute(Tuple.of(pmId))
                .onSuccess(rows -> {
                    JsonArray projects = new JsonArray();
                    rows.forEach(row -> {
                        JsonObject project = row.toJson();
                        // Calculate risk level
                        project.put("risk_level", calculateRiskLevel(project));
                        projects.add(project);
                    });
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(projects.encode());
                })
                .onFailure(err -> ctx.fail(500, err));
        } catch (NumberFormatException e) {
            ctx.response().setStatusCode(400).end("Invalid PM ID format");
        }
    }

    private String calculateRiskLevel(JsonObject project) {
        if ("Completed".equals(project.getString("status"))) {
            return "None";
        }
        
        int daysRemaining = project.getInteger("days_remaining", 0);
        double completionPercentage = project.getDouble("completion_percentage", 0.0);
        
        if (daysRemaining < 0) {
            return "Critical";
        }
        
        double requiredDailyProgress = (100 - completionPercentage) / Math.max(1, daysRemaining);
        
        if (requiredDailyProgress > 5) {
            return "High";
        } else if (requiredDailyProgress > 2) {
            return "Medium";
        } else if (completionPercentage < 30 && daysRemaining < 14) {
            return "Medium";
        } else {
            return "Low";
        }
    }
    
}