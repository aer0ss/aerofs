/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio;

/**
 * An instance of this class should be thrown when a state function wants to
 * abort early. This allows developers to run any cleanup code for a state function
 * using a standard try-catch block.
 */
class ExAbortState extends Exception
{
    ExAbortState(String abortMsg, Exception abortException)
    {
        assert abortMsg != null && abortException != null :
            ("cannot construct " + this.getClass().getSimpleName() + " with null params");

        _abortMsg = abortMsg;
        _abortException = abortException;
    }

    public String getAbortMsg()
    {
        return _abortMsg;
    }

    public Exception getAbortException()
    {
        return _abortException;
    }

    /** message describing the abort cause */
    private final String _abortMsg;

    /** exception that caused the abort */
    private final Exception _abortException;

    /** serialization id */
    private static final long serialVersionUID = 1L;
}
