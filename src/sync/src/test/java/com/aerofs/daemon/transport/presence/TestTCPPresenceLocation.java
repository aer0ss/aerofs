package com.aerofs.daemon.transport.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.junit.Assert.assertEquals;

public class TestTCPPresenceLocation extends AbstractTest
{
    String _host = "127.0.0.1";
    int _port = 12345;
    TCPPresenceLocation location;

    @Before
    public void setUp() throws Exception
    {
        location = new TCPPresenceLocation(InetAddress.getByName(_host), _port);
    }

    @Test
    public void shouldStoreValues() throws Exception
    {
        assertEquals(location.transportType(), TransportType.LANTCP);
        assertEquals(location.socketAddress(), new InetSocketAddress(_host, _port));
    }

    @Test
    public void shouldExportLocation() throws Exception
    {
        assertEquals(location.exportLocation(), _host + ":" + _port);
    }

    @Test
    public void shouldParseExported() throws Exception {
        assertEquals(location, TCPPresenceLocation.fromExportedLocation(location.exportLocation()));
    }
}
