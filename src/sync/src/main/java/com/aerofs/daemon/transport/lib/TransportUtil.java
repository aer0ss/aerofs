/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.transport.ExIOFailed;
import com.aerofs.daemon.transport.ExTransport;
import com.aerofs.lib.SystemUtil;
import com.aerofs.proto.Diagnostics.ChannelState;
import com.aerofs.proto.Diagnostics.PBInetSocketAddress;
import com.aerofs.proto.Diagnostics.ServerStatus;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.UnknownHostException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public abstract class TransportUtil
{
    private static final Logger l = LoggerFactory.getLogger(TransportUtil.class);

    private TransportUtil() { } // private to protect instantiation

    // Note (GS): In an ideal world, the CNameVerificationHandler would do some magic so that
    // channel.isChannelConnected() returns false until the CName is verified. Unfortunately, I
    // haven't found a way to do that. I'm able to delay firing the channelConnected event until
    // the CName is verified, but I can't make channel.isConnected() lie to you. So,
    // we check if a valid IChannelData attachment exists for the channel:
    public static boolean isChannelConnected(Channel channel)
    {
        return hasValidChannelData(channel);
    }

    public static void setChannelData(Channel channel, ChannelData data)
    {
        checkState(data.getRemoteDID() != null && data.getRemoteUserID() != null);
        channel.setAttachment(data);
    }

    public static ChannelData getChannelData(Channel channel)
    {
        Object a = channel.getAttachment();
        checkState(a != null && a instanceof ChannelData);
        return (ChannelData) a;
    }

    public static boolean hasValidChannelData(Channel channel)
    {
        Object a = channel.getAttachment();
        return a != null && a instanceof ChannelData;
    }

    public static ChannelState getChannelState(Channel channel)
    {
        if (channel.getCloseFuture().isDone()) {
            return ChannelState.CLOSED;
        } else if (hasValidChannelData(channel)) {
            return ChannelState.VERIFIED;
        } else {
            return ChannelState.CONNECTING;
        }
    }

    /**
     * Helpful logging method to print an {@link java.net.InetSocketAddress} in a consistent way
     *
     * @param a address to print
     * @return log string of the form: addr:port
     */
    public static String prettyPrint(InetSocketAddress a)
    {
        checkNotNull(a);
        return a.getAddress() + ":" + a.getPort();
    }

    public static PBInetSocketAddress fromInetSockAddress(InetSocketAddress a, boolean resolveName)
    {
        checkNotNull(a);

        PBInetSocketAddress.Builder builder = PBInetSocketAddress
                .newBuilder()
                .setHost(a.getHostName())
                .setPort(a.getPort());

        if (resolveName) {
            String resolved = getResolvedAddress(a);
            if (resolved != null) builder.setResolvedHost(resolved);
        }

        return builder.build();
    }

    /**
     * Attempts to return a resolved address, and if not, null
     */
    public static @Nullable String getResolvedAddress(InetSocketAddress a)
    {
        checkNotNull(a);

        InetAddress resolved = null;

        if (a.isUnresolved()) {
            String host = a.getHostName();
            try {
                resolved = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                l.warn("fail name resolution host:{}", host);
            }
        } else {
            resolved = a.getAddress();
        }

        return (resolved == null ? null : resolved.getHostAddress());
    }

    public static Socket newConnectedSocket(InetSocketAddress serverAddress, int ioTimeout)
            throws IOException
    {
        Socket s = null;
        try {
            InetSocketAddress address = serverAddress;
            if (address.isUnresolved()) {
                address = new InetSocketAddress(address.getHostName(), address.getPort());
            }

            s = new Socket();
            s.connect(address, ioTimeout);
            s.setSoTimeout(ioTimeout);
            return s;
        } catch (IOException e) {
            try {
                if (s!= null) s.close();
            } catch (IOException closeException) {
                l.warn("fail close socket err:{}", closeException.getMessage());
            }

            throw e;
        }
    }

    public static String getReachabilityErrorString(ServerStatus.Builder serverStatus, IOException e)
    {
        return getUserFacingIOExceptionMessage(e) + ": " + serverStatus.getServerAddress().getHost();
    }

    private static String getUserFacingIOExceptionMessage(IOException e)
    {
        if (e instanceof NoRouteToHostException) {
            return "no route to host";
        } else if (e instanceof UnknownHostException) {
            return "unknown host";
        } else {
            return e.getMessage();
        }
    }

    public static ExTransport newExTransportOrFatalOnError(String message, @Nullable Throwable cause)
    {
        if (cause == null) {
            return new ExIOFailed(message);
        } else if (cause instanceof ExTransport) {
            return (ExTransport) cause;
        } else if (cause instanceof Exception) {
            return new ExIOFailed(message, cause);
        } else {
            SystemUtil.fatal(cause); // this is an error of some kind - kill ourselves
            return null;
        }
    }

    public static String hexify(Channel channel)
    {
        return hexify(channel.getId());
    }

    public static String hexify(int num)
    {
        String hex = Integer.toHexString(num);
        return String.format("0x%1$8s", hex).replace(' ', '0');
    }
}
