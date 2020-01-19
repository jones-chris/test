package com.cj.controllers;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.cj.config.Constants;
import com.cj.service.*;
import com.cj.utils.Converter;
import com.cj.utils.TableauColumnSchema;
import com.cj.utils.TableauColumns;
import com.cj.utils.TableauTableSchema;
import com.google.gson.Gson;
import com.querybuilder4j.databasemetadata.QueryTemplateDao;
import com.querybuilder4j.statements.SelectStatement;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
public class RestApiController {
    private LoggingService loggingService;
    private DatabaseAuditService databaseAuditService;
    private DatabaseHealerService databaseHealerService;
    private DatabaseMetaDataService databaseMetaDataService;
    private QueryTemplateService queryTemplateService;
    private QueryTemplateDao queryTemplateDao;
    private Properties queryBuilder4JDatabaseProperties;

    @Autowired
    public RestApiController(LoggingService loggingService,
                             DatabaseAuditService databaseAuditService,
                             DatabaseHealerService databaseHealerService,
                             DatabaseMetaDataService databaseMetaDataService,
                             QueryTemplateService queryTemplateService,
                             QueryTemplateDao queryTemplateDao,
                             @Qualifier("querybuilder4jdb_properties") Properties properties) {
        this.loggingService = loggingService;
        this.databaseAuditService = databaseAuditService;
        this.databaseHealerService = databaseHealerService;
        this.databaseMetaDataService = databaseMetaDataService;
        this.queryTemplateService = queryTemplateService;
        this.queryTemplateDao = queryTemplateDao;
        this.queryBuilder4JDatabaseProperties = properties;
    }

