package com.cj.model;

import java.util.ArrayList;
import java.util.List;

public class Schema {

    private String fullyQualifiedName;
    private String databaseName;
    private String schemaName;
    private transient List<Table> tables = new ArrayList<>();

    public Schema(String databaseName, String schemaName) {
        this.fullyQualifiedName = String.format("%s.%s", databaseName, schemaName);
        this.databaseName = databaseName;
        this.schemaName = schemaName;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    public void setFullyQualifiedName(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public List<Table> getTables() {
        return tables;
    }

    public void setTables(List<Table> tables) {
        this.tables = tables;
    }
}
