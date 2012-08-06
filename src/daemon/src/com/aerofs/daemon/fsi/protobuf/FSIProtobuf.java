package com.aerofs.daemon.fsi.protobuf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import com.aerofs.daemon.transport.lib.TCPProactorMT;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnection;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IConnector;
import com.aerofs.daemon.transport.lib.TCPProactorMT.IReactor;
import com.aerofs.lib.C;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;

public class FSIProtobuf implements TCPProactorMT.IConnectionManager
{
    private TCPProactorMT _proactor;

    public void init_() throws IOException
    {
        String addr = Cfg.db().get(Key.RITUAL_BIND_ADDR);

        InetAddress addrBind = addr.equals("*") ? null : InetAddress.getByName(addr);
        _proactor = new TCPProactorMT("fsi", this, addrBind, Cfg.port(Cfg.PortType.FSI),
                C.FSI_MAGIC, false, Integer.MAX_VALUE);
    }

    public void start_()
    {
        _proactor.start_();
    }

    @Override
    public IReactor newIncomingConnection(IConnection c, InetSocketAddress from)
    {
        return new FSIReactor();
    }

    @Override
    public IConnector newOutgoingConnection(IConnection c, InetSocketAddress to,
            Object cookie)
    {
        return null;
    }
}
