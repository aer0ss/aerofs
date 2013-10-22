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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

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

    private static final String TEST_PACKET = "hello";
    private static final int STREAM_TEST_DATA_SIZE = 5 * C.MB;
    public static final String DIGEST_TYPE = "MD5";

    @Parameters
    public static Collection<Object[]> transports()
    {
        Object[][] data;
        if (System.getProperty("transport.jingle.tests.enable") == null)  {
            data = new Object[][]{{TransportType.LANTCP}, {TransportType.ZEPHYR}};
        } else {
            data = new Object[][]{{TransportType.LANTCP}, {TransportType.JINGLE}, {TransportType.ZEPHYR}};
        }

        return Arrays.asList(data);
    }

    //--------------------------------------------------------------------------------------------//
    //
    // Setup

    @Rule public TestRule timeoutRule = new Timeout((int) (1 * C.MIN));

    @Rule public TransportResource transport0;
    @Rule public TransportResource transport1;

    public LinkStateService linkStateService = new LinkStateService();

    public TestTransports(TransportType transportType)
            throws Exception
    {
        SecureRandom secureRandom = new SecureRandom();
        MockCA mockCA = new MockCA(String.format("testca-%d@arrowfs.org", Math.abs(secureRandom.nextInt())), secureRandom);
        MockRockLog mockRockLog = new MockRockLog();
        transport0 = new TransportResource(transportType, linkStateService, mockCA, mockRockLog);
        transport1 = new TransportResource(transportType, linkStateService, mockCA, mockRockLog);
    }

    @Before
    public void setup()
    {
        l.info("STARTING UP TEST");
        linkStateService.markLinksUp();
    }

    @After
    public void teardown()
    {
        l.info("SHUTTING DOWN TEST");
        linkStateService.markLinksDown();
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

    private class TransportStreamSender
    {
        private Thread streamSender;

        public TransportStreamSender(final TransportResource transport, final DID destdid, final byte[] bytes, final AtomicReference<byte[]> sentBytesDigest)
        {
            streamSender = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    l.info("create stream sender to {}", destdid);

                    try {
                        DigestOutputStream digestOutputStream = new DigestOutputStream(transport.newOutgoingStream(destdid), MessageDigest.getInstance(DIGEST_TYPE));
                        digestOutputStream.write(bytes);
                        digestOutputStream.close();

                        sentBytesDigest.set(digestOutputStream.getMessageDigest().digest());

                        l.info(">>> complete writing stream -> {} digest:{}", destdid, sentBytesDigest.get());
                    } catch (IOException e) {
                        l.warn("fail send stream", e);
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
            streamSender.setDaemon(false);
        }

        public void start()
        {
            streamSender.start();
        }

        @SuppressWarnings("unused")
        public void stop()
        {
            try {
                streamSender.interrupt();
                streamSender.join();
            } catch (InterruptedException e) {
                l.warn("interrupted during stop", e);
            }
        }
    }

    private class TransportStreamReceiver
    {
        private final Thread streamReceiver;

        public TransportStreamReceiver(final DID sourcedid, final TransportInputStream inputStream, final AtomicReference<byte[]> receivedDigest, final Semaphore receivedStreamSemaphore)
        {
            streamReceiver = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    l.info("create stream receiver from {}", sourcedid);

                    try {
                        byte[] receivedData = new byte[STREAM_TEST_DATA_SIZE];

                        DigestInputStream digestInputStream = new DigestInputStream(inputStream, MessageDigest.getInstance(DIGEST_TYPE));
                        int bytesRead = digestInputStream.read(receivedData);
                        assertThat(bytesRead, equalTo(STREAM_TEST_DATA_SIZE));
                        digestInputStream.close();

                        receivedDigest.set(digestInputStream.getMessageDigest().digest());

                        l.info(">>> complete reading stream <- {} digest:{}", sourcedid, receivedDigest.get());
                    } catch (IOException e) {
                        l.warn("fail receive stream", e);
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        receivedStreamSemaphore.release();
                    }
                }
            });
            streamReceiver.setDaemon(false);
        }

        public void start()
        {
            streamReceiver.start();
        }

        @SuppressWarnings("unused")
        public void stop()
        {
            try {
                streamReceiver.interrupt();
                streamReceiver.join();
            } catch (InterruptedException e) {
                l.warn("interrupted during stop", e);
            }
        }
    }

    @Test
    public void shouldSendAndReceiveStream()
            throws Exception
    {
        l.info("transport0:{} transport1:{}", transport0.getTransport().id(), transport1.getTransport().id());

        SID sharedStore = SID.generate();

        transport0.joinStore(sharedStore);
        transport1.joinStore(sharedStore);

        final AtomicReference<byte[]> sentBytesDigest = new AtomicReference<byte[]>(null);

        transport0.setTransportListener(new TransportListener()
        {
            boolean senderCreated;

            @Override
            public void onDeviceAvailable(DID did, Collection<SID> sid)
            {
                if (senderCreated) {
                    return;
                }

                final DID destdid = transport1.getDID();
                if (did.equals(destdid)) {
                    byte[] transport0StreamBytes = transport0.getRandomBytes(STREAM_TEST_DATA_SIZE);
                    TransportStreamSender streamSender = new TransportStreamSender(transport0, destdid, transport0StreamBytes, sentBytesDigest);
                    streamSender.start();
                    senderCreated = true;
                }
            }
        });

        final Semaphore transport1ReceivedStreamSemaphore = new Semaphore(0);
        final AtomicReference<byte[]> receivedBytesDigest = new AtomicReference<byte[]>(null);

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

                TransportStreamReceiver transportStreamReceiver = new TransportStreamReceiver(did, transportInputStream, receivedBytesDigest, transport1ReceivedStreamSemaphore);
                transportStreamReceiver.start();
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

        l.info("about to acquire");

        transport1ReceivedStreamSemaphore.acquire();
        assertThat(receivedBytesDigest.get(), equalTo(sentBytesDigest.get()));

        l.info("acquired");
    }
}
