package com.aerofs.daemon.transport.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.transport.lib.IPresenceLocation;
import com.aerofs.ids.DID;
import com.aerofs.testlib.AbstractTest;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

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
        Assert.assertEquals(location.transportType(), TransportType.LANTCP);
        Assert.assertEquals(location.did(), _did);
        Assert.assertEquals(location.socketAddress(), new InetSocketAddress(_host, _port));
    }

    @Test
    public void shouldExportLocation() throws Exception
    {
        Assert.assertEquals(location.exportLocation(), _host + ":" + _port);
    }

    @Test
    public void shouldExportJson() throws Exception
    {
        JsonObject json = location.toJson();
        Assert.assertTrue(json.has("version"));
        Assert.assertTrue(json.getAsJsonPrimitive("version").getAsInt() >= 100);

        Assert.assertEquals(json.getAsJsonPrimitive("transport").getAsString(), TransportType.LANTCP.toString());
        Assert.assertEquals(json.getAsJsonPrimitive("location").getAsString(), _host + ":" + _port);
    }

    @Test
    /**
     * This test is not specific to TCP
     * and I am not sure where to put the tested method.
     */
    public void shouldCheckVersion()
    {
        Assert.assertTrue(IPresenceLocation.versionCompatible(100, 100));
        Assert.assertTrue(IPresenceLocation.versionCompatible(105, 100));
        Assert.assertTrue(IPresenceLocation.versionCompatible(120, 100));
        Assert.assertTrue(IPresenceLocation.versionCompatible(199, 100));

        Assert.assertTrue(IPresenceLocation.versionCompatible(200, 200));
        Assert.assertTrue(IPresenceLocation.versionCompatible(223, 200));

        Assert.assertFalse(IPresenceLocation.versionCompatible(300, 200));
        Assert.assertFalse(IPresenceLocation.versionCompatible(300, 299));

        // Not sure here, do we want updated client to accept old locations?
        Assert.assertFalse(IPresenceLocation.versionCompatible(200, 300));
    }
}
