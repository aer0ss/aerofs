package com.aerofs.daemon.transport.xmpp.zephyr.client.netty;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.tng.xmpp.zephyr.Constants;
import org.jboss.netty.channel.Channel;

public class ZephyrClientContext {
    private final DID _localDID;
    private final DID _remoteDID;
    private final Channel _channel;
    private int _localZid;
    private int _remoteZid;

    private boolean _bound;

    private long _bytesRead;
    private long _bytesWritten;

    public ZephyrClientContext(DID localDID, DID remoteDid, Channel channel)
    {
        assert localDID != null;
        assert remoteDid != null;
        assert channel != null;

        _localDID = localDID;
        _remoteDID = remoteDid;
        _channel = channel;
        _remoteZid = Constants.ZEPHYR_INVALID_CHAN_ID;
        _localZid = Constants.ZEPHYR_INVALID_CHAN_ID;

        _bytesRead = 0;
        _bytesWritten = 0;
    }

    public Channel getChannel()
    {
        return _channel;
    }

    public DID getLocalDID()
    {
        return _localDID;
    }

    public DID getRemoteDID_()
    {
        return _remoteDID;
    }

    public void setLocalZid_(int localZid)
    {
        _localZid = localZid;
    }

    public int getLocalZid_()
    {
        return _localZid;
    }

    public void setRemoteZid_(int remoteZid)
    {
        _remoteZid = remoteZid;
    }

    public int getRemoteZid_()
    {
        return _remoteZid;
    }

    public boolean isConnected_()
    {
        return _channel.isConnected();
    }

    public boolean isRegistered_()
    {
        return _localZid != Constants.ZEPHYR_INVALID_CHAN_ID;
    }

    public void setBound_(boolean value)
    {
        _bound = value;
    }

    public boolean isBound_()
    {
        return _bound;
    }

    public void incrementBytesRead_(long bytesRead)
    {
        _bytesRead += bytesRead;
    }

    public long getBytesRead_()
    {
        return _bytesRead;
    }

    public void incrementBytesWritten_(long bytesWritten)
    {
        _bytesWritten += bytesWritten;
    }

    public long getBytesWritten_()
    {
        return _bytesWritten;
    }

    private String getTinyDebugString_()
    {
        return _localDID + " (" + _localZid + ") -> " + _remoteDID + " (" + _remoteZid + ")";
    }

    @Override
    public String toString()
    {
        return "zc[" + getTinyDebugString_() + "]";
    }

    public String getDebugString_()
    {
        return "zc[" + getTinyDebugString_() + ": tx=" + _bytesWritten + " rx=" + _bytesRead + "]";
    }
}
