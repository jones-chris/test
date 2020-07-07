package com.cj.cache;

import com.cj.config.Qb4jConfig;
import com.cj.model.Column;
import com.cj.model.Database;
import com.cj.model.Schema;
import com.cj.model.Table;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class DatabaseMetadataCache {

    private Qb4jConfig qb4jConfig;

    private Set<Database> cache = new HashSet<>();

    public Set<Database> getCache() {
        return cache;
    }

    @Autowired
    public DatabaseMetadataCache(Qb4jConfig qb4jConfig) throws Exception {
        this.qb4jConfig = qb4jConfig;
        refreshCache();
    }

    // Run on start up AND every 24 hours thereafter.
    @Scheduled(initialDelay = 0, fixedRate = 8640000000L)
    public void refreshCache() throws Exception {
        // Get list of databases from qb4jConfig's target data sources.
        List<Database> databases = qb4jConfig.getTargetDataSources().stream()
                .map(targetDataSource -> new Database(targetDataSource.getName(), targetDataSource.getDatabaseType()))
                .collect(Collectors.toList());

        for (Database database : databases) {
            // Get schemas
            List<Schema> schemas = this.getSchemas();
            database.setSchemas(schemas);

            // Get tables
            for (Schema schema : database.getSchemas()) {
                List<Table> tables = this.getTablesAndViews(schema.getSchemaName());
                schema.setTables(tables);

                // Get columns
                for (Table table : schema.getTables()) {
                    List<Column> columns = this.getColumns(table.getSchemaName(), table.getTableName());
                    table.setColumns(columns);
                }
            }

        }

        // Save database metadata to cache
        cache.clear();
        cache.addAll(databases);
    }

    public Database findDatabases(String databaseName) throws Exception {
        return this.cache.stream()
                .filter(database -> database.getDatabaseName().equals(databaseName))
                .findAny()
                .orElseThrow(Exception::new);
    }

    public List<Schema> findSchemas(String databaseName) throws Exception {
        return this.findDatabases(databaseName).getSchemas();
    }

    public List<Table> findTables(String databaseName, String schemaName) throws Exception {
        return this.findSchemas(databaseName).stream()
                .filter(schema -> schema.getSchemaName().equals(schemaName))
                .map(Schema::getTables)
                .findAny()
                .orElseThrow(Exception::new);
    }

    public List<Column> findColumns(String databaseName, String schemaName, String tableName) throws Exception {
        return this.findTables(databaseName, schemaName).stream()
                .filter(table -> table.getTableName().equals(tableName))
                .map(Table::getColumns)
                .findAny()
                .orElseThrow(Exception::new);
    }

    private List<Schema> getSchemas() throws Exception {
        List<Schema> schemas = new ArrayList<>();
        for (Qb4jConfig.TargetDataSource targetDataSource : qb4jConfig.getTargetDataSources()) {

            try (Connection conn = targetDataSource.getDataSource().getConnection()) {
                ResultSet rs = conn.getMetaData().getSchemas();

                String databaseName = targetDataSource.getName();
                while (rs.next()) {
                    String schemaName = rs.getString("TABLE_SCHEM");
                    Schema schema = new Schema(databaseName, schemaName);
                    schemas.add(schema);
                }

                // If no schemas exist (which is the case for some databases, like SQLite), add a schema with null for
                // the schema name.
                if (schemas.isEmpty()) {
                    schemas.add(new Schema(databaseName, null));
                }

            }

        }

        return schemas;
    }

    private List<Table> getTablesAndViews(String schema) throws Exception {
        List<Table> tables = new ArrayList<>();
        for (Qb4jConfig.TargetDataSource targetDataSource : qb4jConfig.getTargetDataSources()) {

            try (Connection conn = targetDataSource.getDataSource().getConnection()) {
                ResultSet rs = conn.getMetaData().getTables(null, schema, null, null);

                String databaseName = targetDataSource.getName();
                while (rs.next()) {
                    String schemaName = rs.getString("TABLE_SCHEM");
                    String tableName = rs.getString("TABLE_NAME");
                    Table table = new Table(databaseName, schemaName, tableName);
                    tables.add(table);
                }

            }

        }

        return tables;
    }

    private List<Column> getColumns(String schema, String table) throws Exception {
        List<Column> columns = new ArrayList<>();
        for (Qb4jConfig.TargetDataSource targetDataSource : qb4jConfig.getTargetDataSources()) {

            try (Connection conn = targetDataSource.getDataSource().getConnection()) {
                ResultSet rs = conn.getMetaData().getColumns(null, schema, table, "%");

                String databaseName = targetDataSource.getName();
                while (rs.next()) {
                    String schemaName = rs.getString("TABLE_SCHEM");
                    String tableName = rs.getString("TABLE_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    int dataType = rs.getInt("DATA_TYPE");

                    Column column = new Column(databaseName, schemaName, tableName, columnName, dataType);

                    columns.add(column);
                }

            }

        }

        return columns;
    }

}