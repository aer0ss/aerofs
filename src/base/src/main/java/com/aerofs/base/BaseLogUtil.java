/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.base;

public class BaseLogUtil
{
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
        Throwable t = throwable;
        do {
            t.setStackTrace(new StackTraceElement[0]);
            t = t.getCause();
        } while (t != null);

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
}
