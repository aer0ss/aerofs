package com.aerofs.lib;

import org.apache.log4j.DefaultThrowableRenderer;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.FormattingInfo;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableRenderer;

/**
 * Helpers for dealing with log4j
 */
class LogUtil
{
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

    private LogUtil() {}

    static Layout newPatternLayout(String pattern)
    {
        return new ShorteningPatternLayout(pattern);
    }

    static Logger getLogger(Class<?> c)
    {
        return Logger.getLogger(c.getName());
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

    static ThrowableRenderer newThrowableRenderer()
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
}
