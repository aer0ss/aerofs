/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.BaseParam;
import com.aerofs.base.C;
import com.aerofs.base.config.ConfigurationProperties;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.LoggingRule;
import com.aerofs.daemon.transport.MockCA;
import com.aerofs.daemon.transport.MockRockLog;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
import com.aerofs.daemon.transport.lib.UnicastTransportListener;
import com.aerofs.daemon.transport.lib.UnicastTransportListener.Received;
import com.aerofs.daemon.transport.lib.Waiter;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.os.OSUtil;
import com.google.common.base.Charsets;
import org.hamcrest.MatcherAssert;
import org.jboss.netty.channel.Channel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.InOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

public final class TestZephyrUnicast
{
    static
    {
        ConfigurationProperties.setProperties(new Properties());
        LoggerSetup.init();
        OSUtil.get().loadLibrary("aerofsd");
    }

    private static final Logger l = LoggerFactory.getLogger(TestZephyrUnicast.class);

    private static final byte[] TEST_DATA = "hello".getBytes(Charsets.US_ASCII);

    private UnicastZephyrDevice localDevice;
    private UnicastZephyrDevice otherDevice;

    // FIXME (AG): check renegotiation

    @Rule
    public Timeout timeoutRule = new Timeout((int) (1 * C.MIN));

    @Rule
    public LoggingRule loggingRule = new LoggingRule(l);

    @Before
    public void setup()
            throws Exception
    {
        Random random = new Random();
        SecureRandom secureRandom = new SecureRandom();
        MockRockLog mockRockLog = new MockRockLog();
        MockCA mockCA = new MockCA("test-ca", new SecureRandom());

        localDevice = new UnicastZephyrDevice(random, secureRandom, BaseParam.Zephyr.SERVER_ADDRESS.getHostName(), BaseParam.Zephyr.SERVER_ADDRESS.getPort(), mockCA, mockRockLog, new UnicastTransportListener());
        otherDevice = new UnicastZephyrDevice(random, secureRandom, BaseParam.Zephyr.SERVER_ADDRESS.getHostName(), BaseParam.Zephyr.SERVER_ADDRESS.getPort(), mockCA, mockRockLog, new UnicastTransportListener());

        localDevice.init();
        otherDevice.init();

        localDevice.start();
        otherDevice.start();
    }

    // returns the MessageHandler used to send the packet
    private Channel sendPacketAndWaitForItToBeReceived(UnicastZephyrDevice senderDevice, UnicastZephyrDevice receiverDevice, byte[] data)
            throws ExDeviceOffline, InterruptedException, ExecutionException
    {
        Waiter waiter = new Waiter();
        Channel channel = (Channel) senderDevice.unicast.send(receiverDevice.did, waiter, Prio.LO, TransportProtocolUtil.newDatagramPayload(data), null);

        // wait until we trigger that the packet is sent
        waiter.future.get();

        // wait until the packet shows up at the other side
        Received received = receiverDevice.transportListener.received.take();
        MatcherAssert.assertThat(received.packet, equalTo(data));

        return channel ;
    }

    @Test
    public void shouldDisconnectChannelAndNotifyUnicastListenerOfDeviceDisconnectionWhenLinkGoesDown()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        Channel localChannel = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, TEST_DATA);

        // set the link state changed _for the local machine_
        localDevice.linkStateService.markLinksDown();

        // wait for our local channel to be closed
        localChannel.getCloseFuture().awaitUninterruptibly();

        localDevice.unicastListener.unicastUnavailableSemaphore.acquire(); // wait for the local unicast to be marked unavailable
        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // verification
        //    |
        //    |
        //    V

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener).onDeviceConnected(otherDevice.did);
        localInOrder.verify(localDevice.unicastListener).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device (ignore link going down, because we only care that it happened on the local device)
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener).onDeviceConnected(localDevice.did);
        otherInOrder.verify(otherDevice.unicastListener).onDeviceDisconnected(localDevice.did);
    }

    @Test
    public void shouldDisconnectChannelAndNotifyUnicastListenerOfDeviceDisconnectionWhenAnExceptionIsThrownInThePipeline()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        // send a packet to the remote device (which will force a new channel to be created)
        Waiter waiter = new Waiter();
        Channel localChannel = (Channel) localDevice.unicast.send(otherDevice.did, waiter, Prio.LO, new byte[][]{TEST_DATA}, null); // IMPORTANT: PURPOSELY BROKEN PACKET

        // wait until we trigger the failure
        waiter.future.get();

        // now, wait until the channel over which we sent the packet is closed (it should be closed!)
        localChannel.getCloseFuture().awaitUninterruptibly();

        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // verification
        //    |
        //    |
        //    V

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener).onDeviceConnected(otherDevice.did);
        localInOrder.verify(localDevice.unicastListener).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener).onDeviceConnected(localDevice.did);
        otherInOrder.verify(otherDevice.unicastListener).onDeviceDisconnected(localDevice.did);
    }

    @Test
    public void shouldNotNotifyUnicastListenerThatDeviceHasConnectedBeforeMakingAConnectionToTheRemoteDevice()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        DID unreachableDevice = DID.generate();

        // send a packet to the remote device (which will force a new channel to be created)
        Waiter waiter = new Waiter();
        localDevice.unicast.send(unreachableDevice, waiter, Prio.LO, TransportProtocolUtil.newDatagramPayload(
                TEST_DATA), null); // send a valid packet to a fake DID

        // verification
        //    |
        //    |
        //    V

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener, times(0)).onDeviceConnected(unreachableDevice);
    }

    @Test
    public void shouldNotNotifyUnicastListenerOfDisconnectionWhenConnectionToPeerViaZephyrIsBroken()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        // send a packet to the remote device (which will force a new channel to be created)
        // wait for it to be received
        Channel localChannel = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, TEST_DATA);

        // system state:
        //
        // 1. localDevice <--------> otherDevice

        // send a packet _from_ the other device to the local device (verify that bidirectional transfer is possible)
        // wait for that packet to be received
        Channel otherChannel = sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, TEST_DATA);

        // now...kill the connection from the other device
        otherChannel.disconnect().awaitUninterruptibly();

        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // verification
        //    |
        //    |
        //    V

        // at this point, both channels should be dead
        assertThat(localChannel.getCloseFuture().isDone(), equalTo(true));
        assertThat(otherChannel.getCloseFuture().isDone(), equalTo(true));

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener).onDeviceConnected(otherDevice.did);
        localInOrder.verify(localDevice.unicastListener).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener).onDeviceConnected(localDevice.did);
        otherInOrder.verify(otherDevice.unicastListener).onDeviceDisconnected(localDevice.did);
    }

    private void waitForIUnicastListenerCallbacksToBeTriggered(boolean waitForLocal, boolean waitForOther)
            throws InterruptedException
    {
        if (waitForLocal) {
            localDevice.unicastListener.deviceDisconnectedSemaphore.acquire();
        }

        if (waitForOther) {
            otherDevice.unicastListener.deviceDisconnectedSemaphore.acquire();
        }
    }
}
