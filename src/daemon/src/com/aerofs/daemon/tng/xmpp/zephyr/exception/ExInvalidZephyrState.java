/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.exception;

public class ExInvalidZephyrState extends ExZephyrFatal
{
    private static final long serialVersionUID = 1L;

    public ExInvalidZephyrState(Enum<?> state, String message)
    {
        super("At state " + state + ": " + message);
    }

    public ExInvalidZephyrState(String message)
    {
        super(message);
    }
}