    /**
     * Gets the query template names given a limit, offset, and and ordering (ascending vs descending).
     *
     * @param limit
     * @param offset
     * @param ascending
     * @return
     */
    @GetMapping(value = "/queryTemplates")
    public ResponseEntity<String> getQueryTemplates(@RequestParam(required = false) Integer limit,
                                                    @RequestParam(required = false) Integer offset,
                                                    @RequestParam(required = false) boolean ascending) {
        try {
            String jsonResults = queryTemplateService.getNames(limit, offset, ascending);
            return new ResponseEntity<>(jsonResults, HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get a query template by a unique name.
     *
     * @param name
     * @return
     */
    @GetMapping(value = "/queryTemplates/{name}")
    public ResponseEntity<String> getQueryTemplateById(@PathVariable String name) {
        try {
            String queryTemplate = queryTemplateService.findByName(name);
            return new ResponseEntity<>(queryTemplate, HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Save a SelectStatement.
     *
     * @param selectStatement
     * @return
     */
    @PostMapping(value = "/saveQueryTemplate")
    public ResponseEntity<String> saveQueryTemplate(@RequestBody SelectStatement selectStatement) {
        try {
            if (selectStatement.getName() == null) {
                throw new RuntimeException("The name of the select statement cannot be null when saving the statement " +
                        "as a query template");
            }

            Gson gson = new Gson();
            String json = gson.toJson(selectStatement);

            queryTemplateService.save(selectStatement.getName(), json);

            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Returns all database schemas that a given user has access to.
     *
     * @return
     */
    @GetMapping(value = "/schemas")
    public ResponseEntity<String> getSchemas() {
        try {
            String schemasJson = databaseMetaDataService.getSchemas();
            return new ResponseEntity<>(schemasJson, HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Returns all database tables and views that a given user has access to.
     *
     * @param schema
     * @return
     */
    @GetMapping(value = "/tablesAndViews/{schema}")
    public ResponseEntity<String> getTablesAndViews(@PathVariable(value = "schema", required = true) String schema) {
        try {
            schema = (schema.equals("null")) ? null : schema;
            String tablesJson = databaseMetaDataService.getTablesAndViews(schema);
            return new ResponseEntity<>(tablesJson, HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Returns all columns for any number of tables or views given a schema name and table/view name (user permissions apply).
     *
     * @param schema
     * @param tables
     * @return
     */
    @GetMapping(value = "/columns/{schema}/{tables}")
    public ResponseEntity<String> getColumns(@PathVariable String schema,
                                             @PathVariable String tables)  {
        try {
            schema = (schema.equals("null")) ? null : schema;

            String[] splitTables = tables.split("&");

            Map<String, Integer> columnsMap = new HashMap<>();
            for (String table : splitTables) {
                Map<String, Integer> columns = databaseMetaDataService.getColumns(schema, table);
                columnsMap.putAll(columns);
            }

            String columnsJson = Converter.convertToJSON(columnsMap.keySet().toArray(), "column").toString();
            return new ResponseEntity<>(columnsJson, HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get a column's members.
     *
     * @param schema
     * @param table
     * @param column
     * @param limit
     * @param offset
     * @param ascending
     * @param search
     * @return
     */
    @GetMapping(value = "/columns-members/{schema}/{table}/{column}")
    public ResponseEntity<String> getColumnMembers(@PathVariable String schema,
                                                   @PathVariable String table,
                                                   @PathVariable String column,
                                                   @RequestParam int limit,
                                                   @RequestParam int offset,
                                                   @RequestParam boolean ascending,
                                                   @RequestParam(required = false) String search) {
        try {
            String jsonResults = databaseMetaDataService.getColumnMembers(schema, table, column, limit, offset, ascending, search);
            return new ResponseEntity<>(jsonResults, HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Execute a SelectStatement, audits the database for any unexpected changes, heals the database if necessary, publishes
     * a request to an SNS topic (if the database needed to be healed), and returns the query's results.
     *
     * @param selectStatement
     * @return
     */
    @PostMapping(value = "/query")
    public ResponseEntity<String> getQueryResults(@RequestBody SelectStatement selectStatement) {
        try {
            selectStatement.setQueryTemplateDao(queryTemplateDao);
            String sql = selectStatement.toSql(queryBuilder4JDatabaseProperties);
            String queryResults = databaseMetaDataService.executeQuery(sql);

            JSONObject jsonObject = getDatabaseAuditResults(selectStatement, sql);
            jsonObject.append("queryResults", queryResults);
            jsonObject.append("sqlResult", sql);

            return new ResponseEntity<>(jsonObject.toString(4), HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/tableau-wdc-types")
    public ResponseEntity<String> getColumnDataTypes(@RequestBody TableauColumns columns) {
        try {
            List<String> tables = new ArrayList<>();
            columns.getColumns().forEach((column) -> {
                String table = column.split("\\.")[0];
                if (! tables.contains(table)) { tables.add(table); }
            });

            Map<String, Integer> sqlTypes = new HashMap<>();
            for (String table : tables) {
                Map<String, Integer> tableMetaData = databaseMetaDataService.getColumns(null, table);
                sqlTypes.putAll(tableMetaData);
            }

            List<TableauColumnSchema> columnSchemas = new ArrayList<>();
            for (String column : columns.getColumns()) {
                Integer sqlType = sqlTypes.get(column);
                String tableType = Constants.tableauDataTypeMappings.get(sqlType);
                columnSchemas.add(new TableauColumnSchema(column.split("\\.")[1], tableType));
            }
            TableauTableSchema tableauTableSchema = new TableauTableSchema("qb4j", "my alias", columnSchemas);

            return ResponseEntity.ok(new Gson().toJson(tableauTableSchema));
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private JSONObject getDatabaseAuditResults(SelectStatement selectStatement, String sql) {
//      Log the SelectStatement and the database audit results to logging.db
        Map<String, Boolean> databaseAuditResults = databaseAuditService.runAllChecks();

//      Set QueryTemplateDao to null before persisting SelectStatement.
        selectStatement.setQueryTemplateDao(null);
        loggingService.add(selectStatement, sql, databaseAuditResults);

        Map<String, Runnable> healerFunctions;
        if (databaseAuditResults.values().contains(false)) {
            // Publish message to SNS topic to alert interested parties.
//                try {
//                    publishSnsMessage();
//                } catch (Exception ex) {
//                    // Todo:  Log the exception and SelectStatement.toString().
//                }

            // Heal database so it's ready for next request.
            healerFunctions = buildHealerFunctionsMap();
            databaseAuditResults.forEach((key, passedCheck) -> {
                if (! passedCheck) {
                    healerFunctions.get(key).run();
                }
            });
        }

        return new JSONObject(databaseAuditResults);
    }

    /**
     * Builds a Map of functions that should be run if a certain check (the key) is false.
     *
     * @return
     */
    private Map<String, Runnable> buildHealerFunctionsMap() {
        Map<String, Runnable> healerFunctions = new HashMap<>();

        healerFunctions.put("databaseExists", () -> {
            databaseHealerService.createDatabase();
            databaseHealerService.createTable();
            databaseHealerService.insertTestData();
        });
        healerFunctions.put("tableExists", () -> {
            databaseHealerService.createTable();
            databaseHealerService.insertTestData();
        });
        healerFunctions.put("tablesAreSame", () -> {
            databaseHealerService.dropAllTablesExcept("county_spending_detail");
        });
        healerFunctions.put("numOfTableColumnsAreSame", () -> {
            databaseHealerService.dropTable();
            databaseHealerService.createTable();
            databaseHealerService.insertTestData();
        });
        healerFunctions.put("numOfTableRowsAreSame", () -> {
            databaseHealerService.dropTable();
            databaseHealerService.createTable();
            databaseHealerService.insertTestData();
        });
        healerFunctions.put("tableDataIsSame", () -> {
            databaseHealerService.dropTable();
            databaseHealerService.createTable();
            databaseHealerService.insertTestData();
        });

        return healerFunctions;
    }

    /**
     * Wrapper method for publishing a message to SNS.  This method includes a throws clause in the signature header
     * to call attention to the fact that the SNS client could throw various Exception child classes.
     *
     * @throws Exception
     */
//    private void publishSnsMessage() throws Exception {
//        String snsMessage = "A query was submitted that caused a database audit result to fail. Please " +
//                "check the S3 logs for more information.";
//        String arn = "arn:aws:sns:us-east-1:526661363425:qb4j-sql-injection-success";
//        PublishRequest publishRequest = new PublishRequest(arn, snsMessage);
//        snsClient.publish(publishRequest);
//    }
}
