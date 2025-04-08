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
    boolean isProduction = false;
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
        TaskController taskController = new TaskController(dbClient);
        // 2. Test connection
        dbClient.query("SELECT 1").execute()
            .onSuccess(res -> {
                System.out.println("✅ PostgreSQL connection verified");
                
                // 3. Create router and handlers
                Router router = Router.router(vertx);
                AuthHandler authHandler = new AuthHandler(vertx, dbClient);
                UserController userController = new UserController(dbClient);
                ProjectController projectController = new ProjectController(dbClient);
                // ReportController reportController =new ReportController(dbClient);
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
               
    
    // Add after session handler configuration

                // OPTIONS handler

                router.route()
                .handler(CorsHandler.create("http://localhost:4200")
                    .allowedHeaders(Set.of("Content-Type", "Authorization"))
                    .allowCredentials(true));
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
                
                // Add to your router setup

router.get("/api/projects").handler(projectController::getAllProjects);
router.post("/api/projects").handler(projectController::createProject);
router.put("/api/projects/:project_id").handler(projectController::updateProject);
router.delete("/api/projects/:project_id").handler(projectController::deleteProject);

router.get("/api/tasks").handler(taskController::getAllTasks);
router.post("/api/tasks").handler(taskController::createTask);
router.put("/api/tasks/:task_id").handler(taskController::updateTask);
router.delete("/api/tasks/:task_id").handler(taskController::deleteTask);
router.get("/api/tasks/:project_id").handler(taskController::getTasksByProject);


TeamController teamController = new TeamController(dbClient);

// Teams routes
router.get("/api/teams").handler(teamController::getAllTeams);
router.post("/api/teams").handler(teamController::createTeam);
router.put("/api/teams/:team_id").handler(teamController::updateTeam);
router.delete("/api/teams/:team_id").handler(teamController::deleteTeam);
router.get("/api/projects/:project_id/teams").handler(teamController::getTeamsByProject);

// Team members routes
router.get("/api/teams/:team_id/members").handler(teamController::getTeamMembers);
router.post("/api/teams/:team_id/members").handler(teamController::addTeamMember);
router.put("/api/teams/:team_id/members/:user_id").handler(teamController::updateTeamMemberRole);
router.delete("/api/teams/:team_id/members/:user_id").handler(teamController::removeTeamMember);
router.get("/api/my_teams/:user_id").handler(teamController::getTeamDashboard);

router.get("/api/project-status").handler(projectController::handleProjectStatus);
router.get("/api/task-status").handler(taskController::handleTaskStatus);

router.get("/api/team-dashboard").handler(taskController::getTeamDashboard);

router.get("/api/user-tasks").handler(taskController::getUserTasks);
router.get("/api/user-teams").handler(teamController::getUserTeams);

router.get("/api/team-projects").handler(teamController::getTeamProjects);
// Add to your router initialization
// In your main server setup, before creating routes

router.patch("/api/tasks/:task_id/status")
    .handler(taskController::updateTaskStatus);


// router.get("/api/projects/:project_id/tasks").handler(this::getTasksByProject);
// router.get("/api/tasks/:project_id").handler(this::getTasksByProject);

// In your main Verticle (e.g., MainVerticle.java)
// router.get("/api/reports").handler(reportController::getAllReports);
// router.post("/api/reports").handler(reportController::createReport);
// router.put("/api/reports/:id").handler(reportController::updateReport);
// router.delete("/api/reports/:id").handler(reportController::deleteReport);

// // Project-specific reports (matches your tasks pattern)
// router.get("/api/projects/:project_id/reports").handler(reportController::getReportsByProject);

// // Type-specific reports
// router.get("/api/reports/type/:type").handler(reportController::getReportsByType);

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
