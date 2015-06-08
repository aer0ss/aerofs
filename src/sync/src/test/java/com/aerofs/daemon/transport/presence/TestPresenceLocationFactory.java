package com.aerofs.daemon.transport.presence;

import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.ids.DID;
import com.aerofs.testlib.AbstractTest;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;


public class TestPresenceLocationFactory extends AbstractTest
{
    DID _did;

    @Before
    public void setUp() throws Exception {
        _did = DID.generate();
    }

    @Test
    public void shouldProducePresenceLocation() throws Exception
    {
        TCPPresenceLocation tcpPresenceLocation = PresenceLocationFactory.getTCPPresenceLocation(_did, TCPPresenceLocation.VERSION, "1.2.3.4:12345");

        Assert.assertEquals(tcpPresenceLocation.did(), _did);
        Assert.assertEquals(tcpPresenceLocation.socketAddress(), new InetSocketAddress("1.2.3.4", 12345));
    }

    @Test
    public void shouldImportFromJson() throws Exception
    {
        JsonObject json = new JsonObject();
        json.addProperty("location", "1.2.3.4:12345");
        json.addProperty("version", TCPPresenceLocation.VERSION);
        json.addProperty("transport", TransportType.LANTCP.toString());

        // JSON -> fromJson() -> Location
        TCPPresenceLocation tcpPresenceLocation = (TCPPresenceLocation)PresenceLocationFactory.fromJson(_did, json);

        Assert.assertEquals(tcpPresenceLocation.did(), _did);
        Assert.assertEquals(tcpPresenceLocation.transportType(), TransportType.LANTCP);
        Assert.assertEquals(tcpPresenceLocation.socketAddress(), new InetSocketAddress("1.2.3.4", 12345));
        Assert.assertEquals(tcpPresenceLocation.version(), TCPPresenceLocation.VERSION);
    }

    /**
     * The factory should return null for incomplete json objects
     */
    @Test
    public void shouldRefuseIncomplete() throws Exception
    {
        JsonObject json = new JsonObject();
        json.addProperty("location", "1.2.3.4:12345");
        json.addProperty("transport", TransportType.LANTCP.toString());
        // Version is missing

        Assert.assertNull(PresenceLocationFactory.fromJson(_did, json));

        JsonObject json2 = new JsonObject();
        json.addProperty("version", TCPPresenceLocation.VERSION);
        json2.addProperty("transport", TransportType.LANTCP.toString());
        // Location is missing

        Assert.assertNull(PresenceLocationFactory.fromJson(_did, json2));
    }

    /**
     * The factory should return null if the data is invalid
     */
    @Test
    public void shouldRefuseInvalid() throws Exception
    {
        JsonObject json = new JsonObject();
        json.addProperty("version", TCPPresenceLocation.VERSION);
        json.addProperty("location", "1.2.3.4:12345");
        json.addProperty("transport", "UNICORN");
        // I just hope we will not create the UNICORN transport one day...

        Assert.assertNull(PresenceLocationFactory.fromJson(_did, json));

        JsonObject json2 = new JsonObject();
        json2.addProperty("version", TCPPresenceLocation.VERSION);
        json2.addProperty("location", "1.2.a3.4:12345");
        json2.addProperty("transport", TransportType.LANTCP.toString());
        // Invalid socket address

        Assert.assertNull(PresenceLocationFactory.fromJson(_did, json2));
    }

    @Test
    public void shouldImportExportFromJson() throws Exception
    {
        TCPPresenceLocation tcpPresenceLocation = PresenceLocationFactory.getTCPPresenceLocation(_did, TCPPresenceLocation.VERSION, "1.2.3.4:12345");

        // Location -> toJson() -> fromJson() -> Location
        TCPPresenceLocation tcpPresenceLocation2 = (TCPPresenceLocation)PresenceLocationFactory.fromJson(tcpPresenceLocation.did(), tcpPresenceLocation.toJson());

        Assert.assertEquals(tcpPresenceLocation2.did(), tcpPresenceLocation.did());
        Assert.assertEquals(tcpPresenceLocation2.socketAddress(), tcpPresenceLocation.socketAddress());
        Assert.assertEquals(tcpPresenceLocation2.version(), tcpPresenceLocation.version());
    }

    @Test
    /**
     * For an incompatible version, the factory should return null
     */
    public void shouldRefuseIncompatible() throws Exception
    {
        // Incompatible version
        TCPPresenceLocation tcpPresenceLocation = PresenceLocationFactory.getTCPPresenceLocation(_did, TCPPresenceLocation.VERSION + 100, "1.2.3.4:12345");

        Assert.assertNull(tcpPresenceLocation);
    }
}
