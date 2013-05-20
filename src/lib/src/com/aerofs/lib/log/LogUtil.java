package com.aerofs.lib.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Helpers for dealing with logback
 * IMPORTANT: DO NOT USE "L" in here!!!
 */
public abstract class LogUtil
{
    static
    {
        Loggers.init();
    }

    public static enum Level
    {
        NONE,
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public static String CONSOLE_LOG_FMT = "%d{HHmmss.SSS}%.-1level %thread @%c{0}, %m%n";

    /**
     * Initialize the logging system by loading a named config file with
     * some context properties set.
     * @param rtRoot  Will be passed to config file as RTROOT
     * @param progName    Will be passed to config file as PROGNAME
     * @param logLevel Will be passed to config file as LOGLEVEL
     * @param configFile  Name of XML config file to load
     */
    public static void initializeFromConfigFile(
            String rtRoot, String progName,
            Level logLevel, String configFile)
            throws JoranException, ExNoResource
    {
        // NB: getILoggerFactory causes the default configuration to be loaded.
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        context.reset();

        context.putProperty("PROGNAME", progName);
        context.putProperty("RTROOT", rtRoot);
        context.putProperty("LOGLEVEL", logLevel.name());

        URL configUrl = Thread.currentThread().getContextClassLoader()
                .getResource(configFile);

        if (configUrl == null) {
            // we have to check explicitly since Joran dies with NPE otherwise.
            // it's not impossible to continue since logback provides a fallback configurator.
            throw new ExNoResource("Can't locate logger configuration " + configFile);
        }

        configurator.doConfigure(configUrl);

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                DateFormat format = new SimpleDateFormat("yyyyMMdd");
                String strDate = format.format(new Date());
                LoggerFactory.getLogger(LogUtil.class).debug("TERMINATED " + strDate);
            }
        }));
    }

    /**
     * Create a console appender and attach it to the root logger. This will inherit
     * the log level of root (which is generally set by configuration, see
     * initializeFromConfigFile).
     */
    public static void startConsoleLogging()
    {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ConsoleAppender<ILoggingEvent> ca = new ConsoleAppender<ILoggingEvent>();
        ca.setContext(context);
        ca.setName("console");

        PatternLayoutEncoder pl = new PatternLayoutEncoder();
        pl.setContext(context);
        pl.setPattern(CONSOLE_LOG_FMT);
        pl.start();

        ca.setEncoder(pl);
        ca.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME)
                .addAppender(ca);
    }

    /**
     * Suppress the stack trace for the given throwable.
     *
     * This is useful to pass an abbreviated exception on to the logging subsystem.
     *
     * Example: l.warn("Oh noes! Bad {}", thing, LogUtil.suppress(myEx));
     */
    public static <T extends Throwable> T suppress(T throwable)
    {
        throwable.setStackTrace(new StackTraceElement[0]);
        return throwable;
    }

    /**
     * Suppress the stack trace if the throwable is an instance of one of the given
     * exception types.
     */
    public static <T extends Throwable> T suppress(T throwable, Class<?>... suppressTypes)
    {
        for (Class<?> clazz : suppressTypes) {
            if (clazz.isInstance(throwable)) {
                return suppress(throwable);
            }
        }
        return throwable;
    }

    private LogUtil() { /* private to enforce uninstantiability */ }
}
