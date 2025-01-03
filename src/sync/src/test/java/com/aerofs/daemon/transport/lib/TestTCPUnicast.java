/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.C;
import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.ChannelFactories;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.daemon.transport.LoggingRule;
import com.aerofs.daemon.transport.MockCA;
import com.aerofs.ids.UserID;
import com.aerofs.testlib.LoggerSetup;
import com.aerofs.daemon.transport.lib.UnicastTransportListener.Received;
import com.aerofs.daemon.transport.tcp.UnicastTCPDevice;
import com.google.common.base.Charsets;
import org.hamcrest.MatcherAssert;
import org.jboss.netty.channel.Channel;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.InOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestTCPUnicast
{
    static
    {
        System.loadLibrary("aerofsd");
        LoggerSetup.init();
    }

    private static final Logger l = LoggerFactory.getLogger(TestTCPUnicast.class);

    private static final long CHANNEL_CONNECT_TIMEOUT = 1 * C.SEC; // this can be fairly fast because both devices are on the local machine
    private static final byte[] TEST_DATA = "hello".getBytes(Charsets.US_ASCII);
    private static final byte[][] TEST_PAYLOAD = TransportProtocolUtil.newDatagramPayload(TEST_DATA);

    private UnicastTCPDevice localDevice;
    private UnicastTCPDevice otherDevice;

    @Rule
    public LoggingRule loggingRule = new LoggingRule(l);

    @Rule
    public Timeout timeoutRule = new Timeout((int) (60 * C.SEC));

    @Before
    public void setup()
            throws Exception
    {
        Random random = new Random();
        SecureRandom secureRandom = new SecureRandom();
        MockCA mockCA = new MockCA("test-ca", new SecureRandom());

        localDevice = new UnicastTCPDevice(CHANNEL_CONNECT_TIMEOUT, random, secureRandom, mockCA, new UnicastTransportListener());
        otherDevice = new UnicastTCPDevice(CHANNEL_CONNECT_TIMEOUT, random, secureRandom, mockCA, new UnicastTransportListener());

        localDevice.start(ChannelFactories.newServerChannelFactory(), ChannelFactories.newClientChannelFactory());
        otherDevice.start(ChannelFactories.newServerChannelFactory(), ChannelFactories.newClientChannelFactory());
        when(localDevice.addressResolver.resolve(otherDevice.did))
                .thenReturn(otherDevice.unicast.getListeningAddress());
        when(otherDevice.addressResolver.resolve(localDevice.did))
                .thenReturn(localDevice.unicast.getListeningAddress());

        l.info("local:{} remote:{}", localDevice.did, otherDevice.did);
    }

    @After
    public void tearDown()
    {
        localDevice.stop();
        otherDevice.stop();
    }

    // returns the Channel used to send the pack to the remote peer
    // if you specify the sendingChannel that will be used to send the packet to the peer
    private Channel sendPacketAndWaitForItToBeReceived(UnicastTCPDevice senderDevice, UnicastTCPDevice receiverDevice, @Nullable Channel sendingChannel, byte[] data)
            throws ExTransportUnavailable, ExDeviceUnavailable, InterruptedException, ExecutionException
    {
        Waiter waiter = new Waiter();
        Channel channel;
        if (sendingChannel != null) {
            senderDevice.unicast.send(sendingChannel, TransportProtocolUtil.newDatagramPayload(data), waiter);
            channel = sendingChannel;
        } else {
            channel = (Channel) senderDevice.unicast.send(receiverDevice.did, TransportProtocolUtil.newDatagramPayload(data), waiter);
        }

        // wait until we trigger that the packet is sent
        waiter.future.get();

        // wait until the packet shows up at the other side
        Received received = receiverDevice.transportListener.received.take();
        MatcherAssert.assertThat(received.packet, equalTo(data));

        return channel;
    }

    @Test
    public void shouldRejectSelfConnection()
            throws InterruptedException, ExDeviceUnavailable, ExecutionException, ExTransportUnavailable {
        when(localDevice.addressResolver.resolve(otherDevice.did))
                .thenReturn(localDevice.unicast.getListeningAddress());

        // attempt to send out the packet to this unavailable device
        Waiter waiter = new Waiter();
        localDevice.unicast.send(otherDevice.did, TransportProtocolUtil.newDatagramPayload(TEST_DATA), waiter);

        // wait...we should get an exception
        boolean exceptionThrown = false;
        try {
            waiter.future.get();
        } catch (ExecutionException e) {
            // TODO: ideally we'd be able to check what exception got to ChannelTeardownHandler
            exceptionThrown = true;
        }

        assertThat(exceptionThrown, equalTo(true));
    }

    @Ignore
    @Test
    public void shouldNotAcceptIncomingConnectionsIfLinkGoesDown()
            throws InterruptedException, ExDeviceUnavailable, ExecutionException, ExTransportUnavailable
    {
        // setup
        //   |
        //   |
        //   V

        Channel localChannel = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, null, TEST_DATA);

        // mark the links as down
        localDevice.linkStateService.markLinksDown();

        // wait for our local channel to be closed
        checkNotNull(localChannel).getCloseFuture().awaitUninterruptibly();

        // we should be notified (on both sides) that a device went offline
        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // now, attempt to send a packet from the remote to the local device
        //
        // otherDevice --- packet ---> localDevice
        //
        Waiter waiter = new Waiter();
        otherDevice.unicast.send(localDevice.did, TransportProtocolUtil.newDatagramPayload(TEST_DATA), waiter);

        // wait...we should get an exception because the packet can't be sent
        boolean exceptionThrown = false;
        try {
            waiter.future.get();
        } catch (ExecutionException e) {
            exceptionThrown = true;
        }

        assertThat(exceptionThrown, equalTo(true));
    }

    @Ignore
    @Test
    public void shouldAcceptIncomingConnectionsOnceLinkGoesDownAndComesBackUp()
            throws InterruptedException, ExTransportUnavailable, ExDeviceUnavailable, ExecutionException
    {
        // attempt to send a packet from the local to the remote device
        //
        // localDevice --- packet ---> otherDevice
        //
        Channel localChannel = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, null, TEST_DATA);

        // mark the links as down on the local device
        localDevice.linkStateService.markLinksDown();

        // wait for our local channel to be closed
        checkNotNull(localChannel).getCloseFuture().awaitUninterruptibly();

        // we should be notified (on both sides) that a device went offline
        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // now, attempt to send a packet from the remote to the local device
        //
        // otherDevice --- packet ---> localDevice
        //
        Waiter waiter = new Waiter();
        otherDevice.unicast.send(localDevice.did, TransportProtocolUtil.newDatagramPayload(TEST_DATA), waiter);

        // wait...we should get an exception because the packet can't be sent
        // i.e. this should fail
        boolean exceptionThrown = false;
        try {
            waiter.future.get();
        } catch (ExecutionException e) {
            exceptionThrown = true;
        }

        assertThat(exceptionThrown, equalTo(true));

        // bring the links back up on the local device
        localDevice.linkStateService.markLinksUp();

        // attempt to send another packet from the remote to the local device
        // this should succeed
        //
        // otherDevice --- packet ---> localDevice
        //
        sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, null, TEST_DATA);
    }

    @Test
    public void shouldFailToSendPacketAfterChannelConnectTimeoutIfTheRemoteDeviceCannotBeReached()
            throws ExDeviceUnavailable, ExecutionException, InterruptedException, ExTransportUnavailable
    {
        // create an unreachable device
        DID unavilableDevice = DID.generate();
        when(localDevice.addressResolver.resolve(unavilableDevice)).thenReturn(new InetSocketAddress("127.0.1.1", 19028)); // pick an address that's unlikely to be valid

        // attempt to send out the packet to this unavailable device
        Waiter waiter = new Waiter();
        localDevice.unicast.send(unavilableDevice, TransportProtocolUtil.newDatagramPayload(TEST_DATA), waiter);

        // wait...we should get an exception
        boolean exceptionThrown = false;
        try {
            waiter.future.get();
        } catch (ExecutionException e) {
            exceptionThrown = true; // FIXME (AG): ideally I'd throw a device unavailable instead of ChannelClosed
        }

        assertThat(exceptionThrown, equalTo(true));
    }

    @Test
    public void shouldDisconnectChannelAndNotifyUnicastListenerOfDeviceDisconnectionWhenLinkGoesDown()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        Channel localChannel = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, null, TEST_DATA);

        // set the link state changed
        localDevice.linkStateService.markLinksDown();

        // wait for our local channel to be closed
        checkNotNull(localChannel).getCloseFuture().awaitUninterruptibly();

        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // verification
        //    |
        //    |
        //    V

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener).onDeviceConnected(otherDevice.did, otherDevice.userID);
        localInOrder.verify(localDevice.unicastListener).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener).onDeviceConnected(localDevice.did, localDevice.userID);
        otherInOrder.verify(otherDevice.unicastListener).onDeviceDisconnected(localDevice.did);

        // we should have signalled somehow that unicast is unavailable
        verify(localDevice.unicastListener).onUnicastUnavailable();
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
        Channel localChannel = (Channel) localDevice.unicast.send(otherDevice.did, new byte[][]{TEST_DATA}, waiter); // IMPORTANT: PURPOSELY BROKEN PACKET

        // wait until we trigger the failure
        waiter.future.get();

        // now, wait until the channel over which we sent the packet is closed (it should be closed!)
        checkNotNull(localChannel).getCloseFuture().awaitUninterruptibly();

        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // verification
        //    |
        //    |
        //    V

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener).onDeviceConnected(otherDevice.did, otherDevice.userID);
        localInOrder.verify(localDevice.unicastListener).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener).onDeviceConnected(localDevice.did, localDevice.userID);
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
        when(localDevice.addressResolver.resolve(unreachableDevice)).thenReturn(new InetSocketAddress(0));

        // send a packet to the remote device (which will force a new channel to be created)
        Waiter waiter = new Waiter();
        localDevice.unicast.send(unreachableDevice, TEST_PAYLOAD, waiter); // send a valid packet to a fake DID

        // verification
        //    |
        //    |
        //    V

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener, times(0)).onDeviceConnected(eq(unreachableDevice), any(UserID.class));
    }

    @Test
    public void shouldNotNotifyUnicastListenerOfDisconnectionIfBothIncomingAndOutgoingConnectionsExistAndOnlyTheIncomingConnectionIsBroken()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        // create a new channel for each send request
        localDevice.unicast.setReuseChannels(false);
        otherDevice.unicast.setReuseChannels(false);

        // send a packet to the remote device (which will force a new channel to be created)
        // wait for it to be received
        Channel localChannel = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, null, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice

        // send a packet _from_ the other device to the local device (this will force another channel to be created)
        // wait for that packet to be received
        Channel otherChannel = sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, null, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice
        // 2. localDevice (in)  <------- (out) otherDevice

        // now...kill connection 2. (i.e. the connection _from_ otherDevice _to_ localDevice
        checkNotNull(otherChannel).disconnect().awaitUninterruptibly();

        // verification
        //    |
        //    |
        //    V

        // verify that connection 1 is still alive and usable
        assertThat(checkNotNull(localChannel).getCloseFuture().isDone(), equalTo(false));
        sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, localChannel, TEST_DATA);

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener, times(2)).onDeviceConnected(otherDevice.did, otherDevice.userID); // once for incoming, once for outgoing
        localInOrder.verify(localDevice.unicastListener, times(0)).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener, times(2)).onDeviceConnected(localDevice.did, localDevice.userID); // once for incoming, once for outgoing
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

        // create a new channel for each send request
        localDevice.unicast.setReuseChannels(false);
        otherDevice.unicast.setReuseChannels(false);

        // send a packet to the remote device (which will force a new channel to be created)
        // wait for it to be received
        Channel localChannel = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, null, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice

        // send a packet _from_ the other device to the local device (this will force another channel to be created)
        // wait for that packet to be received
        Channel otherChannel = sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, null, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice
        // 2. localDevice (in)  <------- (out) otherDevice

        // now...kill connection 1. (i.e. the connection from _localDevice_ to _otherDevice_
        checkNotNull(localChannel).disconnect().awaitUninterruptibly();

        // verification
        //    |
        //    |
        //    V

        // verify that connection 2 is still alive and usable
        assertThat(checkNotNull(otherChannel).getCloseFuture().isDone(), equalTo(false));
        sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, otherChannel, TEST_DATA);

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener, times(2)).onDeviceConnected(otherDevice.did, otherDevice.userID); // once for incoming, once for outgoing
        localInOrder.verify(localDevice.unicastListener, times(0)).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener, times(2)).onDeviceConnected(localDevice.did, localDevice.userID); // once for incoming, once for outgoing
        otherInOrder.verify(otherDevice.unicastListener, times(0)).onDeviceDisconnected(localDevice.did);
    }

    @Test
    public void shouldNotifyUnicastListenerOfDeviceDisconnectionWhenSingleBidirectionalChannelIsDisconnected()
            throws Exception
    {
        // setup
        //   |
        //   |
        //   V

        // send a packet to the remote device (which will force a new channel to be created)
        // wait for it to be received
        Channel localChannel = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, null, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice

        // send a packet _from_ the other device to the local device (we will reuse the same channel)
        Channel otherChannel = sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, null, TEST_DATA);

        // system state:
        //
        // 1. localDevice (in/out) <--------> (in/out) otherDevice

        // now...kill connection 1. and 2.
        checkNotNull(localChannel).disconnect().awaitUninterruptibly();
        checkNotNull(otherChannel).disconnect().awaitUninterruptibly();

        waitForIUnicastListenerCallbacksToBeTriggered(true, true);

        // verification
        //    |
        //    |
        //    V

        // verify that both connections are indeed dead
        assertThat(checkNotNull(localChannel).getCloseFuture().isDone(), equalTo(true));
        assertThat(checkNotNull(otherChannel).getCloseFuture().isDone(), equalTo(true));

        // verify the order of calls for the local device
        InOrder localInOrder = inOrder(localDevice.unicastListener);
        localInOrder.verify(localDevice.unicastListener, atLeastOnce()).onUnicastReady();
        localInOrder.verify(localDevice.unicastListener, times(1)).onDeviceConnected(otherDevice.did, otherDevice.userID);
        localInOrder.verify(localDevice.unicastListener, times(1)).onDeviceDisconnected(otherDevice.did);

        // verify the order of calls for the other device
        InOrder otherInOrder = inOrder(otherDevice.unicastListener);
        otherInOrder.verify(otherDevice.unicastListener, atLeastOnce()).onUnicastReady();
        otherInOrder.verify(otherDevice.unicastListener, times(1)).onDeviceConnected(localDevice.did, localDevice.userID);
        otherInOrder.verify(otherDevice.unicastListener, times(1)).onDeviceDisconnected(localDevice.did);
    }

    @Test
    public void shouldNotTriggerRaceConditionWhenManyPacketsAreBeingSentAndSocketClosedFromOtherSide()
            throws InterruptedException, ExDeviceUnavailable, ExecutionException,
            ExTransportUnavailable
    {
        // send a packet to the remote device (which will force a new channel to be created)
        // wait for it to be received
        final Channel localChannel = sendPacketAndWaitForItToBeReceived(localDevice, otherDevice, null, TEST_DATA);

        // system state:
        //
        // 1. localDevice (out) --------> (in) otherDevice

        // send a packet _from_ the other device to the local device (we will reuse the same channel)
        Channel otherChannel = sendPacketAndWaitForItToBeReceived(otherDevice, localDevice, null, TEST_DATA);

        // system state:
        //
        // 1. localDevice (in/out) <--------> (in/out) otherDevice

        // create and start a thread that'll send packets as fast as possible
        final AtomicBoolean keepWriting = new AtomicBoolean(true);
        Thread senderThread = new Thread(() -> {
            while(keepWriting.get()) {
                try {
                    for (int i = 0; i < 10; ++i) {
                        localChannel.write(TransportProtocolUtil.newDatagramPayload(TEST_DATA));
                    }
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        senderThread.start();

        l.info("started sending thread");

        Thread.sleep(2000);

        // close via the remote side
        // this will force a close event to be fired via the netty I/O thread
        otherChannel.close();

        // shut down the sender thread
        keepWriting.set(false);
        senderThread.join();
    }

    private void waitForIUnicastListenerCallbacksToBeTriggered(boolean waitForLocal, boolean waitForOther)
            throws InterruptedException
    {
        if (waitForLocal) {
            localDevice.unicastListener.deviceDisconnectedSemaphore.acquire();
            l.debug("local disconnected");
        }

        if (waitForOther) {
            otherDevice.unicastListener.deviceDisconnectedSemaphore.acquire();
            l.debug("remote disconnected");
        }
    }
}
