package com.aerofs.lib;

import com.aerofs.base.Loggers;
import com.aerofs.labeling.L;
import com.aerofs.lib.cfg.Cfg;
import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.DefaultThrowableRenderer;
import org.apache.log4j.Layout;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.FormattingInfo;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableRenderer;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.apache.log4j.varia.NullAppender;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Helpers for dealing with log4j
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

    private static final String PROP_RTROOT = "aerofs.rtroot";
    private static final String PROP_APP = "aerofs.app";
    private static final String LOG4J_PROPERTIES_FILE = "aerofs-log4j.properties";

    private static final String[] PKG_SUFFIXES = new String[] {
        "com.aerofs.daemon.core.",
        "com.aerofs.daemon.",
        "com.aerofs.",
    };
    private static final String[] PKG_SUFFIX_REPLACEMENTS = new String[] {
        "~",
        "!",
        "@",
    };

    private LogUtil()
    {
        // private to enforce uninstantiability
    }

    private static String makeShortName(String name)
    {
        for (int i = 0; i < PKG_SUFFIXES.length; i++) {
            if (name.startsWith(PKG_SUFFIXES[i])) {
                name = PKG_SUFFIX_REPLACEMENTS[i] +
                    name.substring(PKG_SUFFIXES[i].length());
                break;
            }
        }
        return name;
    }

    private static Layout newPatternLayout(String pattern)
    {
        return new ShorteningPatternLayout(pattern);
    }

    private static class ShorteningPatternLayout extends PatternLayout
    {
        public ShorteningPatternLayout(String pattern)
        {
            super(pattern);
        }

        @Override
        protected PatternParser createPatternParser(String pattern)
        {
            return new PatternParser(pattern) {
                @Override
                protected void finalizeConverter(char c)
                {
                    PatternConverter pc;
                    switch (c) {
                    case 'c':
                        pc = new CategoryPatternConverter(formattingInfo,
                                extractPrecisionOption());
                        currentLiteral.setLength(0);
                        break;

                    default:
                        super.finalizeConverter(c);
                        return;
                    }
                    addConverter(pc);
                }
            };

        }
    }

    private static class CategoryPatternConverter extends PatternConverter
    {
        int _precision;

        CategoryPatternConverter(FormattingInfo formattingInfo, int precision)
        {
            super(formattingInfo);
            _precision =  precision;
        }

        String getFullyQualifiedName(LoggingEvent event)
        {
            String name = event.getLoggerName();
            return makeShortName(name);
        }

        @Override
        public String convert(LoggingEvent event)
        {
            String n = getFullyQualifiedName(event);
            if (_precision <= 0) {
                return n;
            } else {
                int len = n.length();

                // We substract 1 from 'len' when assigning to 'end' to avoid out of
                // bounds exception in return r.substring(end+1, len). This can happen if
                // precision is 1 and the category name ends with a dot.
                int end = len -1 ;
                for(int i = _precision; i > 0; i--) {
                    end = n.lastIndexOf('.', end-1);
                    if(end == -1)
                        return n;
                }
                return n.substring(end+1, len);
            }
        }
    }

    private static ThrowableRenderer newThrowableRenderer()
    {
        return new AeroThrowableRenderer();
    }

    private static class AeroThrowableRenderer implements ThrowableRenderer
    {
        private final ThrowableRenderer _default = new DefaultThrowableRenderer();

        @Override
        public String[] doRender(Throwable t)
        {
            if (Util.shouldPrintStackTrace(t)) {
                return _default.doRender(t);
            } else {
                return new String[] { t.toString() };
            }
        }
    }

    public static void setLevel(Class<?> clazz, Level level)
    {
        Logger.getLogger(clazz).setLevel(level.getLog4jLevel());
    }

    public static void setLevel(String clazzName, Level level)
    {
        Logger.getLogger(clazzName).setLevel(level.getLog4jLevel());
    }

    public static void disableLog4J()
    {
        Logger.getRootLogger().removeAllAppenders();
        Logger.getRootLogger().addAppender(new NullAppender());
    }

    /**
     * @param app the log file will be named "<app>.log"
     */
    public static void initLog4J(String rtRoot, String app)
        throws IOException
    {
        System.setProperty(PROP_RTROOT, rtRoot);
        System.setProperty(PROP_APP, app);

        if (L.get().isStaging()) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            URL url = cl.getResource(LOG4J_PROPERTIES_FILE);
            if (url != null) {
                PropertyConfigurator.configure(url);
                return;
            }
        }

        if (app.equals(Param.SH_NAME)) {
            Logger.getRootLogger().addAppender(new NullAppender());
        } else {
            setupLog4JLayoutAndAppenders(rtRoot + File.separator + app + Param.LOG_FILE_EXT,
                    L.get().isStaging(), true);
        }

        String strDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
        l.debug(app + " ========================================================\n" +
            Cfg.ver() + (L.get().isStaging() ? " staging " : " ") +
            strDate + " " + AppRoot.abs() + " " + new File(rtRoot).getAbsolutePath());

        if (Cfg.useProfiler()) {
            l.debug("profiler: " + Cfg.profilerStartingThreshold());
        }

        Logger.getRootLogger().setLevel(
                        Cfg.lotsOfLog(rtRoot)
                        ?
                        org.apache.log4j.Level.DEBUG
                        :
                        org.apache.log4j.Level.INFO);

        // print termination banner
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run()
            {
                DateFormat format = new SimpleDateFormat("yyyyMMdd");
                String strDate = format.format(new Date());
                l.debug("TERMINATED " + strDate);
            }
        }));
    }

    private static Layout getLogLayout(boolean fullyQualified)
    {
        LoggerRepository repo = LogManager.getLoggerRepository();
        if (repo instanceof ThrowableRendererSupport) {
            ThrowableRendererSupport trs = ((ThrowableRendererSupport)repo);
            trs.setThrowableRenderer(LogUtil.newThrowableRenderer());
        }

        String patternLayoutString = "%d{HHmmss.SSS}%-.1p %t ";
        patternLayoutString += fullyQualified ? "%c" : "%C{1}";
        patternLayoutString += ", %m%n";
        Layout layout = LogUtil.newPatternLayout(patternLayoutString);
        return layout;
    }

    private static void setupLog4JLayoutAndAppenders(String logfile, boolean logToConsole,
            boolean fullyQualified)
            throws IOException
    {
        Layout layout = getLogLayout(fullyQualified);

        List<Appender> appenders = new ArrayList<Appender>(2); // max number of appenders
        appenders.add(new DailyRollingFileAppender(layout, logfile, "'.'yyyyMMdd"));
        if (logToConsole) {
            appenders.add(new ConsoleAppender(layout));
        }

        for (Appender appender : appenders) {
            Logger.getRootLogger().addAppender(appender);
        }
    }
}
