/*
 * Copyright (c) Air Computing Inc., 2015.
 */

package com.aerofs.ca.server.config;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuppressWarnings("unused")
public class CAConfig extends Configuration
{
    @NotNull
    @Valid
    private DatabaseConfiguration database;

    public DatabaseConfiguration getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfiguration database) {
        this.database = database;
    }
}
