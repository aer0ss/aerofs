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

import com.aerofs.baseline.Managed;
import org.apache.tomcat.dbcp.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Wraps a {@link DataSource} instance and exposes the
 * {@link Managed} interface, allowing the wrapped instance's
 * lifecycle to be managed by baseline.
 * <br>
 * All overridden methods from {@code DataSource} are pass-throughs.
 * These methods should <strong>NOT</strong> be used once
 * {@link #stop()} is called.
 */
@ThreadSafe
@Singleton
public final class ManagedDataSource implements Managed, DataSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedDataSource.class);

    private final BasicDataSource dataSource;

    /**
     * Constructor.
     *
     * @param dataSource valid {@code DataSource} instance whose lifecycle should be managed by baseline
     */
    @Inject
    public ManagedDataSource(BasicDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("start datasource:{}", dataSource.getUrl());
    }

    @Override
    public void stop() {
        LOGGER.info("stop datasource:{}", dataSource.getUrl());

        try {
            dataSource.close();
        } catch (SQLException e) {
            LOGGER.warn("fail close datasource connections", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return dataSource.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return dataSource.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        dataSource.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        dataSource.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return dataSource.getLoginTimeout();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return dataSource.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return dataSource.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return dataSource.isWrapperFor(iface);
    }
}
