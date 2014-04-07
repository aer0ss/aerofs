/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.LoggingRule;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableSet;
import org.jivesoftware.smack.PacketInterceptor;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;

// TODO (AG): can use the listener/semaphores to wait instead of timeouts

public final class TestXMPPConnectionService
{
    static
    {
        LoggerSetup.init();
    }

    private static final long TEST_CONNECT_TIMEOUT = 30000;
    private static final int PING_INTERVAL_IN_MS = 1000;
    private static final int MAX_PINGS_BEFORE_DISCONNECTION = 3;
    private static final long INITIAL_CONNECT_RETRY_INTERVAL_IN_MS = 1000;
    private static final long MAX_CONNECT_RETRY_INTERVAL_IN_MS = 10000;
    private static final int STATE_CHANGE_SLEEP_IN_MS = 20000;

    private static final long ACQUIRE_TIMEOUT = 10000;
    private static final TimeUnit ACQUIRE_TIMEOUT_TIMEUNIT = TimeUnit.MILLISECONDS;

    private static final Logger l = LoggerFactory.getLogger(TestXMPPConnectionService.class);

    private final LinkStateService linkStateService = new LinkStateService();
    private TestLinkStateListener linkStateListener = new TestLinkStateListener();
    private final RockLog rockLog = mock(RockLog.class);
    private XMPPConnectionService xmppConnectionService;

    @Rule
    public Timeout timeout = new Timeout(60000);

    @Rule
    public LoggingRule loggingRule = new LoggingRule(l);

    private class TestListener implements IXMPPConnectionServiceListener
    {
        Semaphore connectedSemaphore = new Semaphore(0);
        Semaphore disconnectedSemaphore = new Semaphore(0);

        @Override
        public void xmppServerConnected(XMPPConnection conn)
                throws Exception
        {
            connectedSemaphore.release();
        }

        @Override
        public void xmppServerDisconnected()
        {
            disconnectedSemaphore.release();
        }
    }

    private class TestLinkStateListener implements ILinkStateListener {

        Semaphore linkStateChangedSemaphore = new Semaphore(0);

        @Override
        public void onLinkStateChanged(
                ImmutableSet<NetworkInterface> previous,
                ImmutableSet<NetworkInterface> current,
                ImmutableSet<NetworkInterface> added,
                ImmutableSet<NetworkInterface> removed)
        {
            linkStateChangedSemaphore.release();
        }
    }

    @Before
    public void setup()
    {
        xmppConnectionService = new XMPPConnectionService(
                "test",
                DID.generate(),
                InetSocketAddress.createUnresolved("localhost", 5222),
                "arrowfs.org",
                "u",
                new byte[]{0},
                PING_INTERVAL_IN_MS,
                MAX_PINGS_BEFORE_DISCONNECTION,
                INITIAL_CONNECT_RETRY_INTERVAL_IN_MS,
                MAX_CONNECT_RETRY_INTERVAL_IN_MS,
                rockLog,
                linkStateService
        );

        linkStateService.addListener(linkStateListener, sameThreadExecutor());
    }

    @After
    public void teardown()
    {
        xmppConnectionService.stop();
    }

    @Test
    public void shouldDisconnectXMPPConnectionIfAllLinksGoDown()
            throws InterruptedException
    {
        TestListener listener = new TestListener();
        xmppConnectionService.addListener(listener);

        xmppConnectionService.start();
        linkStateService.markLinksUp();
        assertThatServiceConnected(listener);

        linkStateService.markLinksDown();
        assertThatServiceDisconnected(listener);
    }

    @Test
    public void shouldStartXmppConnectionServiceEvenWhenLinkStateChangeHappensFirst()
            throws InterruptedException
    {
        TestListener listener = new TestListener();
        xmppConnectionService.addListener(listener);

        linkStateService.markLinksUp();
        linkStateListener.linkStateChangedSemaphore.acquire(); // wait until we're notified of the link-state-change before proceeding

        xmppConnectionService.start();
        assertThatServiceConnected(listener);
    }

