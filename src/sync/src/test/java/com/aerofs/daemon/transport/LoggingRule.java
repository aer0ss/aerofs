/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;

public final class LoggingRule extends TestWatcher
{
    private final Logger l;

    public LoggingRule(Logger l) {this.l = l;}

    @Override
    protected void starting(Description description)
    {
        super.starting(description);
        l.info("STARTING {}", description.getMethodName());
    }

    @Override
    protected void finished(Description description)
    {
        l.info("FINISHING {}", description.getMethodName());
        super.finished(description);
    }

    @Override
    protected void failed(Throwable e, Description description)
    {
        l.info("FAILED {}", description.getMethodName(), e);
        super.failed(e, description);
    }
}
