package com.aerofs.polaris;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;

@SuppressWarnings("unused")
public final class PolarisConfiguration extends Configuration {

    private int maxReturnedTransforms = Constants.MAX_RETURNED_TRANSFORMS;

    @Valid
    private DatabaseConfiguration database;

    public int getMaxReturnedTransforms() {
        return maxReturnedTransforms;
    }

    public void setMaxReturnedTransforms(int maxReturnedTransforms) {
        this.maxReturnedTransforms = maxReturnedTransforms;
    }

    public DatabaseConfiguration getDatabase() {
        return database;
    }

    public void setDatabase(DatabaseConfiguration database) {
        this.database = database;
    }

    @Override
    public boolean equals(@Nullable Object o) {
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