    @Test
    public void shouldReconnectToXMPPServerWhenLinkTransitionsFromDownToUp()
            throws InterruptedException
    {
        final AtomicInteger upCount = new AtomicInteger(0);

        TestListener listener = new TestListener()
        {
            @Override
            public void xmppServerConnected(XMPPConnection connection)
                    throws Exception
            {
                upCount.getAndIncrement();
                super.xmppServerConnected(connection);
            }
        };

        xmppConnectionService.addListener(listener);

        // start the service up
        xmppConnectionService.start();
        linkStateService.markLinksUp();
        assertThatServiceConnected(listener);

        // take it down by marking the links down (should disconnect connection)
        linkStateService.markLinksDown();
        assertThatServiceDisconnected(listener);

        // now, bring the service back up again
        linkStateService.markLinksUp();
        assertThatServiceConnected(listener);

        // check that we've actually connected twice
        assertThat(upCount.get(), equalTo(2));
    }

    @Test
    public void shouldReconnectWhenXMPPListenerThrows()
            throws InterruptedException
    {
        final AtomicInteger upCount = new AtomicInteger(0);

        TestListener listener = new TestListener() {
            @Override
            public void xmppServerConnected(XMPPConnection connection)
                    throws Exception
            {
                try {
                    int prevCount = upCount.getAndIncrement();
                    if (prevCount == 0) {
                        throw new Exception("this is a buggy listener"); // NOTE: RuntimeException and all subclasses kill the retry
                    }
                } finally {
                    super.connectedSemaphore.release(); // yes...I know this is odd usage
                }
            }
        };

        xmppConnectionService.addListener(listener);

        // we should attempt to connect once
        xmppConnectionService.start();
        linkStateService.markLinksUp();
        assertThatServiceConnected(listener);

        // then...the listener throws and we should
        // attempt to reconnect
        assertThatServiceConnected(listener, TEST_CONNECT_TIMEOUT);

        // just for kicks, verify that we've actually connected twice
        assertThat(upCount.get(), equalTo(2));
    }

    @Test
    public void shouldTurnOffPingIfAPingResponseIsReceived()
            throws InterruptedException
    {
        final AtomicInteger pingsSent = new AtomicInteger(0);
        final AtomicReference<String> firstPingPacketId = new AtomicReference<String>(null);
        final ConcurrentSkipListSet<String> receivedPacketIds = new ConcurrentSkipListSet<String>();

        TestListener listener = new TestListener()
        {
            @Override
            public void xmppServerConnected(XMPPConnection connection)
                    throws Exception
            {
                connection.addPacketSendingListener(new PacketListener()
                {
                    @Override
                    public void processPacket(Packet packet)
                    {
                        IQ iq = (IQ) packet;
                        if (iq.getChildElementXML().equals(XMPPConnectionService.XMPP_PING_STANZA)) {
                            pingsSent.getAndIncrement();
                            firstPingPacketId.compareAndSet(null, iq.getPacketID());
                        }
                    }
                }, new PacketTypeFilter(IQ.class));

                connection.addPacketListener(new PacketListener()
                {
                    @Override
                    public void processPacket(Packet packet)
                    {
                        l.debug("recv packet:{}", packet.toXML());
                        receivedPacketIds.add(packet.getPacketID());
                    }
                }, new PacketTypeFilter(IQ.class));

                super.xmppServerConnected(connection);
            }
        };
        xmppConnectionService.addListener(listener);

        // start the service up
        xmppConnectionService.start();
        linkStateService.markLinksUp();
        assertThatServiceConnected(listener);

        // ping...and wait to see that we don't have any errant pings happening
        xmppConnectionService.ping();
        Thread.sleep(PING_INTERVAL_IN_MS * (MAX_PINGS_BEFORE_DISCONNECTION + 1));

        // check that we 1) sent only 1 ping, and 2) that we actually got a response
        assertThat(pingsSent.get(), equalTo(1));
        assertThat(receivedPacketIds.contains(firstPingPacketId.get()), equalTo(true));
    }

