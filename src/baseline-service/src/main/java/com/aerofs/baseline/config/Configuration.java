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

package com.aerofs.baseline.config;

import com.aerofs.baseline.http.HttpConfiguration;
import com.aerofs.baseline.logging.LoggingConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Objects;
import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Base class for all baseline derived service configurations.
 * <p>
 * Contains the following configuration blocks:
 * <ul>
 *     <li>app</li>
 *     <li>admin</li>
 *     <li>logging</li>
 * </ul>
 */
@SuppressWarnings("unused")
@NotThreadSafe
public abstract class Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    @SuppressWarnings("unchecked")
    public static <C extends Configuration> C loadYAMLConfigurationFromFile(Class<?> derived, String configurationFile) throws IOException {
        FileInputStream in = null;

        try {
            in = new FileInputStream(configurationFile);
            return Configuration.loadYAMLConfigurationFromStream(derived, in);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    LOGGER.warn("fail close input stream for {}", configurationFile, e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <C extends Configuration> C loadYAMLConfigurationFromResourcesUncheckedThrow(Class<?> derived, String configFile) {
        try {
            return loadYAMLConfigurationFromResources(derived, configFile);
        } catch (Exception e) {
            throw new RuntimeException("fail load " + configFile, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <C extends Configuration> C loadYAMLConfigurationFromResources(Class<?> derived, String configFile) throws IOException {
        try (FileInputStream in = new FileInputStream(Resources.getResource(configFile).getFile())) {
            return Configuration.loadYAMLConfigurationFromStream(derived, in);
        }
    }

    @SuppressWarnings("unchecked")
    public static <C extends Configuration> C loadYAMLConfigurationFromStream(Class<?> derived, InputStream configStream) throws IOException {
        ObjectMapper configurationMapper = new ObjectMapper(new YAMLFactory());
        Class<?> configurationTypeClass = Generics.getTypeParameter(derived, Configuration.class);
        return (C) configurationMapper.readValue(configStream, configurationTypeClass);
    }

    public static <C extends Configuration> void validateConfiguration(Validator validator, C configuration) throws ConstraintViolationException {
        Set<ConstraintViolation<C>> violations = validator.validate(configuration);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    @NotNull
    @Valid
    private HttpConfiguration admin;

    @NotNull
    @Valid
    private HttpConfiguration service;

    @NotNull
    @Valid
    private LoggingConfiguration logging = new LoggingConfiguration();

    public HttpConfiguration getAdmin() {
        return admin;
    }

    public void setAdmin(HttpConfiguration admin) {
        this.admin = admin;
    }

    public HttpConfiguration getService() {
        return service;
    }

    public void setService(HttpConfiguration service) {
        this.service = service;
    }

    public LoggingConfiguration getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfiguration logging) {
        this.logging = logging;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || !Configuration.class.isAssignableFrom(o.getClass())) return false;

        Configuration other = (Configuration) o;
        return Objects.equal(admin, other.admin) && Objects.equal(service, other.service) && Objects.equal(logging, other.logging);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(admin, service, logging);
    }

    @Override
    public String toString() {
        return Objects
                .toStringHelper(this)
                .add("admin", admin)
                .add("service", service)
                .add("logging", logging)
                .toString();
    }
}
