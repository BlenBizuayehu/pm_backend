package com.example;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;


public class TaskController {
    private final PgPool dbClient;
    private final Vertx vertx;

    public TaskController(PgPool dbClient, Vertx vertx) {
        this.dbClient = dbClient;
        this.vertx = vertx;
    }

    public void getAllTasks(RoutingContext ctx) {
        dbClient.query("SELECT task_id, title, description, status, deadline, project_id, assigned_to, created_at FROM tasks ORDER BY task_id")
            .execute()
            .onSuccess(rows -> {
                JsonArray tasks = new JsonArray();
                rows.forEach(row -> tasks.add(row.toJson()));
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(tasks.encode());
            })
            .onFailure(err -> {
                System.err.println("Database error: " + err.getMessage());
                ctx.fail(500, err);
            });
    }

    public void createTask(RoutingContext ctx) {
        JsonObject task = ctx.getBodyAsJson();
        
        try {
            // Parse the date string from frontend
            LocalDate deadline = task.getString("deadline") != null ?
                LocalDate.parse(task.getString("deadline")) :
                null;
                
            dbClient.preparedQuery(
                "INSERT INTO tasks (title, description, status, deadline, project_id, assigned_to) " +  // Removed trailing comma
                "VALUES ($1, $2, $3, $4, $5, $6) " +
                "RETURNING task_id, title, description, status, deadline, project_id, assigned_to, created_at")  // Removed trailing comma
                .execute(Tuple.of(
                    task.getString("title"),
                    task.getString("description", ""),
                    task.getString("status", "Pending"),
                    deadline,
                    task.getInteger("project_id"),
                    task.getInteger("assigned_to")))
                .onSuccess(rows -> {
                    ctx.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(rows.iterator().next().toJson().encode());
                })
                .onFailure(err -> {
                    System.err.println("Create task error: " + err.getMessage());
                    ctx.fail(500, err);
                });
        } catch (DateTimeParseException e) {
            ctx.response()
                .setStatusCode(400)
                .end("Invalid date format. Use YYYY-MM-DD");
        }
    }
    

    // In your DashboardController.java
public void getTeamDashboard(RoutingContext ctx) {
    // 1. Verify authentication (if needed)
    String authHeader = ctx.request().getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        ctx.response().setStatusCode(401).end("Unauthorized");
        return;
    }

    // 2. Extract user ID from token or request
    String userIdParam = ctx.request().getParam("user_id");
    
    // 3. Query database for team dashboard data
    dbClient.preparedQuery(
        "SELECT t.team_id, t.team_name, " +
        "COUNT(DISTINCT tm.user_id) as member_count, " +
        "COUNT(DISTINCT p.project_id) as project_count " +
        "FROM teams t " +
        "LEFT JOIN team_members tm ON t.team_id = tm.team_id " +
        "LEFT JOIN projects p ON t.team_id = p.team_id " +
        "WHERE tm.user_id = $1 " +  // Only teams the user belongs to
        "GROUP BY t.team_id")
        .execute(Tuple.of(userIdParam))
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
    public void updateTask(RoutingContext ctx) {
        String taskIdParam = ctx.pathParam("task_id");
        JsonObject task = ctx.getBodyAsJson();
    
        if (taskIdParam == null || taskIdParam.isEmpty()) {
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject().put("error", "Missing task_id").encode());
            return;
        }
    
