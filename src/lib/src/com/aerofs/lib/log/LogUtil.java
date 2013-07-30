package com.aerofs.lib.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.rolling.DefaultTimeBasedFileNamingAndTriggeringPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import org.slf4j.LoggerFactory;

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

    /**  Suffix used when rolling daily log files */
    private final static String DATE_SUFFIX = ".%d{yyyyMMdd}";
    /**  maximum number of archived log files to keep */
    private final static int MAX_HISTORY = 5;

    /**
     * Initialize the logging system by building a configuration context and
     * replacing whatever was loaded automatically.
     */
    public static void initialize(String rtRoot, String progName, Level logLevel, boolean consoleOutput)
            throws JoranException, ExNoResource
    {
        configure(logLevel, rtRoot + "/" + progName + ".log", consoleOutput);

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
     * Programmatically configure a logging environment. The xml configurator insists
     * on initializing appenders inline which has undesirable side effects (like leaving
     * empty files in approot).
     */
    private static void configure(Level logLevel, String filename, boolean consoleOutput)
    {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        // -- Weird and subtle warning --
        // Daily file rollover requires
        // - triggering policy: when to roll over (i.e., daily)
        // - rolling pollicy: what to do when the trigger fires
        // and these two need to be linked up to each other reflexively.
        DefaultTimeBasedFileNamingAndTriggeringPolicy<ILoggingEvent> trigger
                = new DefaultTimeBasedFileNamingAndTriggeringPolicy<ILoggingEvent>();
        trigger.setContext(context);

        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<ILoggingEvent>();
        rollingPolicy.setContext(context);
        rollingPolicy.setFileNamePattern(filename + DATE_SUFFIX);
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(trigger);
        rollingPolicy.setMaxHistory(MAX_HISTORY);
        rollingPolicy.setCleanHistoryOnStart(true);

        trigger.setTimeBasedRollingPolicy(rollingPolicy);

        // The appender controls writing log messages to a destination, in this case a file.
        RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<ILoggingEvent>();
        appender.setAppend(true);
        appender.setEncoder(newEncoder(context));
        appender.setFile(filename);
        appender.setName("LOGFILE");
        appender.setPrudent(false);
        appender.setRollingPolicy(rollingPolicy);
        appender.setTriggeringPolicy(trigger);
        appender.setContext(context);

        // finally, link the rolling policy object up to it's parent - the appender. sigh.
        rollingPolicy.setParent(appender);
        rollingPolicy.start();
        appender.start();

        // Finally, we have all the components. Set the root logger to use the new appender:
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(getLevel(logLevel));
        rootLogger.addAppender(appender);

        if (consoleOutput) {
            ConsoleAppender<ILoggingEvent> console = new ConsoleAppender<ILoggingEvent>();
            console.setContext(context);
            console.setEncoder(newEncoder(context));
            console.start();
            rootLogger.addAppender(console);
        }
    }

    private static PatternLayoutEncoder newEncoder(LoggerContext context)
    {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%d{HHmmss.SSS}%.-1level %thread @%c{0}, %m%n");
        encoder.setContext(context);
        encoder.start();
        return encoder;
    }

    private static ch.qos.logback.classic.Level getLevel(Level logLevel)
    {
        if (logLevel == Level.ERROR)      return ch.qos.logback.classic.Level.ERROR;
        else if (logLevel == Level.WARN)  return ch.qos.logback.classic.Level.WARN;
        else if (logLevel == Level.INFO)  return ch.qos.logback.classic.Level.INFO;
        else if (logLevel == Level.DEBUG) return ch.qos.logback.classic.Level.DEBUG;
        else if (logLevel == Level.TRACE) return ch.qos.logback.classic.Level.TRACE;
        else if (logLevel == Level.NONE)  return ch.qos.logback.classic.Level.OFF;
        else throw new IllegalArgumentException("Illegal log level " + logLevel.toString());
    }

    /**
     * Suppress the stack trace for the given throwable.
     *
     * This is useful to pass an abbreviated exception on to the logging subsystem.
     *
     * Example: l.warn("Oh noes! Bad {}", thing, LogUtil.suppress(myEx));
     *
     * TODO(jP): This should replace usage of Util.e() throughout. No new uses of Util.e!
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
