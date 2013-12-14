/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.daemon.transport.LoggingRule;
import com.aerofs.daemon.transport.MockCA;
import com.aerofs.daemon.transport.MockRockLog;
import com.aerofs.daemon.transport.TransportLoggerSetup;
import com.aerofs.daemon.transport.lib.UnicastTransportListener.Received;
import com.aerofs.daemon.transport.lib.handlers.ClientHandler;
import com.aerofs.daemon.transport.tcp.UnicastTCPDevice;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.os.OSUtil;
import com.google.common.base.Charsets;
import org.hamcrest.MatcherAssert;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public final class TestTCPUnicast
{
    static
    {
        TransportLoggerSetup.init();
        OSUtil.get().loadLibrary("aerofsd");
    }

    private static final Logger l = LoggerFactory.getLogger(TestTCPUnicast.class);

    private static final byte[] TEST_DATA = "hello".getBytes(Charsets.US_ASCII);
    private static final byte[][] TEST_PAYLOAD = TPUtil.newDatagramPayload(TEST_DATA);

    private final LinkStateService linkStateService = new LinkStateService();

    private UnicastTCPDevice localDevice;
    private UnicastTCPDevice otherDevice;

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

        localDevice = new UnicastTCPDevice(random, secureRandom, linkStateService, mockCA, mockRockLog, new UnicastTransportListener());
        otherDevice = new UnicastTCPDevice(random, secureRandom, linkStateService, mockCA, mockRockLog, new UnicastTransportListener());

        when(localDevice.unicastCallbacks.resolve(otherDevice.did)).thenReturn(otherDevice.listeningAddress);
        when(otherDevice.unicastCallbacks.resolve(localDevice.did)).thenReturn(localDevice.listeningAddress);

        linkStateService.start();

        localDevice.start(new NioServerSocketChannelFactory(), new NioClientSocketChannelFactory());
        otherDevice.start(new NioServerSocketChannelFactory(), new NioClientSocketChannelFactory());
    }

    // returns the ClientHandler used to send the packet
    private ClientHandler sendPacketAndWaitForItToBeReceived(UnicastTCPDevice senderDevice, UnicastTCPDevice receiverDevice, byte[] data)
            throws ExTransportUnavailable, ExDeviceUnavailable, InterruptedException, ExecutionException
    {
        Waiter waiter = new Waiter();
        ClientHandler clientHandler = (ClientHandler) senderDevice.unicast.send(receiverDevice.did, waiter, Prio.LO, TPUtil.newDatagramPayload(data), null);

        // wait until we trigger that the packet is sent
        waiter.future.get();

        // wait until the packet shows up at the other side
        Received received = receiverDevice.transportListener.received.take();
        MatcherAssert.assertThat(received.packet, equalTo(data));

        return clientHandler;
    }

    @Ignore
    @Test
    public void shouldDisconnectChannelAndNotifyUnicastListenerOfDeviceDisconnectionWhenLinkGoesDown()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        ClientHandler localClient = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, TEST_DATA);

        // set the link state changed
        linkStateService.markLinksDown();

        // wait for our local channel to be closed
        checkNotNull(localClient.getChannel()).getCloseFuture().awaitUninterruptibly();

        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // verification
        //    |
        //    |
        //    V

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener).onDeviceConnected(otherDevice.did);
        localInOrder.verify(localDevice.unicastListener).onUnicastUnavailable();
        localInOrder.verify(localDevice.unicastListener).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener).onDeviceConnected(localDevice.did);
        otherInOrder.verify(otherDevice.unicastListener).onUnicastUnavailable();
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
        ClientHandler localClient = (ClientHandler) localDevice.unicast.send(otherDevice.did, waiter, Prio.LO, new byte[][]{TEST_DATA}, null); // IMPORTANT: PURPOSELY BROKEN PACKET

        // wait until we trigger the failure
        waiter.future.get();

        // now, wait until the channel over which we sent the packet is closed (it should be closed!)
        checkNotNull(localClient.getChannel()).getCloseFuture().awaitUninterruptibly();

        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // verification
        //    |
        //    |
        //    V

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener).onDeviceConnected(otherDevice.did);
        localInOrder.verify(localDevice.unicastListener).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
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
        when(localDevice.unicastCallbacks.resolve(unreachableDevice)).thenReturn(new InetSocketAddress(0));

        // send a packet to the remote device (which will force a new channel to be created)
        Waiter waiter = new Waiter();
        localDevice.unicast.send(unreachableDevice, waiter, Prio.LO, TEST_PAYLOAD, null); // send a valid packet to a fake DID

        // verification
        //    |
        //    |
        //    V

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener, times(0)).onDeviceConnected( unreachableDevice);
    }

    @Test
    public void shouldNotNotifyUnicastListenerOfDisconnectionIfBothIncomingAndOutgoingConnectionsExistAndOnlyTheIncomingConnectionIsBroken()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        // send a packet to the remote device (which will force a new channel to be created)
        // wait for it to be received
        ClientHandler localClient = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice

        // send a packet _from_ the other device to the local device (this will force another channel to be created)
        // wait for that packet to be received
        ClientHandler otherClient = sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice
        // 2. localDevice (in)  <------- (out) otherDevice

        // now...kill connection 2. (i.e. the connection _from_ otherDevice _to_ localDevice
        checkNotNull(otherClient.getChannel()).disconnect().awaitUninterruptibly();

        // verification
        //    |
        //    |
        //    V

        // verify that connection 1 is still alive and usable
        assertThat(checkNotNull(localClient.getChannel()).getCloseFuture().isDone(), equalTo(false));
        sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, TEST_DATA);

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener, times(2)).onDeviceConnected(otherDevice.did); // once for incoming, once for outgoing
        localInOrder.verify(localDevice.unicastListener, times(0)).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener, times(2)).onDeviceConnected(localDevice.did); // once for incoming, once for outgoing
        otherInOrder.verify(otherDevice.unicastListener, times(0)).onDeviceDisconnected(localDevice.did);
    }

    @Test
    public void shouldNotNotifyUnicastListenerOfDisconnectionIfBothIncomingAndOutgoingConnectionsExistAndOnlyTheOutgoingConnectionIsBroken()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        // send a packet to the remote device (which will force a new channel to be created)
        // wait for it to be received
        ClientHandler localClient = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice

        // send a packet _from_ the other device to the local device (this will force another channel to be created)
        // wait for that packet to be received
        ClientHandler otherClient = sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice
        // 2. localDevice (in)  <------- (out) otherDevice

        // now...kill connection 1. (i.e. the connection from _localDevice_ to _otherDevice_
        checkNotNull(localClient.getChannel()).disconnect().awaitUninterruptibly();

        // verification
        //    |
        //    |
        //    V

        // verify that connection 2 is still alive and usable
        assertThat(checkNotNull(otherClient.getChannel()).getCloseFuture().isDone(), equalTo(false));
        sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, TEST_DATA);

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener, times(2)).onDeviceConnected(otherDevice.did); // once for incoming, once for outgoing
        localInOrder.verify(localDevice.unicastListener, times(0)).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener, times(2)).onDeviceConnected(localDevice.did); // once for incoming, once for outgoing
        otherInOrder.verify(otherDevice.unicastListener, times(0)).onDeviceDisconnected(localDevice.did);
    }

    @Test
    public void shouldNotifyUnicastListenerOfDeviceDisconnectionWhenBothServerAndClientChannelAreDisconnected()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        // send a packet to the remote device (which will force a new channel to be created)
        // wait for it to be received
        ClientHandler localClient = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice

        // send a packet _from_ the other device to the local device (this will force another channel to be created)
        // wait for that packet to be received
        ClientHandler otherClient = sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice
        // 2. localDevice (in)  <------- (out) otherDevice

        // now...kill connection 1. and 2.
        checkNotNull(localClient.getChannel()).disconnect().awaitUninterruptibly();
        checkNotNull(otherClient.getChannel()).disconnect().awaitUninterruptibly();

        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // verification
        //    |
        //    |
        //    V

        // verify that both connections are indeed dead
        assertThat(checkNotNull(localClient.getChannel()).getCloseFuture().isDone(), equalTo(true));
        assertThat(checkNotNull(otherClient.getChannel()).getCloseFuture().isDone(), equalTo(true));

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener, times(2)).onDeviceConnected(otherDevice.did); // once for incoming, once for outgoing
        localInOrder.verify(localDevice.unicastListener, times(1)).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener, times(2)).onDeviceConnected(localDevice.did); // once for incoming, once for outgoing
        otherInOrder.verify(otherDevice.unicastListener, times(1)).onDeviceDisconnected(localDevice.did);
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
