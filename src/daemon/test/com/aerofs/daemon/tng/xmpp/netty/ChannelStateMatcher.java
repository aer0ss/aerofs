package com.aerofs.daemon.tng.xmpp.netty;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.mockito.ArgumentMatcher;

/**
 * Argument matcher that checks the event type (ChannelState) of a ChannelStateEvent
 */
public class ChannelStateMatcher extends ArgumentMatcher<ChannelEvent> {

    private final ChannelState _state;

    public ChannelStateMatcher(ChannelState state)
    {
        _state = state;
    }

    @Override
    public boolean matches(Object argument)
    {
        if (argument instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) argument;
            return event.getState().equals(_state);
        }
        return false;
    }

}