    @Test
    public void shouldStopPingingWhenTheLinkIsDisconnected()
            throws InterruptedException
    {
        // stop the existing instance
        // and wait for it to shutdown
        xmppConnectionService.stop();
        Thread.sleep(2000);

        // create a new instance
        int linkStateChangePingInterval = 8000;

        xmppConnectionService = new XMPPConnectionService(
                "test",
                DID.generate(),
                InetSocketAddress.createUnresolved("localhost", 5222),
                "arrowfs.org",
                "u",
                new byte[]{0},
                linkStateChangePingInterval, // long ping interval
                MAX_PINGS_BEFORE_DISCONNECTION,
                INITIAL_CONNECT_RETRY_INTERVAL_IN_MS,
                MAX_CONNECT_RETRY_INTERVAL_IN_MS,
                rockLog,
                linkStateService
        );

        final Semaphore pingSemaphore = new Semaphore(0);
        final AtomicInteger pingCount = new AtomicInteger(0);

        TestListener listener = new TestListener()
        {
            @Override
            public void xmppServerConnected(XMPPConnection connection)
                    throws Exception
            {
                connection.addPacketSendingListener(new PacketListener()
                {
                    @Override
                    public void processPacket(Packet packet)
                    {
                        IQ iq = (IQ)packet;
                        if (iq.getChildElementXML().equals(XMPPConnectionService.XMPP_PING_STANZA)) {
                            pingSemaphore.release();
                            pingCount.getAndIncrement();
                        }
                    }
                }, new PacketTypeFilter(IQ.class));

                super.xmppServerConnected(connection);
            }
        };
        xmppConnectionService.addListener(listener);

        // start up the system
        xmppConnectionService.start();
        linkStateService.markLinksUp();
        assertThatServiceConnected(listener);

        // start pinging, and check that we've sent out
        // at least one ping
        xmppConnectionService.ping();
        boolean receivedPing = pingSemaphore.tryAcquire(ACQUIRE_TIMEOUT, ACQUIRE_TIMEOUT_TIMEUNIT);
        assertThat(receivedPing, equalTo(true));

        // now, mark links down
        linkStateService.markLinksDown();
        assertThatServiceDisconnected(listener);

        // wait for a bit to see if we're still pinging
        l.info("waiting to receive another ping...");
        Thread.sleep(linkStateChangePingInterval);
        assertThat(pingCount.get(), equalTo(1));
    }

    @Test
    public void shouldDisconnectLinkIfPingsAreNotAnswered()
            throws InterruptedException
    {
        final AtomicInteger connectionAttemptsMade = new AtomicInteger(0);
        final AtomicInteger pingsSent = new AtomicInteger(0);

        TestListener listener = new TestListener()
        {
            @Override
            public void xmppServerConnected(XMPPConnection connection)
                    throws Exception
            {
                connectionAttemptsMade.getAndIncrement();

                connection.addPacketInterceptor(new PacketInterceptor()
                {
                    @Override
                    public void interceptPacket(Packet packet)
                    {
                        IQ iq = (IQ)packet;
                        if (iq.getChildElementXML().equals(XMPPConnectionService.XMPP_PING_STANZA)) {
                            // I'd _love_ to simply drop the damn packet,
                            // but smack provides me with no way to do that
                            // instead, I set the packet id to something else so that
                            // the XMPPConnectionService can't correlate the response with
                            // the pings it's sending out...
                            iq.setPacketID("BOGUS");
                            pingsSent.getAndIncrement();
                        }
                    }
                }, new PacketTypeFilter(IQ.class));

                super.xmppServerConnected(connection);
            }
        };
        xmppConnectionService.addListener(listener);

        xmppConnectionService.start();
        linkStateService.markLinksUp();
        assertThatServiceConnected(listener);

        // give it enough time to ping and then disconnect
        xmppConnectionService.ping();
        assertThatServiceDisconnected(listener, PING_INTERVAL_IN_MS * (MAX_PINGS_BEFORE_DISCONNECTION + 1));

        // and then, enough time to reconnect
        assertThatServiceConnected(listener, (MAX_CONNECT_RETRY_INTERVAL_IN_MS * 2) + STATE_CHANGE_SLEEP_IN_MS);

        // and then, verify that everything is A-OK
        assertThat(pingsSent.get(), equalTo(MAX_PINGS_BEFORE_DISCONNECTION));
        assertThat(connectionAttemptsMade.get(), equalTo(2));
    }

