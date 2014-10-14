package com.aerofs.baseline.simple;

import com.aerofs.baseline.config.Configuration;
import com.aerofs.baseline.db.DatabaseConfiguration;
import com.google.common.base.Objects;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Min;

/**
 * Configuration object for the {@code} Simple server.
 */
@SuppressWarnings("unused")
public final class SimpleConfiguration extends Configuration {

    @Min(1)
    private int maxSeats;

    @Valid
    private DatabaseConfiguration database;

    public int getMaxSeats() {
        return maxSeats;
    }

    public void setMaxSeats(int maxSeats) {
        this.maxSeats = maxSeats;
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

        SimpleConfiguration other = (SimpleConfiguration) o;
        return maxSeats == other.maxSeats && database.equals(other.database) && super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Objects.hashCode(maxSeats, database);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("maxSeats", getMaxSeats())
                .add("app", getApp())
                .add("admin", getAdmin())
                .add("logging", getLogging())
                .add("database", getDatabase())
                .toString();
    }
}