        try {
            int taskId = Integer.parseInt(taskIdParam);
            
            // Parse deadline - handle both String and null values
            Object deadlineValue = task.getValue("deadline");
            LocalDate deadline = null;
            
            if (deadlineValue != null) {
                try {
                    if (deadlineValue instanceof String) {
                        deadline = LocalDate.parse((String) deadlineValue);
                    } else if (deadlineValue instanceof LocalDate) {
                        deadline = (LocalDate) deadlineValue;
                    }
                } catch (DateTimeParseException e) {
                    ctx.response()
                        .setStatusCode(400)
                        .end(new JsonObject()
                            .put("error", "Invalid deadline format. Expected YYYY-MM-DD")
                            .encode());
                    return;
                }
            }
    
            dbClient.preparedQuery(
                "UPDATE tasks SET " +
                "title = $1, description = $2, status = $3, " +
                "deadline = $4, project_id = $5, assigned_to = $6 " +
                "WHERE task_id = $7 " +
                "RETURNING task_id, title, description, status, deadline, project_id, assigned_to, created_at")
                .execute(Tuple.of(
                    task.getString("title"),
                    task.getString("description", ""),
                    task.getString("status", "Pending"),
                    deadline,  // Use parsed LocalDate
                    task.getInteger("project_id"),
                    task.getInteger("assigned_to"),  // Changed from assignee_id to assigned_to
                    taskId))
                .onSuccess(rows -> {
                    if (rows.size() == 0) {
                        ctx.response()
                            .setStatusCode(404)
                            .end(new JsonObject().put("error", "Task not found").encode());
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
                    .put("error", "task_id must be a number")
                    .encode());
        }
  }
  public void handleTaskStatus(RoutingContext ctx) {
    dbClient.preparedQuery("SELECT status, COUNT(*) as count FROM tasks GROUP BY status")
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
                   .put("error", "Failed to fetch task status")
                   .put("details", err.getMessage())
                   .encode());
        });
}
  public void deleteTask(RoutingContext ctx) {
    String taskIdParam = ctx.pathParam("task_id");
    
    if (taskIdParam == null || taskIdParam.isEmpty()) {
        ctx.response()
            .setStatusCode(400)
            .end(new JsonObject()
                .put("error", "Missing task_id")
                .encode());
        return;
    }

    try {
        int taskId = Integer.parseInt(taskIdParam);
        
        dbClient.preparedQuery("DELETE FROM tasks WHERE task_id = $1 RETURNING task_id")
            .execute(Tuple.of(taskId))
            .onSuccess(rows -> {
                if (rows.size() == 0) {
                    ctx.response()
                        .setStatusCode(404)
                        .end(new JsonObject()
                            .put("error", "Task not found")
                            .encode());
                } else {
                    ctx.response()
                        .setStatusCode(204) // 204 No Content is standard for successful DELETE
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
                .put("error", "Invalid task ID format")
                .encode());
    }
}

public void updateTaskStatus(RoutingContext ctx) {
    String taskIdParam = ctx.pathParam("task_id");
    JsonObject updateData = ctx.getBodyAsJson();
    
    if (taskIdParam == null || taskIdParam.isEmpty()) {
        ctx.response()
            .setStatusCode(400)
            .end(new JsonObject().put("error", "Missing task_id").encode());
        return;
    }
    
    if (updateData == null || !updateData.containsKey("status")) {
        ctx.response()
            .setStatusCode(400)
            .end(new JsonObject().put("error", "Missing status in request body").encode());
        return;
    }

    try {
        int taskId = Integer.parseInt(taskIdParam);
        String newStatus = updateData.getString("status");
        
        // Validate status is one of allowed values
        List<String> allowedStatuses = Arrays.asList("To Do", "In Progress", "Done", "Blocked");
        if (!allowedStatuses.contains(newStatus)) {
            ctx.response()
                .setStatusCode(400)
                .end(new JsonObject()
                    .put("error", "Invalid status value")
                    .put("allowed_values", allowedStatuses)
                    .encode());
            return;
        }

        dbClient.preparedQuery(
            "UPDATE tasks SET status = $1 WHERE task_id = $2 " +
            "RETURNING task_id, status")
            .execute(Tuple.of(newStatus, taskId))
            .onSuccess(rows -> {
                if (rows.size() == 0) {
                    ctx.response()
                        .setStatusCode(404)
                        .end(new JsonObject().put("error", "Task not found").encode());
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
                .put("error", "task_id must be a number")
                .encode());
    }
}

public void getTasksByProject(RoutingContext ctx) {
    try {
        int projectId = Integer.parseInt(ctx.pathParam("project_id"));
        
        dbClient.preparedQuery("""
            SELECT t.task_id, t.title, t.description, t.status, 
                   t.priority, t.deadline, t.assigned_to
            FROM tasks t
            WHERE t.project_id = $1
            ORDER BY 
                CASE t.status
                    WHEN 'Not Started' THEN 1
                    WHEN 'In Progress' THEN 2
                    WHEN 'Completed' THEN 3
                    ELSE 4
                END,
                CASE t.priority
                    WHEN 'High' THEN 1
                    WHEN 'Medium' THEN 2
                    WHEN 'Low' THEN 3
                    ELSE 4
                END
            """)
            .execute(Tuple.of(projectId))
            .onSuccess(rows -> {
                JsonArray tasks = new JsonArray();
                rows.forEach(row -> {
                    JsonObject task = row.toJson();
                    // Rename deadline to due_date if needed for frontend
                    // task.put("due_date", task.getValue("deadline"));
                    tasks.add(task);
                });
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(tasks.encode());
            })
            .onFailure(err -> ctx.fail(500, err));
    } catch (NumberFormatException e) {
        ctx.response().setStatusCode(400).end("Invalid project ID");
    }
}

    public void handleGetTeamProjects(RoutingContext ctx) {
        try {
            String[] teamIds = ctx.request().getParam("teamIds").split(",");
            Integer[] ids = Arrays.stream(teamIds)
                .map(Integer::parseInt)
                .toArray(Integer[]::new);
            
            dbClient.preparedQuery("""
                SELECT t.team_id, p.name as project_name
                FROM team t
                LEFT JOIN project p ON t.project_id = p.project_id
                WHERE t.team_id = ANY($1)
                """)
                .execute(Tuple.of(ids))
                .onSuccess(rows -> {
                    JsonArray projects = new JsonArray();
                    rows.forEach(row -> projects.add(new JsonObject()
                        .put("team_id", row.getInteger("team_id"))
                        .put("project", row.getString("project_name"))));
                    
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(projects.encode());
                })
                .onFailure(err -> ctx.fail(500, err));
        } catch (Exception e) {
            ctx.fail(400, new IllegalArgumentException("Invalid team IDs format"));
        }
    }

    public void getUserTasks(RoutingContext ctx) {
        String username = ctx.request().getParam("username");
        System.out.println("Received request for username: " + username);
    
        if (username == null || username.isEmpty()) {
            System.out.println("Username parameter missing");
            ctx.response().setStatusCode(400).end("Username parameter is required");
            return;
        }
    
        System.out.println("Querying user_id for: " + username);
        dbClient.preparedQuery("SELECT user_id FROM users WHERE full_name = $1")
            .execute(Tuple.of(username))
            .onSuccess(userRes -> {
                System.out.println("User query result size: " + userRes.size());
                
                if (userRes.size() == 0) {
                    System.out.println("User not found: " + username);
                    ctx.response().setStatusCode(404).end("User not found");
                    return;
                }
                
                int userId = userRes.iterator().next().getInteger("user_id");
                System.out.println("Found user_id: " + userId + " for username: " + username);
                
                System.out.println("Querying tasks for user_id: " + userId);
                dbClient.preparedQuery("SELECT * FROM tasks WHERE assigned_to = $1")
                    .execute(Tuple.of(userId))
                    .onSuccess(taskRes -> {
                        System.out.println("Found " + taskRes.size() + " tasks");
                        JsonArray tasks = new JsonArray();
                        taskRes.forEach(row -> tasks.add(row.toJson()));
                        ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(tasks.encode());
                    })
                    .onFailure(err -> {
                        System.err.println("Task query failed: " + err.getMessage());
                        ctx.response().setStatusCode(500).end("Database error");
                    });
            })
            .onFailure(err -> {
                System.err.println("User query failed: " + err.getMessage());
                ctx.response().setStatusCode(500).end("Database error");
            });
    }

    public void uploadTaskDocument(RoutingContext ctx) {
        String taskIdParam = ctx.pathParam("task_id");
        
        try {
            int taskId = Integer.parseInt(taskIdParam);
            
            if (ctx.fileUploads().isEmpty()) {
                ctx.response()
                    .setStatusCode(400)
                    .end(new JsonObject().put("error", "No file uploaded").encode());
                return;
            }
    
            FileUpload upload = ctx.fileUploads().iterator().next();
            String originalFilename = upload.fileName();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String newFilename = UUID.randomUUID() + fileExtension;
            
            // Use absolute path to avoid issues
            String fullPath = "uploads/" + newFilename;
            
            // First ensure uploads directory exists
            ctx.vertx().fileSystem().mkdirs("uploads", mkdirRes -> {
                if (mkdirRes.failed()) {
                    ctx.fail(500, mkdirRes.cause());
                    return;
                }
                
                // Now move the file
                ctx.vertx().fileSystem().move(upload.uploadedFileName(), fullPath, moveRes -> {
                    if (moveRes.failed()) {
                        ctx.fail(500, moveRes.cause());
                        return;
                    }
                    
                    // Update database
                    dbClient.preparedQuery(
                        "UPDATE tasks SET document_path = $1 WHERE task_id = $2 RETURNING *"
                    )
                    .execute(Tuple.of(newFilename, taskId))
                    .onSuccess(rows -> {
                        if (rows.size() == 0) {
                            ctx.response()
                                .setStatusCode(404)
                                .end(new JsonObject().put("error", "Task not found").encode());
                        } else {
                            ctx.response()
                                .putHeader("Content-Type", "application/json")
                                .end(rows.iterator().next().toJson().encode());
                        }
                    })
                    .onFailure(err -> ctx.fail(500, err));
                });
            });
        } catch (Exception e) {
            ctx.fail(400, e);
        }
    }

    // In your TaskController
// TaskController.java
// Add these methods to your TaskController

public void getAllTaskDocuments(RoutingContext ctx) {
    // Verify user is PM or Admin
    String userRole = ctx.user().get("role");
    if (!"pm".equals(userRole) && !"admin".equals(userRole)) {
        ctx.response()
           .setStatusCode(403)
           .end(new JsonObject().put("error", "Forbidden: Only PMs and Admins can access this").encode());
        return;
    }

    dbClient.preparedQuery("""
        SELECT t.task_id, t.title, u.full_name as assigned_to, 
               t.document_path, t.updated_at as last_upload
        FROM tasks t
        JOIN users u ON t.assigned_to = u.user_id
        WHERE t.document_path IS NOT NULL
        ORDER BY t.updated_at DESC
        """)
        .execute()
        .onSuccess(rows -> {
            JsonArray documents = new JsonArray();
            rows.forEach(row -> {
                JsonObject doc = new JsonObject()
                    .put("task_id", row.getInteger("task_id"))
                    .put("task_title", row.getString("title"))
                    .put("assigned_to", row.getString("assigned_to"))
                    .put("document_path", row.getString("document_path"))
                    .put("last_upload", row.getLocalDateTime("last_upload").toString());
                documents.add(doc);
            });
            ctx.response()
               .putHeader("Content-Type", "application/json")
               .end(documents.encode());
        })
        .onFailure(err -> {
            ctx.fail(500, err);
        });
}

public void getTaskDocuments(RoutingContext ctx) {
    String taskIdParam = ctx.pathParam("task_id");
    
    try {
        int taskId = Integer.parseInt(taskIdParam);
        
        dbClient.preparedQuery("""
            SELECT document_path
            FROM tasks
            WHERE task_id = $1 AND document_path IS NOT NULL
            """)
            .execute(Tuple.of(taskId))
            .onSuccess(rows -> {
                if (rows.size() == 0) {
                    ctx.response()
                       .setStatusCode(404)
                       .end(new JsonObject().put("message", "No documents found for this task").encode());
                    return;
                }
                
                Row row = rows.iterator().next();
                JsonObject response = new JsonObject()
                    .put("document_path", row.getString("document_path"))
                    .put("download_url", "/api/tasks/" + taskId + "/document/download");
                
                ctx.response()
                   .putHeader("Content-Type", "application/json")
                   .end(response.encode());
            })
            .onFailure(err -> ctx.fail(500, err));
    } catch (NumberFormatException e) {
        ctx.fail(400, e);
    }
}

public void getTaskDocumentInfo(RoutingContext ctx) {
    String taskIdParam = ctx.pathParam("task_id");
    
    try {
        int taskId = Integer.parseInt(taskIdParam);
        
        dbClient.preparedQuery("""
            SELECT document_path, 
                   LENGTH(document_path) as size,
                   CASE 
                       WHEN document_path LIKE '%.pdf' THEN 'application/pdf'
                       WHEN document_path LIKE '%.doc%' THEN 'application/msword'
                       WHEN document_path LIKE '%.xls%' THEN 'application/vnd.ms-excel'
                       WHEN document_path LIKE '%.jpg%' THEN 'image/jpeg'
                       WHEN document_path LIKE '%.png' THEN 'image/png'
                       ELSE 'application/octet-stream'
                   END as mime_type
            FROM tasks
            WHERE task_id = $1 AND document_path IS NOT NULL
            """)
            .execute(Tuple.of(taskId))
            .onSuccess(rows -> {
                if (rows.size() == 0) {
                    ctx.response()
                       .setStatusCode(200)
                       .end(new JsonObject().put("exists", false).encode());
                    return;
                }
                
                Row row = rows.iterator().next();
                String documentPath = row.getString("document_path");
                String filename = documentPath.substring(documentPath.lastIndexOf("/") + 1);
                
                JsonObject response = new JsonObject()
                    .put("exists", true)
                    .put("document_path", documentPath)
                    .put("filename", filename)
                    .put("size", row.getLong("size"))
                    .put("type", row.getString("mime_type"));
                
                ctx.response()
                   .putHeader("Content-Type", "application/json")
                   .end(response.encode());
            })
            .onFailure(err -> ctx.fail(500, err));
    } catch (NumberFormatException e) {
        ctx.fail(400, e);
    }
}
public void downloadTaskDocument(RoutingContext ctx) {
    String taskIdParam = ctx.pathParam("taskId");
    
    try {
        int taskId = Integer.parseInt(taskIdParam);
        
        dbClient.preparedQuery("SELECT document_path FROM tasks WHERE task_id = $1")
            .execute(Tuple.of(taskId))
            .onSuccess(rows -> {
                if (rows.size() == 0 || rows.iterator().next().getString("document_path") == null) {
                    ctx.response()
                       .setStatusCode(404)
                       .end(new JsonObject().put("error", "Document not found").encode());
                    return;
                }
                
                String filePath = "uploads/" + rows.iterator().next().getString("document_path");
                String filename = filePath.substring(filePath.lastIndexOf("/") + 1);
                
                ctx.response()
                   .putHeader("Content-Type", "application/octet-stream")
                   .putHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                   .sendFile(filePath);
            })
            .onFailure(err -> ctx.fail(500, err));
    } catch (Exception e) {
        ctx.fail(400, e);
    }
}

public void getTaskDocumentPath(RoutingContext ctx) {
    String taskIdParam = ctx.pathParam("task_id");
    
    try {
        int taskId = Integer.parseInt(taskIdParam);
        
        dbClient.preparedQuery("SELECT document_path FROM tasks WHERE task_id = $1")
            .execute(Tuple.of(taskId))
            .onSuccess(rows -> {
                if (rows.size() == 0) {
                    sendJsonResponse(ctx, 404, new JsonObject()
                        .put("success", false)
                        .put("message", "Task not found"));
                } else {
                    String documentPath = rows.iterator().next().getString("document_path");
                    if (documentPath == null || documentPath.isEmpty()) {
                        sendJsonResponse(ctx, 200, new JsonObject()
                            .put("success", true)
                            .put("hasDocument", false));
                    } else {
                        sendJsonResponse(ctx, 200, new JsonObject()
                            .put("success", true)
                            .put("hasDocument", true)
                            .put("documentPath", documentPath));
                    }
                }
            })
            .onFailure(err -> {
                sendJsonResponse(ctx, 500, new JsonObject()
                    .put("success", false)
                    .put("message", "Database error"));
            });
    } catch (NumberFormatException e) {
        sendJsonResponse(ctx, 400, new JsonObject()
            .put("success", false)
            .put("message", "Invalid task ID format"));
    }
}

private void sendJsonResponse(RoutingContext ctx, int statusCode, JsonObject json) {
    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("Content-Type", "application/json")
        .end(json.encode());
}

private void sendErrorResponse(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("Content-Type", "application/json")
        .end(new JsonObject().put("error", message).encode());
}


}