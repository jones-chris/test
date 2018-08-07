package com.cj.controllers;

import com.cj.service.DatabaseHealerService;
import com.cj.service.DatabaseMetaDataService;
import com.cj.service.LoggingService;
import com.cj.utils.Converter;
import com.cj.service.DatabaseAuditService;
import com.querybuilder4j.config.DatabaseType;
import com.querybuilder4j.utils.ResultSetToHashMapConverter;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RestController
public class DatabaseMetaDataController {
    @Qualifier("querybuilder4j.db")
    @Autowired
    private DataSource dataSource;
    @Autowired
    private LoggingService loggingService;
    @Autowired
    private DatabaseAuditService databaseAuditService;
    @Autowired
    private DatabaseHealerService databaseHealerService;
    @Autowired
    private DatabaseMetaDataService databaseMetaDataService;

    //get query templates
//    @RequestMapping(value = "/queryTemplates", method = RequestMethod.GET)
//    @ResponseBody
//    public String getQueryTemplates() throws Exception {
//        Statement stmt = dataSource.getConnection().createStatement();
//        String sql = "SELECT name FROM qb_templates;";
//        ResultSet rs = stmt.executeQuery(sql);
//
//        return Converter.convertToJSON(rs).toString();
//    }
//
//    //get query template by unique name
//    @RequestMapping(value = "/queryTemplates/{name}", method = RequestMethod.GET)
//    @ResponseBody
//    public JSONArray getQueryTemplateById(@PathVariable String name) throws Exception {
//        Statement stmt = dataSource.getConnection().createStatement();
//        String sql = String.format("SELECT query_object FROM qb_templates WHERE name = '%s';", name);
//        ResultSet rs = stmt.executeQuery(sql);
//
//        return Converter.convertToJSON(rs);
//    }
//
//    //get schemas
//    @RequestMapping(value = "/schemas", method = RequestMethod.GET)
//    @ResponseBody
//    public ResponseEntity<String> getSchemas() throws Exception {
//        try {
//            ResultSet rs = databaseMetaDataService.getSchemas();
//            return new ResponseEntity<>(Converter.convertToJSON(rs).toString(), HttpStatus.OK);
//        } catch (SQLException ex) {
//            return new ResponseEntity<>(ex.getMessage(), HttpStatus.OK);
//        }
//    }
//
//    //get tables/views
//    @RequestMapping(value = "/tablesAndViews/{schema}", method = RequestMethod.GET)
//    @ResponseBody
//    public ResponseEntity<String> getTablesAndViews(@PathVariable(value = "schema", required = true) String schema) throws Exception {
//        //String username = dataSource.getConnection().getMetaData().getUserName();
//        try {
//            ResultSet rs = databaseMetaDataService.getTablesAndViews(schema);
//            return new ResponseEntity<>(Converter.convertToJSON(rs).toString(), HttpStatus.OK);
//        } catch (SQLException ex) {
//            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }
//
//    //get table/view columns
//    @RequestMapping(value = "/columns/{schema}/{table}", method = RequestMethod.GET)
//    @ResponseBody
//    public ResponseEntity<String> getColumns(@PathVariable String schema,
//                                             @PathVariable String table) throws Exception {
//        try {
//            ResultSet rs = databaseMetaDataService.getColumns(schema, table);
//            return new ResponseEntity<>(Converter.convertToJSON(rs).toString(), HttpStatus.OK);
//        } catch (SQLException ex) {
//            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }

    //execute query and return results
    @RequestMapping(value = "/query", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<String> getQueryResults(com.querybuilder4j.sqlbuilders.statements.SelectStatement selectStatement) throws Exception {
        //Load properties file.
        Properties props = new Properties();
        InputStream input = this.getClass().getClassLoader().getResourceAsStream("application.properties");
        props.load(input);

        //Get column meta data to use in SelectStatement's toString method.
        ResultSet columnMetaData = databaseMetaDataService.getColumns(null, selectStatement.getTable());
        Map<String, Integer> columnMetaDataMap = ResultSetToHashMapConverter.toHashMap(columnMetaData);

        //Set selectStatement's databaseType and tableSchema.
        selectStatement.setDatabaseType(Enum.valueOf(DatabaseType.class, props.getProperty("databaseType")));
        selectStatement.setTableSchema(columnMetaDataMap);

        //Convert selectStatement to SQL string and run against database.
        //Then convert ResultSet to JSON.
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(selectStatement.toSql());
            String json = Converter.convertToJSON(rs).toString();

            //Log the SelectStatement and the databas audit results to logging.db
            //If any of the database audit results return false (a failure - meaning this statement changed the querybuilder4j
            //  database in some way), then send email to querybuilder4j@gamil.com for immediate notification.
            Map<String, Boolean> databaseAuditResults = databaseAuditService.runAllChecks(1, 1, new String[1], 1);
            loggingService.add(selectStatement, databaseAuditResults);

            Map<String, Runnable> healerFunctions = buildHealerFunctionsMap();
            for (String key : databaseAuditResults.keySet()) {
                if (databaseAuditResults.get(key) == false) {
                    healerFunctions.get(key).run();
                }
            }

            return new ResponseEntity<>(json, HttpStatus.OK);

        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    private Map<String, Runnable> buildHealerFunctionsMap() {
        Map<String, Runnable> healerFunctions = new HashMap<>();

        healerFunctions.put("databaseExists", () -> {
            databaseHealerService.createDatabase();
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
}
