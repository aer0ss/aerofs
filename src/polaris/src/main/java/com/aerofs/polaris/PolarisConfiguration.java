package com.aerofs.polaris;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@SuppressWarnings("unused")
public final class PolarisConfiguration extends Configuration {

    @Min(1)
    private int maxReturnedTransforms = Constants.MAX_RETURNED_TRANSFORMS;

    @NotNull
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

        PolarisConfiguration other = (PolarisConfiguration) o;
        return maxReturnedTransforms == other.maxReturnedTransforms && Objects.equal(database, other.database);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Objects.hashCode(maxReturnedTransforms, database);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("maxReturnedTransforms", getMaxReturnedTransforms())
                .add("app", getService())
                .add("admin", getAdmin())
                .add("logging", getLogging())
                .add("database", getDatabase())
                .toString();
    }
}
