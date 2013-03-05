package com.aerofs.daemon.core.notification;

import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnector;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IReactor;
import com.aerofs.base.ex.ExProtocolError;

public class ReactorConnector implements IReactor, IConnector
{
    private final RitualNotificationServer _notifier;

    /**
     * Whether the initial snapshot has been sent. Should be only accessed by the core thread
     */
    boolean _snapshotSent;

    public ReactorConnector(RitualNotificationServer notifier)
    {
        _notifier = notifier;
    }

    @Override
    public byte[][] react_(byte[] bs, int wirelen) throws Exception
    {
        throw new ExProtocolError("no incoming data is expected");
    }

    @Override
    public byte[][] getReactorPreamble_()
    {
        return null;
    }

    @Override
    public byte[][] getConnectorPreamble_()
    {
        return null;
    }

    @Override
    public void reactorDisconnected_()
    {
        _notifier.disconnected(this);
    }

    @Override
    public void connectorDisconnected_()
    {
        _notifier.disconnected(this);
    }

}
