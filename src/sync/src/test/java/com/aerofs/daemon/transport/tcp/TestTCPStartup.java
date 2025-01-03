package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.daemon.core.net.TransportFactory;
import com.aerofs.daemon.transport.MockCA;
import com.aerofs.daemon.transport.TransportResource;
import com.aerofs.defects.AutoDefect;
import com.aerofs.defects.DefectFactory;
import com.aerofs.defects.MockDefects;
import com.aerofs.lib.ClientParam;
import com.aerofs.testlib.LoggerSetup;
import org.jboss.netty.channel.ChannelException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Properties;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class TestTCPStartup {
    static
    {
        ConfigurationProperties.setProperties(new Properties());
        MockDefects.init(mock(DefectFactory.class), mock(AutoDefect.class));
        LoggerSetup.init();
    }

    public TCPTransportResource transport;
    public static int cachedPortRangeLow;
    public static int cachedPortRangeHigh;

    public TestTCPStartup()
            throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();

        MockCA mockCA = new MockCA(String.format("testca-%d@arrowfs.org", Math.abs(secureRandom.nextInt())), secureRandom);

        this.transport = new TCPTransportResource(TransportFactory.TransportType.LANTCP, mockCA);
    }

    @BeforeClass
    public static void cachePortRange()
    {
        cachedPortRangeLow = ClientParam.Daemon.PORT_RANGE_LOW;
        cachedPortRangeHigh = ClientParam.Daemon.PORT_RANGE_HIGH;
    }

    @AfterClass
    public static void restorePortRange()
    {
        ClientParam.Daemon.PORT_RANGE_LOW = cachedPortRangeLow;
        ClientParam.Daemon.PORT_RANGE_HIGH = cachedPortRangeHigh;
    }

    @Test
    public void willGetPortInRange()
            throws Throwable
    {
        ClientParam.Daemon.PORT_RANGE_LOW = 2000;
        ClientParam.Daemon.PORT_RANGE_HIGH = 2010;
        this.transport.publicBefore();
        TCP tcp = ((TCP) this.transport.getTransport());
        assertThat("upper bound", tcp.getListeningPort(), lessThanOrEqualTo(2010));
        assertThat("lower bound", tcp.getListeningPort(), greaterThanOrEqualTo(2000));
        this.transport.publicAfter();
    }

    @Test
    public void willTryAdditionalPorts()
            throws Throwable
    {
        // N.B. this assumes that the daemon will start trying ports at the bottom of the range
        try (Socket s = new Socket()) {
            s.bind(new InetSocketAddress(0));
            int boundPort = s.getLocalPort();

            ClientParam.Daemon.PORT_RANGE_LOW = boundPort;
            ClientParam.Daemon.PORT_RANGE_HIGH = boundPort + 5;
            this.transport.publicBefore();
            TCP tcp = ((TCP) this.transport.getTransport());
            assertThat("conflict on lower bound", tcp.getListeningPort(), greaterThan(boundPort));
            this.transport.publicAfter();
        }
    }

    @Test
    public void willThrowErrorIfNoOpenPorts()
            throws Throwable
    {
        try (Socket s = new Socket()) {
            s.bind(new InetSocketAddress(0));
            int boundPort = s.getLocalPort();

            ClientParam.Daemon.PORT_RANGE_LOW = boundPort;
            ClientParam.Daemon.PORT_RANGE_HIGH = boundPort;
            try {
                this.transport.publicBefore();
                fail();
            } catch (ChannelException e) {
            }
        }
    }

    // Need to override methods here to expose them to testing, junit interfaces require that the original methods are protected
    // but these tests need to initialize the transport after modifying a configuration variable
    private class TCPTransportResource extends TransportResource {
        private boolean calledBefore, calledAfter;
        public TCPTransportResource(TransportFactory.TransportType transportType, MockCA mockCA) {
            super(transportType, mockCA, InetSocketAddress.createUnresolved("localhost", 1),
                    InetSocketAddress.createUnresolved("localhost", 1));
        }

        synchronized void publicAfter() {
            if (!calledBefore) {
                throw new IllegalStateException("need to initialize tcptransportresource first");
            }
            if (calledAfter) {
                return;
            }
            super.after();
            calledAfter = true;
        }

        void publicBefore() throws Throwable {
            if (calledBefore) {
                return;
            }
            super.before();
            calledBefore = true;
        }
    }
}
