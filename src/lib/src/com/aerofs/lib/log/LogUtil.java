package com.aerofs.lib.log;

import com.aerofs.base.Loggers;
import com.aerofs.lib.Param;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.apache.log4j.varia.NullAppender;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Helpers for dealing with log4j
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
        NONE  (org.apache.log4j.Level.OFF  ),
        TRACE (org.apache.log4j.Level.TRACE),
        DEBUG (org.apache.log4j.Level.DEBUG),
        INFO  (org.apache.log4j.Level.INFO ),
        WARN  (org.apache.log4j.Level.WARN ),
        ERROR (org.apache.log4j.Level.ERROR);

        private final org.apache.log4j.Level _level;

        private Level(org.apache.log4j.Level level)
        {
            this._level = level;
        }

        private org.apache.log4j.Level getLog4jLevel()
        {
            return _level;
        }
    }

    private static final org.slf4j.Logger l = org.slf4j.LoggerFactory.getLogger(LogUtil.class);

    private static final String LOG4J_PROPERTIES_FILE = "aerofs-log4j.properties";

    private LogUtil()
    {
        // private to enforce uninstantiability
    }

    private static Logger getRootLogger()
    {
        return Logger.getRootLogger();
    }

    public static void setLevel(Class<?> clazz, Level level)
    {
        Logger.getLogger(clazz).setLevel(level.getLog4jLevel());
    }

    public static void setLevel(String className, Level level)
    {
        Logger.getLogger(className).setLevel(level.getLog4jLevel());
    }

    private static void resetLogging()
    {
        getRootLogger().removeAllAppenders();
    }

    public static void disableLogging()
    {
        resetLogging();
        getRootLogger().addAppender(new NullAppender());
    }

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
        if (logfile.equals(Param.SH_NAME)) {
            getRootLogger().addAppender(new NullAppender());
        } else {
            setupAndAddFileAppender(rtRoot + File.separator + logfile + Param.LOG_FILE_EXT);
        }

        getRootLogger().setLevel(logLevel.getLog4jLevel());

        // print termination banner
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                DateFormat format = new SimpleDateFormat("yyyyMMdd");
                String strDate = format.format(new Date());
                l.debug("TERMINATED " + strDate);
            }
        }));
    }

    private static Layout getLogLayout()
    {
        LoggerRepository repo = LogManager.getLoggerRepository();
        if (repo instanceof ThrowableRendererSupport) {
            ThrowableRendererSupport trs = ((ThrowableRendererSupport)repo);
            trs.setThrowableRenderer(new AeroThrowableRenderer());
        }

        final String patternLayoutString = "%d{HHmmss.SSS}%-.1p %t %c, %m%n";
        Layout layout = new ShorteningPatternLayout(patternLayoutString);
        return layout;
    }

    private static void setupAndAddFileAppender(String logfile)
            throws IOException
    {
        getRootLogger().addAppender(new DailyRollingFileAppender(getLogLayout(), logfile, "'.'yyyyMMdd"));
    }

    public static void setupAndAddConsoleAppender()
    {
        getRootLogger().addAppender(new ConsoleAppender(getLogLayout()));
    }
}
