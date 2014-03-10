/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.daemon.transport.TransportLoggerSetup;
import org.junit.Ignore;
import org.junit.Test;

// FIXME (AG) : actually implement tests for the transport heartbeat handler
@Ignore
public final class TestHeartbeatHandler
{
    static
    {
        TransportLoggerSetup.init();
    }

    @Test
    public void shouldNotSendAHeartbeatIfThereIsConstantTrafficOnChannel()
            throws Exception
    {

    }

    @Test
    public void shouldSendHeartbeatIfThereIsNoTrafficOnChannel()
    {

    }

    @Test
    public void shouldRespondToHearbeatRequestWithAHeartbeatResponse()
    {

    }

    @Test
    public void shouldDisconnectChannelIfNoHeartbeatResponsesAreReceivedForMultipleConsequtiveHeartbeats()
    {

    }

    @Test
    public void shouldScheduleAHeartbeatWhenChannelIsConnected()
    {

    }

    @Test
    public void shouldResetHeartbeatCountIfAMatchingHeartbeatResponseIsReceived()
    {

    }
}
