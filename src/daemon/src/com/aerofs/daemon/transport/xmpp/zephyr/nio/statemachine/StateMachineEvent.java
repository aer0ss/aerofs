/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.nio.statemachine;

import javax.annotation.Nullable;

/**
 *
 */
public class StateMachineEvent
{
    /**
     *
     * @param type
     */
    public StateMachineEvent(IStateEventType type)
    {
        _type = type;
        _data = null;
    }

    /**
     *
     * @param type
     * @param data
     */
    public StateMachineEvent(IStateEventType type, @Nullable Object data)
    {
        _type = type;
        _data = data;
    }

    /**
     *
     * @return
     */
    public IStateEventType type()
    {
        return _type;
    }

    /**
     *
     * @return
     */
    @Nullable
    public Object data()
    {
        return _data;
    }

    public String toString()
    {
        return "ev t:" + _type + " d:" + _data;
    }

    private final IStateEventType _type;
    private final Object _data;
}
