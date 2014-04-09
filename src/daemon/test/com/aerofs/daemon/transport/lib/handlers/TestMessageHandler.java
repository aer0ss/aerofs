/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.transport.TransportLoggerSetup;
import com.aerofs.daemon.transport.lib.ChannelData;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.aerofs.rocklog.Defect;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import org.hamcrest.Matchers;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.DefaultExceptionEvent;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.UpstreamChannelStateEvent;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestMessageHandler
{
    static
    {
        TransportLoggerSetup.init();
    }

    private static final InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved("remote.aerofs.com", 8888);

    private final Random r = new Random();
    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private final Channel channel = mock(Channel.class);
    private final ChannelData channelData = new ChannelData(UserID.DUMMY, DID.generate());
    private final ChannelFuture closeFuture = Channels.future(channel);
    private final ChannelPipeline pipeline = mock(ChannelPipeline.class);
    private final ChannelStateEvent openEvent = new UpstreamChannelStateEvent(channel, ChannelState.OPEN, Boolean.TRUE);
    private final ChannelStateEvent connectedEvent = new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED, remoteAddress);
    private final ExceptionEvent exceptionEvent = new DefaultExceptionEvent(channel, new IOException("power switch off"));
    private final RockLog rockLog = mock(RockLog.class);
    private final Defect defect = mock(Defect.class);
    private final MessageHandler handler = new MessageHandler(rockLog); // SUT

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup()
            throws Exception
    {
        when(ctx.getChannel()).thenReturn(channel);
        when(ctx.getPipeline()).thenReturn(pipeline);
        when(channel.getCloseFuture()).thenReturn(closeFuture);
        when(pipeline.getLast()).thenReturn(mock(ChannelHandler.class));

        when(rockLog.newDefect(anyString())).thenReturn(defect);
        when(defect.setMessage(anyString())).thenReturn(defect);
        when(defect.setException(any(Throwable.class))).thenReturn(defect);
        when(defect.addData(anyString(), anyString())).thenReturn(defect);

        handler.channelOpen(ctx, openEvent);
    }

    @Test
    public void shouldNotSendPacketsIfChannelIsNotConnected()
            throws Exception
    {
        handler.writeRequested(ctx, newMessageEvent());
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));
    }

    @Ignore
    @Test
    public void shouldFailAllPendingPacketsIfConnectFails()
    {
        // FIXME (AG): it's not clear to me if I should hook into the connect future
    }

    @Test
    public void shouldFailAllPendingPacketsIfConnectedEventFiredBeforeCNameVerified()
            throws Exception
    {
        // create a number of packets and send them off
        handler.writeRequested(ctx, newMessageEvent());

        // verify that they're actually not passed downstream
        verify(ctx, times(0)).sendDownstream(any(DownstreamMessageEvent.class));

        // now, signal that the channel is connected
        // we should get an exception because we haven't signalled that the peer was verified
        expectedException.expect(IllegalStateException.class);
        handler.channelConnected(ctx, connectedEvent);
    }

    @Test
    public void shouldSendAllPendingPacketsWhenChannelBecomesConnected()
            throws Exception
    {
        // create a number of packets and send them off
        List<ChannelFuture> writeFutures = Lists.newArrayList();
        List<ChannelBuffer> buffers = Lists.newArrayList();
        for (int i = 0; i < 4; i++) {
            DownstreamMessageEvent messageEvent = newMessageEvent();

            handler.writeRequested(ctx, messageEvent);

            buffers.add(ChannelBuffers.wrappedBuffer((byte[][]) messageEvent.getMessage()));
            writeFutures.add(messageEvent.getFuture());
        }

        // store sent packets in this list
        final List<ChannelBuffer> sentBuffers = Lists.newLinkedList();
        doAnswer(new Answer<Void>()
        {
            @Override
            public Void answer(InvocationOnMock invocation)
                    throws Throwable
            {
                DownstreamMessageEvent messageEvent = (DownstreamMessageEvent) invocation.getArguments()[0];
                sentBuffers.add((ChannelBuffer) messageEvent.getMessage());
                return null;
            }
        }).when(ctx).sendDownstream(any(ChannelEvent.class));

        // verify that they're actually not passed downstream
        assertThat(sentBuffers, hasSize(0));

        // now, set up the ChannelData (peer was verified)
        setupChannelDataForVerifiedChannel();

        // now, signal that the channel is connected
        handler.channelConnected(ctx, connectedEvent);

        // we should fire the channel connected event on
        // 2 because the first event is for the channel open event
        verify(ctx, times(2)).sendUpstream(any(UpstreamChannelStateEvent.class));

        // this should trigger the sending of these packets
        verify(ctx, times(4)).sendDownstream(any(DownstreamMessageEvent.class));

        // let's check that these packets were actually sent out, and, in the order they were added
        assertThat(sentBuffers, contains(buffers.toArray()));

        // but their futures were still not triggered
        for (ChannelFuture future : writeFutures) {
            assertThat(future.isDone(), is(false));
        }
    }

    @Test
    public void shouldFailAllPendingPacketsIfChannelIsClosed()
            throws Exception
    {
        // create a number of packets and send them off
        List<ChannelFuture> writeFutures = Lists.newArrayList();
        for (int i = 0; i < 4; i++) {
            DownstreamMessageEvent messageEvent = newMessageEvent();
            handler.writeRequested(ctx, messageEvent);
            writeFutures.add(messageEvent.getFuture());
        }

        // we shouldn't have sent anything downstream
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));

        // we shouldn't have triggered their futures
        for (ChannelFuture future : writeFutures) {
            assertThat(future.isDone(), is(false));
        }

        // now, close the channel
        closeFuture.setFailure(new IOException("violent angry unfeeling lonely death"));

        // all the futures should have been tripped
        for (ChannelFuture future : writeFutures) {
            assertThat(future.isDone(), is(true));
            assertThat(future.isSuccess(), is(false));
            assertThat(future.getCause(), notNullValue());
        }

        // and none of them should have been sent downstream
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));
    }

    @Test // FIXME (AG): is the current behavior correct?
    public void shouldPropagateExceptionEventAndFailAllPendingPacketsIfExceptionIsThrownInPipeline()
            throws Exception
    {
        // create a number of packets and send them off
        List<ChannelFuture> writeFutures = Lists.newArrayList();
        for (int i = 0; i < 4; i++) {
            DownstreamMessageEvent messageEvent = newMessageEvent();
            handler.writeRequested(ctx, messageEvent);
            writeFutures.add(messageEvent.getFuture());
        }

        // we shouldn't have sent anything downstream
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));

        // we shouldn't have triggered their futures
        for (ChannelFuture future : writeFutures) {
            assertThat(future.isDone(), is(false));
        }

        // now, signal that any exception was generated down the pipeline
        handler.exceptionCaught(ctx, exceptionEvent);

        // this exception event is forwarded upstream
        verify(ctx, times(1)).sendUpstream(exceptionEvent);

        // nothing should have been sent downstream still
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));

        // the ChannelTeardownHandler will close the channel
        closeFuture.setFailure(new IOException("down down down"));

        // all the futures should have been tripped
        // and the cause is the one given in the exception event
        for (ChannelFuture future : writeFutures) {
            assertThat(future.isDone(), is(true));
            assertThat(future.isSuccess(), is(false));
            assertThat(future.getCause(), sameInstance(exceptionEvent.getCause()));
        }

        // and we haven't accidentally sent off any packets
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));
    }

    @Test
    public void shouldFailSendImmediatelyIfWriteIsCalledAfterTheChannelIsClosedBeforeThePeerIsVerified()
            throws Exception
    {
        // channel was closed
        closeFuture.setFailure(new IOException("cat chewed through the cable"));

        // attempt to send a packet
        DownstreamMessageEvent messageEvent = newMessageEvent();
        handler.writeRequested(ctx, messageEvent);

        // it should have been failed immediately
        assertThat(messageEvent.getFuture().isDone(), is(true));
        assertThat(messageEvent.getFuture().isSuccess(), is(false));
        assertThat(messageEvent.getFuture().getCause(), notNullValue());

        // we didn't send the packet downstream
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));
    }

    @Test
    public void shouldFailSendImmediatelyIfWriteIsCalledAfterTheChannelIsClosedAfterThePeerIsVerified()
            throws Exception
    {
        // verify the peer
        setupChannelDataForVerifiedChannel();

        // now signal that the channel was connected
        handler.channelConnected(ctx, connectedEvent);

        // check that we don't send anything downstream
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));

        // now signal that the channel was closed
        closeFuture.setFailure(new IOException("cat chewed through the cable"));

        // attempt to send a packet
        DownstreamMessageEvent messageEvent = newMessageEvent();
        handler.writeRequested(ctx, messageEvent);

        // it should have been failed immediately
        assertThat(messageEvent.getFuture().isDone(), is(true));
        assertThat(messageEvent.getFuture().isSuccess(), is(false));
        assertThat(messageEvent.getFuture().getCause(), notNullValue());

        // didn't send the failed packet downstream
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));
    }

    @Test
    public void shouldWriteImmediatelyIfChannelConnectedAndPacketIsSent()
            throws Exception
    {
        // verify the peer
        setupChannelDataForVerifiedChannel();

        // now signal that the channel was connected
        handler.channelConnected(ctx, connectedEvent);

        // check that we don't send anything downstream
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));

        // attempt to send a packet
        DownstreamMessageEvent messageEvent = newMessageEvent();
        handler.writeRequested(ctx, messageEvent);

        // we should send exactly one packet out immediately
        ArgumentCaptor<ChannelEvent> eventCaptor = ArgumentCaptor.forClass(ChannelEvent.class);
        verify(ctx).sendDownstream(eventCaptor.capture());

        // it should be the data we attempted to send
        DownstreamMessageEvent sentEvent = (DownstreamMessageEvent) eventCaptor.getValue();
        assertThat(sentEvent.getMessage(), Matchers.<Object>is(ChannelBuffers.wrappedBuffer((byte[][])messageEvent.getMessage())));

        // but we still didn't trigger the future
        assertThat(messageEvent.getFuture().isDone(), is(false));
        assertThat(sentEvent.getFuture(), sameInstance(messageEvent.getFuture()));
    }

    @Test
    public void shouldTurnAValidIncomingPacketIntoATransportMessage()
            throws Exception
    {
        // verify the peer
        setupChannelDataForVerifiedChannel();

        // fire the channel connected event
        handler.channelConnected(ctx, connectedEvent);

        // incoming _valid_ packet
        int payloadSize = 4;
        byte[] randomBytes = new byte[payloadSize];
        r.nextBytes(randomBytes);
        ChannelBuffer incomingBuffer = ChannelBuffers.wrappedBuffer(TransportProtocolUtil.newDatagramPayload(randomBytes));

        // this is the upstream event
        UpstreamMessageEvent messageEvent = new UpstreamMessageEvent(channel, incomingBuffer, remoteAddress);

        // receive the message event
        handler.messageReceived(ctx, messageEvent);

        // we should create an instance of a TransportMessage and attempt to send it upstream
        ArgumentCaptor<ChannelEvent> eventCaptor = ArgumentCaptor.forClass(ChannelEvent.class);
        // 3 events: 1=channelopen, 2=channelconnected, 3=messageevent
        verify(ctx, times(3)).sendUpstream(eventCaptor.capture());

        // check that the message is correct
        TransportMessage transportMessage = (TransportMessage) ((UpstreamMessageEvent) eventCaptor.getAllValues().get(2)).getMessage();
        assertThat(transportMessage.getHeader().getType(), is(Type.DATAGRAM));
        assertThat(transportMessage.getUserID(), is(channelData.getRemoteUserID()));
        assertThat(transportMessage.getDID(), is(channelData.getRemoteDID()));
        assertThat(transportMessage.isPayload(), is(true));
        assertThat(transportMessage.getPayload().available(), is(payloadSize));

        byte[] deserializedPayload = new byte[payloadSize];
        //noinspection ResultOfMethodCallIgnored
        transportMessage.getPayload().read(deserializedPayload);
        assertThat(Arrays.equals(deserializedPayload, randomBytes), is(true));
    }

    @Test
    public void shouldThrowIfIncomingPacketReceivedBeforePeerVerified()
            throws IOException
    {
        // incoming _valid_ packet
        byte[] randomBytes = new byte[4];
        r.nextBytes(randomBytes);
        ChannelBuffer incomingBuffer = ChannelBuffers.wrappedBuffer(TransportProtocolUtil.newDatagramPayload(randomBytes));

        // this is the upstream event
        UpstreamMessageEvent messageEvent = new UpstreamMessageEvent(channel, incomingBuffer, remoteAddress);

        // receive the message event
        // since the peer is not verified we should throw
        expectedException.expect(IllegalStateException.class);
        handler.messageReceived(ctx, messageEvent);

        // check that no message was fired upstream
        verify(ctx, times(0)).sendUpstream(any(UpstreamMessageEvent.class));
    }

    @Test
    public void shouldThrowIfIncomingPacketCannotBeTransformedIntoATransportMessage()
            throws Exception
    {
        // verify the peer
        setupChannelDataForVerifiedChannel();

        // fire the channel connected event
        handler.channelConnected(ctx, connectedEvent);

        // check that nothing was fired downstream
        verify(ctx, times(0)).sendDownstream(any(ChannelEvent.class));

        // incoming _invalid_ packet
        byte[] randomBytes = new byte[4];
        r.nextBytes(randomBytes);
        ChannelBuffer incomingBuffer = ChannelBuffers.wrappedBuffer(new byte[][] {{0}, randomBytes});

        // this is the upstream event
        UpstreamMessageEvent messageEvent = new UpstreamMessageEvent(channel, incomingBuffer, remoteAddress);

        // receive the message event
        // the peer is verified but we have a bad packet,, so this should throw
        expectedException.expect(InvalidProtocolBufferException.class);
        handler.messageReceived(ctx, messageEvent);

        // check that no message was fired upstream
        verify(ctx, times(0)).sendUpstream(any(UpstreamMessageEvent.class));
    }

    private void setupChannelDataForVerifiedChannel()
    {
        when(channel.getAttachment()).thenReturn(channelData);
    }

    private DownstreamMessageEvent newMessageEvent()
    {
        byte[] randomBytes = new byte[4];
        r.nextBytes(randomBytes);

        byte[][] testData = new byte[][] {{0}, randomBytes};
        return new DownstreamMessageEvent(channel, Channels.future(channel), testData, remoteAddress);
    }
}
