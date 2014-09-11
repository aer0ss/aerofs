package com.aerofs.polaris;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.google.common.base.Objects;

import javax.validation.Valid;

@SuppressWarnings("unused")
public final class PolarisConfiguration extends Configuration {

    @Valid
    private DatabaseConfiguration database;

    public DatabaseConfiguration getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfiguration database) {
        this.database = database;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        PolarisConfiguration that = (PolarisConfiguration) o;
        return database.equals(that.database) && super.equals(that);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Objects.hashCode(database);
    }
}
