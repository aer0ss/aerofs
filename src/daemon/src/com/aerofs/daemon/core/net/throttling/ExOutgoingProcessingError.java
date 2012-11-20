package com.aerofs.daemon.core.net.throttling;

/**
 * Exception thrown when a {@link ILimiter} cannot process_ a {@link Outgoing}
 * No need to put this class under jni.ex as it's only used internally
 */
class ExOutgoingProcessingError extends ExThrottling
{
    private static final long serialVersionUID = 1;

    /**
     * <code>Outgoing</code> that caused the processing exception
     */
    private final Outgoing _ck;

    /**
     * Original exception thrown while processing the <code>Outgoing</code>
     */
    private final Exception _e;

    /**
     * Constructs an <code>ExOutgoingProcessingError</code> given a
     * <code>Outgoing</code> and an <code>Exception</code>
     *
     * @param ck <code>Outgoing</code> that caused the processing exception
     * @param cause exception thrown while processing the <code>Outgoing</code>
     */
    public ExOutgoingProcessingError(Outgoing ck, Exception cause)
    {
        super("fail process outgoing pkt:" + ck, cause);

        _ck = ck;
        _e = cause;
    }

    /**
     * @return <code>Outgoing</code> that caused the processing exception
     */
    public Outgoing getCk()
    {
        return _ck;
    }

    /**
     * @return exception thrown while processing the <code>Outgoing</code>
     */
    public Exception getEx()
    {
        return _e;
    }
}
