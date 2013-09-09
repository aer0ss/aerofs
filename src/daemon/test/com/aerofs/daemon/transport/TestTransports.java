/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.C;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.lib.event.Prio;
import com.aerofs.rocklog.RockLog;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class TestTransports
{
    @Parameters
    public static Collection<Object[]> transports()
    {
        Object[][] data = new Object[][]{{TransportType.LANTCP}, {TransportType.JINGLE}, {TransportType.ZEPHYR}};
        return Arrays.asList(data);
    }

    private static final String TEST_PACKET = "hello";

    @ClassRule public static LoggerResource lr = new LoggerResource(TestTransports.class);

    @Rule public TestRule _timeoutRule = new Timeout((int) (1 * C.MIN));

    @Rule public LinkStateResource linkstates;
    @Rule public TransportResource transport0;
    @Rule public TransportResource transport1;

    public TestTransports(TransportType transportType)
    {
        RockLog rockLog = Mockito.mock(RockLog.class);
        KeyPair caKeyPair = SecTestUtil.generateKeyPairNoCheckedThrow(new SecureRandom());

        linkstates = new LinkStateResource();
        transport0 = new TransportResource(transportType, linkstates.getLinkStateService(), rockLog, caKeyPair);
        transport1 = new TransportResource(transportType, linkstates.getLinkStateService(), rockLog, caKeyPair);
    }

    @Test
    public synchronized void test()
            throws Exception
    {
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
                    lr.l().info(">>>> RECEIVED PACKET:{}", incomingPacket);
                    assertEquals(TEST_PACKET, incomingPacket);
                    packetSemaphore.release();
                }
            }
        });

        packetSemaphore.acquire();
    }
}
