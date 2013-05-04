/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.CoreConstants;
import com.aerofs.base.ex.ISuppressStack;

/**
 * This class formats the throwable component of a logging event (if any exists).
 *
 * If the throwable should be suppressed, we simply return the stringified throwable.
 *
 * Otherwise we return the whole stack trace.
 *
 * The convert() method is invoked automatically by the logback appender based on
 * a custom conversion pattern in the config:
 * &lt;conversionRule conversionWord="suppressedEx"
 *   converterClass="com.aerofs.lib.ExceptionConverter" /&gt;
 */
public class ExceptionConverter extends ThrowableProxyConverter
{
    @Override
    public String convert(ILoggingEvent event)
    {
        if (event.getThrowableProxy() == null) { return CoreConstants.EMPTY_STRING; }

        final ThrowableProxy proxy = (ThrowableProxy)event.getThrowableProxy();
        final Throwable t = proxy.getThrowable();

        return shouldSuppressStack(t) ?
                    (t.toString() + "\n")
                    : super.throwableProxyToString(event.getThrowableProxy());
    }

    private boolean shouldSuppressStack(Throwable t)
    {
        return (t instanceof ISuppressStack) || (t instanceof java.net.SocketException);
    }
}
