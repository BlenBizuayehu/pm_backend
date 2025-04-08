// package com.example;

// import io.vertx.core.json.JsonArray;
// import io.vertx.core.json.JsonObject;
// import io.vertx.ext.web.RoutingContext;
// import io.vertx.pgclient.PgPool;
// import io.vertx.sqlclient.Tuple;

// public class ReportController {
//     private final PgPool dbClient;

//     public ReportController(PgPool dbClient) {
//         this.dbClient = dbClient;
//     }

//     public void getAllReports(RoutingContext ctx) {
//         dbClient.query("SELECT id, project_id, generated_by, report_type, report_data, generated_at FROM reports ORDER BY generated_at DESC")
//             .execute()
//             .onSuccess(rows -> {
//                 JsonArray reports = new JsonArray();
//                 rows.forEach(row -> reports.add(row.toJson()));
//                 ctx.response()
//                     .putHeader("Content-Type", "application/json")
//                     .end(reports.encode());
//             })
//             .onFailure(err -> {
//                 System.err.println("Database error: " + err.getMessage());
//                 ctx.fail(500, err);
//             });
//     }

//     public void createReport(RoutingContext ctx) {
//         JsonObject report = ctx.getBodyAsJson();
        
//         // Handle numeric IDs
//         Integer projectId = report.getValue("project_id") != null ? 
//             Integer.valueOf(report.getValue("project_id").toString()) : null;
//         Integer generatedBy = report.getValue("generated_by") != null ? 
//             Integer.valueOf(report.getValue("generated_by").toString()) : null;

//         dbClient.preparedQuery(
//             "INSERT INTO reports (project_id, generated_by, report_type, report_data, generated_at) " +
//             "VALUES ($1, $2, $3, $4, CURRENT_TIMESTAMP) " +
//             "RETURNING id, project_id, generated_by, report_type, report_data, generated_at")
//             .execute(Tuple.of(
//                 projectId,
//                 generatedBy,
//                 report.getString("report_type"),
//                 report.getString("report_data", "")))
//             .onSuccess(rows -> {
//                 ctx.response()
//                     .setStatusCode(201)
//                     .putHeader("Content-Type", "application/json")
//                     .end(rows.iterator().next().toJson().encode());
//             })
//             .onFailure(err -> {
//                 System.err.println("Create report error: " + err.getMessage());
//                 ctx.fail(500, err);
//             });
//     }

//     public void updateReport(RoutingContext ctx) {
//         String reportIdParam = ctx.pathParam("id");
//         JsonObject report = ctx.getBodyAsJson();

//         if (reportIdParam == null || reportIdParam.isEmpty()) {
//             ctx.response().setStatusCode(400).end("Missing report id");
//             return;
//         }

//         try {
//             int reportId = Integer.parseInt(reportIdParam);
            
//             // Handle numeric IDs
//             Integer projectId = report.getValue("project_id") != null ? 
//                 Integer.valueOf(report.getValue("project_id").toString()) : null;
//             Integer generatedBy = report.getValue("generated_by") != null ? 
//                 Integer.valueOf(report.getValue("generated_by").toString()) : null;

//             dbClient.preparedQuery(
//                 "UPDATE reports SET " +
//                 "project_id = $1, generated_by = $2, report_type = $3, report_data = $4 " +
//                 "WHERE id = $5 " +
//                 "RETURNING id, project_id, generated_by, report_type, report_data, generated_at")
//                 .execute(Tuple.of(
//                     projectId,
//                     generatedBy,
//                     report.getString("report_type"),
//                     report.getString("report_data", ""),
//                     reportId))
//                 .onSuccess(rows -> {
//                     if (rows.size() == 0) {
//                         ctx.response().setStatusCode(404).end("Report not found");
//                     } else {
//                         ctx.response()
//                             .putHeader("Content-Type", "application/json")
//                             .end(rows.iterator().next().toJson().encode());
//                     }
//                 })
//                 .onFailure(err -> {
//                     ctx.response()
//                         .setStatusCode(500)
//                         .end("Database error: " + err.getMessage());
//                 });
//         } catch (NumberFormatException e) {
//             ctx.response().setStatusCode(400).end("Report id must be a number");
//         }
//     }

//     public void deleteReport(RoutingContext ctx) {
//         String reportIdParam = ctx.pathParam("id");

//         if (reportIdParam == null || reportIdParam.isEmpty()) {
//             ctx.response().setStatusCode(400).end("Missing report id");
//             return;
//         }

//         try {
//             int reportId = Integer.parseInt(reportIdParam);
            
//             dbClient.preparedQuery("DELETE FROM reports WHERE id = $1")
//                 .execute(Tuple.of(reportId))
//                 .onSuccess(rows -> {
//                     if (rows.rowCount() == 0) {
//                         ctx.response().setStatusCode(404).end("Report not found");
//                     } else {
//                         ctx.response().setStatusCode(204).end();
//                     }
//                 })
//                 .onFailure(err -> {
//                     ctx.response()
//                         .setStatusCode(500)
//                         .end("Database error: " + err.getMessage());
//                 });
//         } catch (NumberFormatException e) {
//             ctx.response().setStatusCode(400).end("Report id must be a number");
//         }
//     }

//     public void getReportsByProject(RoutingContext ctx) {
//         String projectIdParam = ctx.pathParam("project_id");

//         if (projectIdParam == null || projectIdParam.isEmpty()) {
//             ctx.response().setStatusCode(400).end("Missing project_id");
//             return;
//         }

//         try {
//             int projectId = Integer.parseInt(projectIdParam);
            
//             dbClient.preparedQuery(
//                 "SELECT id, project_id, generated_by, report_type, report_data, generated_at FROM reports " +
//                 "WHERE project_id = $1 ORDER BY generated_at DESC")
//                 .execute(Tuple.of(projectId))
//                 .onSuccess(rows -> {
//                     JsonArray reports = new JsonArray();
//                     rows.forEach(row -> reports.add(row.toJson()));
//                     ctx.response()
//                         .putHeader("Content-Type", "application/json")
//                         .end(reports.encode());
//                 })
//                 .onFailure(err -> {
//                     ctx.fail(500, err);
//                 });
//         } catch (NumberFormatException e) {
//             ctx.response().setStatusCode(400).end("project_id must be a number");
//         }
//     }

//     public void getReportsByType(RoutingContext ctx) {
//         String reportType = ctx.pathParam("type");
        
//         if (reportType == null || reportType.isEmpty()) {
//             ctx.response().setStatusCode(400).end("Missing report type");
//             return;
//         }

//         dbClient.preparedQuery(
//             "SELECT id, project_id, generated_by, report_type, report_data, generated_at FROM reports " +
//             "WHERE report_type = $1 ORDER BY generated_at DESC")
//             .execute(Tuple.of(reportType))
//             .onSuccess(rows -> {
//                 JsonArray reports = new JsonArray();
//                 rows.forEach(row -> reports.add(row.toJson()));
//                 ctx.response()
//                     .putHeader("Content-Type", "application/json")
//                     .end(reports.encode());
//             })
//             .onFailure(err -> {
//                 ctx.fail(500, err);
//             });
//     }
// }