package com.aerofs.base.net;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.CNameVerificationHandler;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.ssl.NotSslRecordException;
import org.jboss.netty.channel.ChannelState;

import static com.aerofs.base.net.NettyUtil.DownstreamChannelEvent.*;

public class NettyUtil
{
    private NettyUtil() {}

    public static CNameVerificationHandler newCNameVerificationHandler(CNameListener listener,
            UserID localuser, DID localdid)
    {
        CNameVerificationHandler cnameHandler = new CNameVerificationHandler(localuser, localdid);
        cnameHandler.setListener(listener);
        return cnameHandler;
    }

    public static Throwable truncateMessageIfNecessary(Throwable e)
    {
        if (e instanceof NotSslRecordException) {
            // NotSslRecordException.getMessage() includes the entire message, which can be huge.
            // For example, "not an SSL/TLS record: df77a927b10cd..." can be kilobytes long.
            // Therefore, we truncate it.
            return new NotSslRecordException(e.getMessage().substring(0, 30) + "... (truncated)");
        } else {
            return e;
        }
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

    public static byte[] toByteArray(ChannelBuffer cb)
    {
        if (cb.hasArray() && cb.arrayOffset() == 0 &&
                cb.readerIndex() == 0 && cb.writerIndex() == cb.array().length) {
            return cb.array();
        } else {
            byte[] array = new byte[cb.readableBytes()];
            cb.getBytes(cb.readerIndex(), array);
            return array;
        }
    }
}
