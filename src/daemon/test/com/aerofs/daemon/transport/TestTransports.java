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
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.lib.event.Prio;
import com.google.common.collect.Maps;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public final class TestTransports
{
    static
    {
        System.loadLibrary("aerofsd");
        ConfigurationProperties.setProperties(new Properties());
        TransportLoggerSetup.init();
    }

    private static final Logger l = LoggerFactory.getLogger(TestTransports.class);

    private static final String TEST_PACKET = "TEST_PACKET";
    private static final int STREAM_TEST_DATA_SIZE = 2 * C.MB;
    private static final String DIGEST_TYPE = "MD5";

    @Parameters
    public static Collection<Object[]> transports()
    {
        Object[][] data = new Object[][]{{TransportType.LANTCP}, {TransportType.ZEPHYR}};
        return Arrays.asList(data);
    }

    //--------------------------------------------------------------------------------------------//
    //
    // Setup

    // test timeout
    @Rule public TestRule timeoutRule = new Timeout((int) (1 * C.MIN));
    // test logging
    @Rule public LoggingRule loggingRule = new LoggingRule(l);

    // for both tests
    private final LinkStateService linkStateService = new LinkStateService();
    private final TransportType transportType;

    // for the stream tests
    private TransportDigestStreamSender streamSender;
    private TransportDigestStreamReceiver streamReceiver;

    // for both tests
    @Rule public TransportResource transport0;
    @Rule public TransportResource transport1;

    public TestTransports(TransportType transportType)
            throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();

        MockCA mockCA = new MockCA(String.format("testca-%d@arrowfs.org", Math.abs(secureRandom.nextInt())), secureRandom);
        MockRockLog mockRockLog = new MockRockLog();

        this.transportType = transportType;
        this.transport0 = new TransportResource(transportType, linkStateService, mockCA, mockRockLog);
        this.transport1 = new TransportResource(transportType, linkStateService, mockCA, mockRockLog);
    }

    @Before
    public void setup()
    {
        l.info("RUNNING END-TO-END TRANSPORT TEST FOR " + transportType);
        linkStateService.markLinksUp();
    }

    @After
    public void teardown()
    {
        l.info("ENDING END-TO-END TRANSPORT TEST FOR " + transportType);
        linkStateService.markLinksDown();

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
    public void shouldSendSingleUnicastPacketBetweenTransport()
            throws Exception
    {
        l.info("transport0:{} transport1:{}", transport0.getTransport().id(), transport1.getTransport().id());

        final Semaphore packetSemaphore = new Semaphore(1);
        packetSemaphore.acquire(); // have to acquire it once so that it can be released at the end

        SID sharedStore = SID.generate();

        transport0.joinStore(sharedStore);
        transport1.joinStore(sharedStore);

        transport0.setTransportListener(new TransportListener()
        {
            @Override
            public void onDeviceAvailable(DID did, Collection<SID> sid)
            {
                if (did.equals(transport1.getDID())) {
                    l.info(">>>> SEND PACKET");
                    transport0.send(transport1.getDID(), TEST_PACKET.getBytes(), Prio.LO);
                }
            }
        });

        transport1.setTransportListener(new TransportListener()
        {
            @Override
            public void onIncomingPacket(DID did, UserID userID, byte[] packet)
            {
                if (did.equals(transport0.getDID())) {
                    String incomingPacket = new String(packet);
                    l.info(">>>> RECV PACKET:{}", incomingPacket);
                    assertEquals(TEST_PACKET, incomingPacket);
                    packetSemaphore.release();
                }
            }
        });

        packetSemaphore.acquire();
    }

    //--------------------------------------------------------------------------------------------//
    //
    // TEST: Stream send and receive

    @Test
    public void shouldSendAndReceiveStream()
            throws Exception
    {
        l.info("transport0:{} transport1:{}", transport0.getTransport().id(), transport1.getTransport().id());

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
            public void onDeviceAvailable(DID did, Collection<SID> sid)
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

                TransportInputStream transportInputStream = new TransportInputStream(did, streamID, transport1.getTransport().q(), new FakeIMCExecutor());
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
}
