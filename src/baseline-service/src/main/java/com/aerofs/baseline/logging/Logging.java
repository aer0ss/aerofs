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

package com.aerofs.baseline.logging;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.logging.LogManager;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

@NotThreadSafe
public abstract class Logging {

    /**
     * Setup info logging to the console.
     * <p>
     * To be used only at startup before the configuration file is parsed.
     */
    public static void setupErrorConsoleLogging() {
        Logger root = getResetRootLogger();

        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(Level.ERROR.levelStr); // only up to ERROR
        filter.start();

        setupConsoleLogging(root, filter);
    }

    /**
     * Setup the logging subsystem.
     * <p>
     * Used once configuration has been loaded and validated.
     *
     * @param loggingConfiguration valid instance of {@link LoggingConfiguration}
     */
    public static void setupLogging(LoggingConfiguration loggingConfiguration) {
        // reset to "all logging allowed"
        Logger root = getResetRootLogger();

        // now, set up the entire logging
        // subsystem with the user-provided
        // configuration
        ThresholdFilter filter = new ThresholdFilter();
        filter.setLevel(loggingConfiguration.getLevel()); // up to the specified config level
        filter.start();

        if (loggingConfiguration.getConsole().isEnabled()) {
            setupConsoleLogging(root, filter);
        }

        if (loggingConfiguration.getFile().isEnabled()) {
            setupFileLogging(root, filter, loggingConfiguration.getFile().getLogfile());
        }

        // there are certain subsystems that create
        // annoying log messages - especially in DEBUG.
        // silence them.
        setHardCodedLoggingOverrides();
    }

    private static void setHardCodedLoggingOverrides() {
        Logger logger;

        // ReflectionHelper instantiates a static reference
        // to the OSGI subsystem. since we don't use OSGI, on
        // DEBUG this call results in worrisome messages
        // claiming "Class Not Found". explicitly move the
        // log level for this component to INFO to silence this
        // NOTE: this call to ReflectionHelper is only done
        // *once* on startup when the first ResourceConfig
        // object and its underlying State() is instantiated
        logger = (Logger) LoggerFactory.getLogger("org.glassfish.jersey.internal.util.ReflectionHelper");
        if (!logger.getEffectiveLevel().isGreaterOrEqual(Level.INFO)) {
            logger.setLevel(Level.INFO);
        }

        // Parameter creates a particularly annoying stack trace
        // where every annotation without a value() method creates
        // a giant stack trace. The value() method is used to
        // check if it's a JAX-RS annotation. Since I also add
        // validation annotations the introspection fails and
        // pollutes the logs
        logger = (Logger) LoggerFactory.getLogger("org.glassfish.jersey.server.model.Parameter");
        if (!logger.getEffectiveLevel().isGreaterOrEqual(Level.INFO)) {
            logger.setLevel(Level.INFO);
        }

        // apache httpclient is too damn noisy
        // in particular, it includes full wire log at DEBUG level...
        logger = (Logger) LoggerFactory.getLogger("org.apache.http");
        if (!logger.getEffectiveLevel().isGreaterOrEqual(Level.INFO)) {
            logger.setLevel(Level.INFO);
        }
    }

    private static Logger getResetRootLogger() {
        // reset JUL logging
        LogManager.getLogManager().reset();
        // set JUL to allow *all* logging to be enabled
        // have to do this otherwise it'll filter before slf4j does
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.FINEST);

        // now, reset the JUL handlers and route its messages to slf4j
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // note that netty by default will use slf4j
        // so we don't have to do anything special to get its output

        // reset the logback system
        Logger root = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
        root.setLevel(Level.ALL);
        root.detachAndStopAllAppenders();
        return root;
    }

    /**
     * Shutdown the logging subsystem.
     */
    public static void stopLogging() {
        Logger root = (Logger) LoggerFactory.getLogger(ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();
    }

    private static void setupConsoleLogging(Logger root, ThresholdFilter filter) {
        MinimalLayout formatter = new MinimalLayout(root.getLoggerContext());
        formatter.start();

        ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.addFilter(filter);
        appender.setContext(root.getLoggerContext());
        appender.setLayout(formatter);
        appender.start();

        root.addAppender(appender);
    }

    private static void setupFileLogging(Logger root, ThresholdFilter filter, String logfilePath) {
        MinimalLayout formatter = new MinimalLayout(root.getLoggerContext());
        formatter.start();

        // setup the file appender
        FileAppender<ILoggingEvent> appender = new FileAppender<>();
        appender.setFile(logfilePath);
        appender.setContext(root.getLoggerContext());
        appender.setLayout(formatter);
        appender.start();

        // wrap it in an async appender
        final AsyncAppender asyncAppender = new AsyncAppender();
        asyncAppender.setContext(root.getLoggerContext());
        asyncAppender.addAppender(appender);
        asyncAppender.addFilter(filter);
        asyncAppender.start();

        // now, add it to the root logger
        root.addAppender(asyncAppender);
    }

    private Logging() {
        // to prevent instantiation by subclasses
    }
}
