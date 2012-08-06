/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.CoreEvent;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.ExInvalidTransition;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IState;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IStateContext;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.IStateEvent;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.StateMachine;
import com.aerofs.lib.PackageLoggingOverride;
import org.apache.log4j.Level;

public class LoggingOverride extends PackageLoggingOverride
{
    private LoggingOverride()
    {
        // private to enforce uninstantiability
    }

    public static synchronized void setLogLevels_()
    {
        if (_set) return;

        Class<?>[] classes = new Class<?>[] {
            IState.class,
            IStateContext.class,
            IStateEvent.class,
            StateMachine.class,
            CoreEvent.class,
            ExInvalidTransition.class,
            ClientConstants.class,
            MultiByteBufferInputStream.class,
            Message.class,
            ClientDispatcher.class,
            ZephyrClientContext.class,
            ZephyrClientManager.class,
            ZephyrClientSpec.class,
            ZephyrClientUtil.class,
            ZephyrClientEvent.class,
            ZephyrClientState.class,
            ExAbortState.class,
            ExBadMessage.class,
            ExInvalidZephyrClient.class
        };

        overrideLogLevels(classes, Level.INFO);

        _set = true;
    }

    private static boolean _set = false;
}