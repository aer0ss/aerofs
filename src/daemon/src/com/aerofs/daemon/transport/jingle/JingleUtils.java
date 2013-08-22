/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.BaseParam.XMPP;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.j.Jid;
import org.jboss.netty.channel.ChannelState;

import static com.aerofs.daemon.transport.jingle.JingleUtils.DownstreamChannelEvent.BIND;
import static com.aerofs.daemon.transport.jingle.JingleUtils.DownstreamChannelEvent.CLOSE;
import static com.aerofs.daemon.transport.jingle.JingleUtils.DownstreamChannelEvent.CONNECT;
import static com.aerofs.daemon.transport.jingle.JingleUtils.DownstreamChannelEvent.DISCONNECT;
import static com.aerofs.daemon.transport.jingle.JingleUtils.DownstreamChannelEvent.INTEREST_OPS;
import static com.aerofs.daemon.transport.jingle.JingleUtils.DownstreamChannelEvent.UNBIND;

public class JingleUtils
{
    private static final String JINGLE_RESOURCE_NAME = "u";

    /**
     * Convert a DID> to an XMPP JID valid on the AeroFS XMPP server
     *
     * @param did {@link com.aerofs.base.id.DID} to convert
     * @return a valid XMPP user id of the form: {$user}@{$domain}/{$resource}
     */
    static Jid did2jid(DID did)
    {
        return new Jid(JabberID.did2user(did), XMPP.SERVER_DOMAIN, JINGLE_RESOURCE_NAME);
    }

    static DID jid2did(Jid jid) throws ExFormatError
    {
        return JabberID.user2did(jid.node());
    }

    public static enum DownstreamChannelEvent
    {
        CLOSE,        // Close the channel.
        BIND,         // Bind the channel to the specified local address. Value is a SocketAddress.
        UNBIND,       // Unbind the channel from the current local address
        CONNECT,      // Connect the channel to the specified remote address. Value is a SocketAddress.
        DISCONNECT,   // Disconnect the channel from the current remote address.
        INTEREST_OPS  // Change the interestOps of the channel. Value is an integer.
    }

    /**
     * Helper method to parse a downstream channel event according to its state and value.
     * See the ChannelState doc for more information.
     * (http://netty.io/3.6/api/org/jboss/netty/channel/ChannelState.html)
     */
    public static DownstreamChannelEvent parseDownstreamEvent(ChannelState state, Object value)
    {
        switch (state) {
        case OPEN:
            if (Boolean.FALSE.equals(value)) return CLOSE;
            break;
        case BOUND:
            if (value != null) return BIND; else return UNBIND;
        case CONNECTED:
            if (value != null) return CONNECT; else return DISCONNECT;
        case INTEREST_OPS:
            return INTEREST_OPS;
        }
        throw new IllegalArgumentException("could not parse state: " + state + " val: " + value);
    }
}
