package com.aerofs.servlets.lib.db.sql;

import javax.sql.DataSource;

public interface IDataSourceProvider {
    public DataSource getDataSource();
}
