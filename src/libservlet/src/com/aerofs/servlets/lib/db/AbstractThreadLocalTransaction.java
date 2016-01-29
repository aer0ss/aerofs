/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.servlets.lib.db;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

/**
 * Abstract class for thread local transactions. This class is here to de-dup code that is shared
 * between the different types of thread local transaction derived classes.
 */
public abstract class AbstractThreadLocalTransaction<T extends Throwable>
{
    private static final Logger l = Loggers.getLogger(AbstractThreadLocalTransaction.class);

    protected abstract boolean isInTransaction();
    protected abstract void rollback() throws T;

    protected void handleExceptionHelper()
    {
        try {
            if (isInTransaction()) {
                rollback();
            }
        } catch (Throwable e) {
            l.error("Exception caught when trying to end transaction: ", e);
            // Do nothing. We don't want to throw further exceptions here.
        }
    }
}