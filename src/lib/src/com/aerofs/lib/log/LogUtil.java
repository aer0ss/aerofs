package com.aerofs.lib.log;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.aerofs.base.Loggers;
<<<<<<< HEAD
import com.aerofs.lib.LibParam;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.apache.log4j.varia.NullAppender;
=======
import org.slf4j.LoggerFactory;
>>>>>>> 209ffae... Convert all logging to logback / slf4j.

import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Helpers for dealing with logback
 * IMPORTANT: DO NOT USE "L" in here!!!
 */
// TODO: Figure out if the above IMPORTANT is still true in a logback world?
// TODO: Is this better factored straight into Main?
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

    /**
     * Initialize the logging system by loading a named config file with
     * some context properties set.
     * @param rtRoot  Will be passed to config file as RTROOT
     * @param prog    Will be passed to config file as PROGNAME
     * @param logLevel Will be passed to config file as LOGLEVEL
     * @param configFile  Name of XML config file to load
     * FIXME: cleaner to handle JoranException here?
     */
    public static void initializeFromConfigFile(
            String rtRoot, String prog,
            Level logLevel, String configFile)
            throws JoranException
    {
        // NB: getILoggerFactory causes the default configuration to be loaded.
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        context.reset();

        context.putProperty("PROGNAME", prog);
        context.putProperty("RTROOT", rtRoot);
        context.putProperty("LOGLEVEL", logLevel.name());

<<<<<<< HEAD
    public static boolean initializeLoggingFromPropertiesFile()
    {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL url = cl.getResource(LOG4J_PROPERTIES_FILE);
        if (url != null) {
            PropertyConfigurator.configure(url);
            return true;
        }

        return false;
    }

    public static void initializeDefaultLoggingProperties(String rtRoot, String logfile, Level logLevel)
        throws IOException
    {
        if (logfile.equals(LibParam.SH_NAME)) {
            getRootLogger().addAppender(new NullAppender());
        } else {
            setupAndAddFileAppender(rtRoot + File.separator + logfile + LibParam.LOG_FILE_EXT);
        }
=======
        URL configUrl = Thread.currentThread().getContextClassLoader()
                .getResource(configFile);
>>>>>>> 209ffae... Convert all logging to logback / slf4j.

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
     * Suppress stack trace for the given throwable.
     *
     * This is useful to pass an abbreviated exception on to the logging subsystem.
     *
     * Example: l.warn("Oh noes! Bad {}", thing, LogUtil.suppress(myEx));
     *
     */
    public static <T extends Throwable> T suppress(T thro )
    {
        thro.setStackTrace(new StackTraceElement[0]);
        return thro;
    }

    /**
     * Suppress stack trace if the throwable is an instance of one of the given
     * exception types.
     *
     */
    // TODO(jP): Varargs and Class<T> don't play nicely; suppressTypes should be an array
    // of Class<T> to be self-documenting.
    // However that breaks the ability to call this method without creating an
    // explicit array of Throwable.
    public static <T extends Throwable> T suppress(T thro, Class... suppressTypes )
    {
        for (Class clazz : suppressTypes) {
            if (clazz.isInstance(thro)) {
                return suppress(thro);
            }
        }
        return thro;
    }

    private LogUtil() { /* private to enforce uninstantiability */ }
}
