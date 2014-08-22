/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.C;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.defects.Defect;
import com.aerofs.defects.DefectFactory;
import com.aerofs.defects.MockDefects;
import com.aerofs.lib.event.Prio;
import com.aerofs.xray.server.XRayServer;
import com.aerofs.zephyr.server.ZephyrServer;
import com.aerofs.testlib.LoggerSetup;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;

// FIXME (AG): send multiple streams simultaneously from both directions
// FIXME (AG): send multiple streams and kill one
@RunWith(Parameterized.class)
public final class TestTransports
{
    static
    {
        ConfigurationProperties.setProperties(new Properties());
        LoggerSetup.init();
    }

    private static final Logger l = LoggerFactory.getLogger(TestTransports.class);

    private static final String TEST_PACKET = "TEST_PACKET";
    private static final int STREAM_TEST_DATA_SIZE = 2 * C.MB;
    private static final String DIGEST_TYPE = "MD5";

    @Parameters
    public static Collection<Object[]> transports()
    {
        Object[][] data = new Object[][]{{TransportType.LANTCP}, {TransportType.ZEPHYR}, {TransportType.XRAYRS}}; // jingle cannot be tested in-process
        return Arrays.asList(data);
    }

    //--------------------------------------------------------------------------------------------//
    //
    // Setup

    @Rule public TestRule timeoutRule = new Timeout((int) (1 * C.MIN)); // 1 min timeout for each test
    @Rule public LoggingRule loggingRule = new LoggingRule(l);

    // for all tests
    private final TransportType transportType;
    private final InetSocketAddress zephyrAddress;
    private final InetSocketAddress xrayAddress;

    // servers
    private ZephyrServer zephyr;
    private XRayServer xray;

    // for the stream tests
    private TransportDigestStreamSender streamSender;
    private TransportDigestStreamReceiver streamReceiver;

    // for all tests
    @Rule public TransportResource transport0;
    @Rule public TransportResource transport1;
    @Rule public TransportResource transport2;
    @Rule public TransportResource transport3;

    public TestTransports(TransportType transportType)
            throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();

        zephyrAddress = InetSocketAddress.createUnresolved("localhost", secureRandom.nextInt(10000) + 5000);
        xrayAddress = InetSocketAddress.createUnresolved("localhost", secureRandom.nextInt(10000) + 5000);

        MockCA mockCA = new MockCA(String.format("testca-%d@arrowfs.org", Math.abs(secureRandom.nextInt())), secureRandom);

