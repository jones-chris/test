package com.cj.service;

public interface DatabaseHealerService {

    boolean dropDatabase();
    boolean createDatabase();
    boolean dropTable();
    boolean dropAllTablesExcept(String tableNotToDrop);
    boolean createTable();
    boolean insertTestData();
    boolean healDatabaseEntirely();

}
