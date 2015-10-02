package com.aerofs.daemon.transport.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.ids.DID;
import com.aerofs.testlib.AbstractTest;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;

public class TestTCPPresenceLocation extends AbstractTest
{
    DID _did;
    String _host = "127.0.0.1";
    int _port = 12345;
    TCPPresenceLocation location;

    @Before
    public void setUp() throws Exception
    {
        _did = DID.generate();
        location = new TCPPresenceLocation(_did, InetAddress.getByName(_host), _port);
    }

    @Test
    public void shouldStoreValues() throws Exception
    {
        assertEquals(location.transportType(), TransportType.LANTCP);
        assertEquals(location.did(), _did);
        assertEquals(location.socketAddress(), new InetSocketAddress(_host, _port));
    }

    @Test
    public void shouldExportLocation() throws Exception
    {
        assertEquals(location.exportLocation(), _host + ":" + _port);
    }

    @Test
    public void shouldParseExported() throws Exception {
        assertEquals(location, TCPPresenceLocation.fromExportedLocation(_did, location.exportLocation()));
    }
}