        this.transportType = transportType;
        this.transport0 = new TransportResource(transportType, mockCA, zephyrAddress, xrayAddress);
        this.transport1 = new TransportResource(transportType, mockCA, zephyrAddress, xrayAddress);
        this.transport2 = new TransportResource(transportType, mockCA, zephyrAddress, xrayAddress);
        this.transport3 = new TransportResource(transportType, mockCA, zephyrAddress, xrayAddress);
    }

    @Before
    public void initMocks()
    {
        MockDefects.init();
    }

    @Before
    public void setup()
            throws Exception
    {
        // start up zephyr
        zephyr = new ZephyrServer(zephyrAddress.getHostName(), (short) zephyrAddress.getPort(), new com.aerofs.zephyr.server.core.Dispatcher());
        zephyr.init();

        Thread zephyrRunner = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                zephyr.start();
            }
        });
        zephyrRunner.setName("zephyr");
        zephyrRunner.start();

        // start up xray
        xray = new XRayServer(xrayAddress.getHostName(), (short) xrayAddress.getPort(), new com.aerofs.xray.server.core.Dispatcher());
        xray.init();
        Thread xrayRunner = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                xray.start();
            }
        });
        xrayRunner.setName("xray");
        xrayRunner.start();

        // start the individual client transports
        l.info("RUNNING END-TO-END TRANSPORT TEST FOR {} T0:{} D0:{}, T1:{} D1:{}, T2:{} D2:{}, T3:{} D3:{}",
                transportType,
                transport0.getTransport().id(),
                transport0.getDID(),
                transport1.getTransport().id(),
                transport1.getDID(),
                transport2.getTransport().id(),
                transport2.getDID(),
                transport3.getTransport().id(),
                transport3.getDID());
    }

    @After
    public void teardown()
    {
        l.info("ENDING END-TO-END TRANSPORT TEST FOR {}", transportType);

        if (zephyr != null) {
            zephyr.stop();
        }

        if (xray != null) {
            xray.stop();
        }

        if (streamSender != null) {
            streamSender.stop();
        }

        if (streamReceiver != null) {
            streamReceiver.stop();
        }
    }

    //--------------------------------------------------------------------------------------------//
    //
    // TEST: Unicast packet send and receive test

    @Test
    public void shouldSendMultipleUnicastPacketsBetweenTransport()
            throws Exception
    {
        final Semaphore availableSemaphore = new Semaphore(0);
        final Semaphore unavailableSempahore = new Semaphore(0);
        final Semaphore packetReceivedSemaphore = new Semaphore(0);

        // setup
        // transport0 -> transport1

        SID sharedStore = SID.generate();

        transport0.joinStore(sharedStore);
        transport1.joinStore(sharedStore);

        Set<DID> otherDIDs = Sets.newHashSet();
        otherDIDs.add(transport1.getDID());

        addDeviceAvailabilityListener(transport0, otherDIDs, availableSemaphore, unavailableSempahore);
        addUnicastPacketListener(transport1, packetReceivedSemaphore);

        // wait for transport1 to come online
        availableSemaphore.acquire();

        // we're going to send these many packets
        int packetCount = 10;

        // now, send multiple packets to it in a non-blocking fashion
        for (int i = 0; i < packetCount; i++) {
            transport0.send(transport1.getDID(), getTestPacketBytes(), Prio.LO);
        }

        // wait until we receive all the packets
        packetReceivedSemaphore.acquire(packetCount);
    }

    //--------------------------------------------------------------------------------------------//
    //
    // TEST: Stream send and receive

    @Test
    public void shouldSendAndReceiveStream()
            throws Exception
    {
        // have both transports join the same store

        SID sharedStore = SID.generate();

        transport0.joinStore(sharedStore);
        transport1.joinStore(sharedStore);

        // create a couple of semaphores to indicate that both the sender and receiver were created
        final Semaphore senderCreated = new Semaphore(0);
        final Semaphore receiverCreated = new Semaphore(0);

        //
        // transport0 [SENDER]
        //

        transport0.setTransportListener(new TransportListener()
        {
            @Override
            public void onStoreAvailableForDevice(DID did, Collection<SID> sid)
            {
                if (did.equals(transport1.getDID())) {

                    // create the sender (transport0 ->)
                    byte[] streamBytes = transport0.getRandomBytes(STREAM_TEST_DATA_SIZE);
                    streamSender = new TransportDigestStreamSender(transport0, transport1.getDID(), DIGEST_TYPE, streamBytes);
                    streamSender.start();

                    senderCreated.release();
                }
            }
        });

        //
        // transport1 [RECEIVER]
        //

        transport1.setTransportListener(new TransportListener()
        {
            private final Map<DID, Map<StreamID, TransportInputStream>> streamDataMap = Maps.newHashMap();

            @Override
            public void onNewStream(DID did, StreamID streamID)
            {
                if (!streamDataMap.containsKey(did)) {
                    streamDataMap.put(did, Maps.<StreamID, TransportInputStream>newHashMap());
                }

                Map<StreamID, TransportInputStream> streamMap = streamDataMap.get(did);
                checkState(!streamMap.containsKey(streamID));

                TransportInputStream transportInputStream = new TransportInputStream(did, streamID, transport1.getTransport().q());
                streamMap.put(streamID, transportInputStream);

                // create the receiver (-> transport1)
                streamReceiver = new TransportDigestStreamReceiver(did, transportInputStream, STREAM_TEST_DATA_SIZE, DIGEST_TYPE);
                streamReceiver.start();

                receiverCreated.release();
            }

            @Override
            public void onIncomingStreamChunk(DID did, StreamID streamID, InputStream chunkInputStream)
            {
                Map<StreamID, TransportInputStream> streamMap = streamDataMap.get(did);
                checkNotNull(streamMap, "no streams for %s", did);

                TransportInputStream stream = streamMap.get(streamID);
                checkNotNull(stream, "no stream for streamID %s", streamID);

                stream.offer(chunkInputStream);
            }
        });

        //
        // wait until the streams were sent/received
        //

        // gate until the sender and receiver are created
        senderCreated.acquire();
        receiverCreated.acquire();

        // wait until the streams are sent/received
        byte[] sentDigest = streamSender.getDigest(30000);
        byte[] receiverDigest = streamReceiver.getDigest(30000);

        assertThat(sentDigest, equalTo(receiverDigest));
    }

    //--------------------------------------------------------------------------------------------//
    //
    // TEST: Unicast pause sync test

    @Test
    public void shouldSendSingleUnicastPacketAndThenFailSendAfterPauseSync()
            throws Exception
    {
        final Semaphore availableSemaphore = new Semaphore(0);
        final Semaphore unavailableSempahore = new Semaphore(0);
        final Semaphore packetReceivedSemaphore = new Semaphore(0);

        // start by sending a single packet
        //
        // this will force the following to happen:
        // peer0 ---> peer1
        // peer0 ---> peer2

        SID sharedStore = SID.generate();

        transport0.joinStore(sharedStore);
        transport1.joinStore(sharedStore);
        transport2.joinStore(sharedStore);

        // peer 0 cares about only 2 DIDs
        Set<DID> otherDIDs = Sets.newHashSet(transport1.getDID(), transport2.getDID());

        addUnicastPacketListener(transport1, packetReceivedSemaphore);
        addUnicastPacketListener(transport2, packetReceivedSemaphore);
        addDeviceAvailabilityListener(transport0, otherDIDs, availableSemaphore, unavailableSempahore);

        // wait for the peers to come online
        availableSemaphore.acquire(otherDIDs.size());

        // now, send a packet to the peer
        for (DID did : otherDIDs) {
            transport0.sendBlocking(did, getTestPacketBytes(), Prio.LO);
        }

        // wait for the packets to arrive at the remote peers
        packetReceivedSemaphore.acquire(otherDIDs.size());

        // now, pause syncing
        transport0.pauseSyncing();

        // wait until the device goes offline
        unavailableSempahore.acquire(otherDIDs.size());

        // now, try again to send a packet to the peer
        // it should fail immediately
        for (DID did : otherDIDs) {
            assertSendBlockingFailed(transport0, did);
        }
    }

    //--------------------------------------------------------------------------------------------//
    //
    // TEST: Unicast pause sync test and then resume sync

    @Test
    public void shouldSendSingleUnicastPacketAndThenFailSendAfterPauseSyncAndThenSendSingleUnicastPacketAfterResumeSync()
            throws Exception
    {
        final Semaphore availableSemaphore = new Semaphore(0);
        final Semaphore unavailableSemaphore = new Semaphore(0);
        final Semaphore packetReceivedSemaphore = new Semaphore(0);

        // start by sending a single packet
        //
        // this will force the following to happen:
        // peer0 ---> peer1
        // peer0 ---> peer2
        // peer0 ---> peer3

        SID sharedStore = SID.generate();

        transport0.joinStore(sharedStore);
        transport1.joinStore(sharedStore);
        transport2.joinStore(sharedStore);
        transport3.joinStore(sharedStore);

        // peer 0 cares about the following DIDs
        Set<DID> otherDIDs = Sets.newHashSet(transport1.getDID(), transport2.getDID(), transport3.getDID());

        addUnicastPacketListener(transport1, packetReceivedSemaphore);
        addUnicastPacketListener(transport2, packetReceivedSemaphore);
        addUnicastPacketListener(transport3, packetReceivedSemaphore);
        addDeviceAvailabilityListener(transport0, otherDIDs, availableSemaphore, unavailableSemaphore);

        // wait for all peers to come online
        availableSemaphore.acquire(otherDIDs.size());

        // now, send a packet to each peer
        for (DID did : otherDIDs) {
            transport0.sendBlocking(did, getTestPacketBytes(), Prio.LO);
        }

        // wait for the packets to arrive at the remote peers
        packetReceivedSemaphore.acquire(otherDIDs.size());

        // now, pause syncing
        transport0.pauseSyncing();

        // wait until all devices go offline
        unavailableSemaphore.acquire(otherDIDs.size());

        // now, try again to send a packet to each peer (they should fail)
        for (DID did : otherDIDs) {
            assertSendBlockingFailed(transport0, did);
        }

        // OK, now, lets resume sync
        transport0.resumeSyncing();

        // wait again for the peers to come online
        availableSemaphore.acquire(otherDIDs.size());

        // now, send a packet to each peer
        for (DID did : otherDIDs) {
            transport0.sendBlocking(did, getTestPacketBytes(), Prio.LO);

        }

        // wait for the packets to arrive at the remote peers
        packetReceivedSemaphore.acquire(otherDIDs.size());
    }

    private static void addDeviceAvailabilityListener(TransportResource transport, final Set<DID> otherDIDs, final Semaphore availableSemaphore, final Semaphore unavailableSempahore)
    {
        checkArgument(!otherDIDs.isEmpty());

        transport.setTransportListener(new TransportListener()
        {
            @Override
            public void onStoreAvailableForDevice(DID did, Collection<SID> sid)
            {
                if (otherDIDs.contains(did)) {
                    availableSemaphore.release();
                }
            }

            @Override
            public void onStoreUnavailableForDevice(DID did, Collection<SID> sids)
            {
                if (otherDIDs.contains(did)) {
                    unavailableSempahore.release();
                }
            }
        });
    }

    private static void addUnicastPacketListener(TransportResource transport, final Semaphore packetReceivedSemaphore)
    {
        transport.setTransportListener(new TransportListener()
        {
            @Override
            public void onIncomingPacket(DID did, UserID userID, byte[] packet)
            {
                try {
                    assertThat(Arrays.equals(packet, getTestPacketBytes()), equalTo(true));
                    packetReceivedSemaphore.release();
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException("could not convert bytes to ASCII", e);
                }
            }
        });
    }

    private static void assertSendBlockingFailed(TransportResource transport, DID did)
    {
        // attempt to send the packet to a peer and check that it fails immediately
        boolean caughtException = false;
        try {
            transport.sendBlocking(did, getTestPacketBytes(), Prio.LO);
        } catch (Exception e) {
            caughtException = true;
            assertThat(e, instanceOf(ExTransport.class));
        }

        // the send should have failed
        assertThat(caughtException, equalTo(true));
    }

    private static byte[] getTestPacketBytes()
            throws UnsupportedEncodingException
    {
        return TEST_PACKET.getBytes(Charsets.US_ASCII.name());
    }
}
