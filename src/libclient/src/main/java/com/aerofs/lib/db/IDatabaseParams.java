package com.aerofs.lib.db;

public interface IDatabaseParams
{
    /**
     * @return URL used by the JDBC driver to connect to the database
     */
    String url();

    /**
     * Return value ignored if isMySQL() returns true
     */
    boolean sqliteExclusiveLocking();

    /**
     * Return value ignored if isMySQL() returns true
     */
    boolean sqliteWALMode();

    boolean autoCommit();
}
