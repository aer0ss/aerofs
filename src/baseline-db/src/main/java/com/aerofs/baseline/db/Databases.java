/*
 * Copyright 2015 Air Computing Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aerofs.baseline.db;

import com.aerofs.baseline.Environment;
import com.aerofs.baseline.metrics.MetricRegistries;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.jdbi.InstrumentedTimingCollector;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.exceptions.DBIException;

import javax.annotation.concurrent.NotThreadSafe;
import javax.sql.DataSource;

/**
 * Utility methods for instantiating and working
 * with {@link DataSource} and {@link DBI} instances.
 */
@SuppressWarnings("unused")
@NotThreadSafe
public abstract class Databases {

    /**
     * Create a new {@code DBI} instance.
     * <br>
     * The created instance will log to the baseline service log,
     * and collects timing metrics.
     *
     * @param dataSource instance of {@code DataSource} from which database connections are sourced
     * @return valid, new {@code DBI} instance
     */
    public static DBI newDBI(DataSource dataSource) {
        DBI dbi = new DBI(dataSource);

        dbi.setSQLLog(new LogbackSQLLog());
        dbi.setTimingCollector(new InstrumentedTimingCollector(MetricRegistries.getRegistry()));

        return dbi;
    }

    /**
     * Create a new {@link DataSource} whose lifecycle is managed by baseline.
     *
     * @param environment {@code Environment} instance for this baseline service
     * @param database database configuration block for configuring the {@code DataSource}
     * @return valid, new {@code DataSource} instance
     */
    public static ManagedDataSource newManagedDataSource(Environment environment, DatabaseConfiguration database) {
        BasicDataSource dataSource = (BasicDataSource) newDataSource(database);
        registerGauges(dataSource);

        ManagedDataSource managed = new ManagedDataSource(dataSource);
        environment.addManaged(managed);

        return managed;
    }

    /**
     * Create a new {@code DataSource}.
     * <br>
     * The lifecycle of the created instance is <strong>NOT</strong>
     * managed by baseline. To create a {@code DataSource} whose lifecycle
     * is managed, use {@link #newManagedDataSource(Environment, DatabaseConfiguration)}.
     *
     * @param database database configuration block for configuring the {@code DataSource}
     * @return valid, new {@code DataSource} instance
     */
    public static DataSource newDataSource(DatabaseConfiguration database) {
        BasicDataSource dataSource = new BasicDataSource();

        // attempt to load the driver class
        dataSource.setDriverClassName(database.getDriverClass());

        // username and password
        // only sets them if the value is not null/empty
        if (!(database.getUsername() == null || database.getUsername().isEmpty())) {
            dataSource.setUsername(database.getUsername());
        }
        if (!(database.getPassword() == null || database.getPassword().isEmpty())) {
            dataSource.setPassword(database.getPassword());
        }

        // other properties
        dataSource.setUrl(database.getUrl());
        dataSource.setDefaultTransactionIsolation(database.getDefaultTransactionIsolation().getLevel());
        dataSource.setDefaultAutoCommit(database.isAutoCommit());
        dataSource.setTestOnBorrow(database.isCheckConnectionOnBorrow());
        dataSource.setTestOnReturn(database.isCheckConnectionOnReturn());
        dataSource.setMinIdle(database.getMinTotalConnections());
        dataSource.setMaxTotal(database.getMaxTotalConnections());
        dataSource.setMaxIdle(database.getMaxIdleConnections());
        dataSource.setMaxWaitMillis(database.getAcquireConnectionTimeout());
        dataSource.setDefaultQueryTimeout(database.getQueryTimeout() <= 0 ? null : database.getQueryTimeout());
        dataSource.setTestWhileIdle(database.isCheckConnectionWhenIdle());
        dataSource.setTimeBetweenEvictionRunsMillis(database.getIdleConnectionCheckInterval());
        dataSource.setMinEvictableIdleTimeMillis(database.getCloseIdleConnectionAfter());
        dataSource.setValidationQuery(database.getValidationQuery());
        dataSource.setValidationQueryTimeout((int) database.getValidationQueryTimeout());
        dataSource.setAccessToUnderlyingConnectionAllowed(false);
        dataSource.setDefaultAutoCommit(false);

        return dataSource;
    }

    private static void registerGauges(final BasicDataSource dataSource) {
        MetricRegistries.getRegistry().register(MetricRegistries.name("db", dataSource.getUrl(), "used"), (Gauge<Integer>) dataSource::getNumActive);
        MetricRegistries.getRegistry().register(MetricRegistries.name("db", dataSource.getUrl(), "idle"), (Gauge<Integer>) dataSource::getNumIdle);
    }

    /**
     * Convenience method for introspecting the
     * underlying cause of a {@code DBIException}.
     * <br>
     * This method can be used to find out what triggered
     * an exception inside a DBI transaction.
     *
     * @param exception instance of {@code DBIException} from which to extract the failure cause
     * @return {@code exception} itself if no {@code non-DBIException} was found to be the cause,
     * otherwise the <strong>first</strong> {@code non-DBIException} instance
     */
    public static Throwable findExceptionRootCause(DBIException exception) {
        Throwable underlying = exception;

        while(underlying != null && underlying instanceof DBIException) {
            underlying = underlying.getCause();
        }

        // if it's DBIExceptions all the way down just return the topmost exception
        if (underlying == null) {
            underlying = exception;
        }

        return underlying;
    }

    private Databases() {
        // to prevent instantiation by subclasses
    }
}