    @Test
    public void shouldPingIfTheNumberOfInterfacesDecreases()
            throws InterruptedException
    {
        // ok...this is a little ridiculous
        // but, to avoid writing a lot of scaffolding, I can essentially
        // trigger the case I want to test simply by making the
        // XMPPConnectionService _think_ I removed some interfaces
        //
        // I do this by storing all the interfaces that we report, and adding
        // _one_ of them to the 'removed' set
        //
        // Yes, this relies on some knowledge of what's going on inside
        // XMPPConnectionService (so it's white-box testing), but I'll punt
        // on wrapping for now

        final AtomicReference<ImmutableSet<NetworkInterface>> interfaces = new AtomicReference<ImmutableSet<NetworkInterface>>(null);

        linkStateService.addListener(new ILinkStateListener()
        {
            @Override
            public void onLinkStateChanged(
                    ImmutableSet<NetworkInterface> previous,
                    ImmutableSet<NetworkInterface> current,
                    ImmutableSet<NetworkInterface> added,
                    ImmutableSet<NetworkInterface> removed)
            {
                checkArgument(!current.isEmpty());
                interfaces.compareAndSet(null, current);
            }
        }, sameThreadExecutor());

        final AtomicInteger pingCount = new AtomicInteger(0);
        TestListener listener = new TestListener() {
            @Override
            public void xmppServerConnected(XMPPConnection connection)
                    throws Exception
            {
                pingCount.getAndIncrement();
                super.xmppServerConnected(connection);
            }
        };
        xmppConnectionService.addListener(listener);

        // check that we start up
        linkStateService.markLinksUp();
        xmppConnectionService.start();
        assertThatServiceConnected(listener);

        // now, futz with the service
        // and tell it that some links went down
        NetworkInterface removedInterface = interfaces.get().iterator().next();
        ImmutableSet<NetworkInterface> removed = ImmutableSet.of(removedInterface);
        xmppConnectionService.onLinkStateChanged(interfaces.get(), interfaces.get(), ImmutableSet.<NetworkInterface>of(), removed);

        // wait for the full ping interval
        Thread.sleep(10000);

        // check that we only pinged once
        assertThat(pingCount.get(), equalTo(1));
        // and that we never disconnected
        assertThat(listener.disconnectedSemaphore.tryAcquire(), equalTo(false));
    }

    private void assertThatServiceDisconnected(TestListener listener)
            throws InterruptedException
    {
        assertThatServiceDisconnected(listener, ACQUIRE_TIMEOUT);
    }

    private void assertThatServiceDisconnected(TestListener listener, long acquireTimeoutInMillis)
            throws InterruptedException
    {
        boolean disconnected = listener.disconnectedSemaphore.tryAcquire(acquireTimeoutInMillis, ACQUIRE_TIMEOUT_TIMEUNIT);
        assertThat(disconnected, equalTo(true));
    }

    private void assertThatServiceConnected(TestListener listener)
            throws InterruptedException
    {
        assertThatServiceConnected(listener, ACQUIRE_TIMEOUT);
    }

     private void assertThatServiceConnected(TestListener listener, long acquireTimeoutInMillis)
            throws InterruptedException
    {
        boolean connected = listener.connectedSemaphore.tryAcquire(acquireTimeoutInMillis, ACQUIRE_TIMEOUT_TIMEUNIT);
        assertThat(connected, equalTo(true));
    }
}
