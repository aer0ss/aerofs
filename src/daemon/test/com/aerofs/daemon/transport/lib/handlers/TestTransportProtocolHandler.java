/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIChunk;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportProtocolUtil;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTPHeader.Type;
import com.aerofs.testlib.LoggerSetup;
import com.google.common.base.Charsets;
import com.google.common.io.Closeables;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferOutputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.newControl;
import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.newDatagramPayload;
import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.newStreamPayload;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class TestTransportProtocolHandler
{
    static
    {
        LoggerSetup.init();
    }

    private static final int CHUNK_SIZE = 1024;
    private static final String TEST_STRING = "hello";
    private static final byte[] TEST_DATA = TEST_STRING.getBytes(Charsets.US_ASCII);
    private static final DID DID_0 = DID.generate();
    private static final UserID USER_0 = UserID.DUMMY;
    private static final InetSocketAddress REMOTE_ADDRESS_0 = new InetSocketAddress("localhost", 9999);

    private final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
    private final ChannelPipeline pipeline = mock(ChannelPipeline.class);
    private final Channel channel = mock(Channel.class);
    private final ITransport transport = mock(ITransport.class);
    private final StreamManager streamManager = new StreamManager();
    private final BlockingPrioQueue<IEvent> outgoingEventSink = new BlockingPrioQueue<IEvent>(10);
    private final TransportProtocolHandler handler = new TransportProtocolHandler(transport, outgoingEventSink, streamManager);

    @Before
    public void setup()
    {
        when(ctx.getChannel()).thenReturn(channel);
        when(channel.getPipeline()).thenReturn(pipeline);
    }

    @Test
    public void shouldSendEIUnicastMessageToCoreWhenADatagramIsReceived()
            throws Exception
    {
        ChannelBuffer datagramBuffer = serializeToChannelBuffer(newDatagramPayload(TEST_DATA));
        TransportMessage datagramMessage = new TransportMessage(datagramBuffer, DID_0, USER_0);

        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, datagramMessage, REMOTE_ADDRESS_0));

        EIUnicastMessage message = (EIUnicastMessage) outgoingEventSink.tryDequeue(new OutArg<Prio>(null));
        message = checkNotNull(message);

        assertThat(message._ep, equalTo(new Endpoint(transport, DID_0)));
        assertThat(message._userID, equalTo(USER_0));

        assertThatIncomingDataMatchesTestData(message.is());
    }

    @Test
    public void shouldProcessIncomingStreamMessagesWhenTheyAreReceivedAndSendStreamEventsToCore()
            throws Exception
    {
        StreamID streamId = new StreamID(1999);

        //
        // send the BEGIN_STREAM message
        //

        ChannelBuffer beginStreamBuffer = newBeginStreamBuffer(streamId);
        TransportMessage beginStreamMessage = new TransportMessage(beginStreamBuffer, DID_0, USER_0);
        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, beginStreamMessage, REMOTE_ADDRESS_0));

        // we shouldn't enqueue an event in the core
        assertThat(outgoingEventSink.tryDequeue(new OutArg<Prio>(null)), nullValue());

        //
        // send stream chunk 0
        //

        ChannelBuffer chunk0Buffer = serializeToChannelBuffer(newStreamPayload(streamId, 0, TEST_DATA));
        TransportMessage chunk0Message = new TransportMessage(chunk0Buffer, DID_0, USER_0);
        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, chunk0Message, REMOTE_ADDRESS_0));

        // verify that the stream has now truly begun
        assertThat(streamManager.hasStreamBegun(DID_0, streamId), equalTo(true));

        // we _did_ send a EIStreamBegun to the core
        EIStreamBegun streamBegun = (EIStreamBegun) outgoingEventSink.tryDequeue(new OutArg<Prio>(null));
        streamBegun = checkNotNull(streamBegun);

        assertThat(streamBegun._ep, equalTo(new Endpoint(transport, DID_0)));
        assertThat(streamBegun._userID, equalTo(USER_0));
        assertThat(streamBegun._streamId, equalTo(streamId));
        assertThat(streamBegun._seq, equalTo(0));
        assertThatIncomingDataMatchesTestData(streamBegun.is());

        //
        // send stream chunk 1
        //

        ChannelBuffer chunk1Buffer = serializeToChannelBuffer(newStreamPayload(streamId, 1, TEST_DATA));
        TransportMessage chunk1Message = new TransportMessage(chunk1Buffer, DID_0, USER_0);
        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, chunk1Message, REMOTE_ADDRESS_0));

        // verify that we have not changed the StreamManager state
        assertThat(streamManager.hasStreamBegun(DID_0, streamId), equalTo(true));

        // but that we _did_ send a EIChunk to the core
        EIChunk chunk = (EIChunk) outgoingEventSink.tryDequeue(new OutArg<Prio>(null));
        chunk = checkNotNull(chunk);

        assertThat(chunk._ep, equalTo(new Endpoint(transport, DID_0)));
        assertThat(chunk._userID, equalTo(USER_0));
        assertThat(chunk._streamId, equalTo(streamId));
        assertThat(chunk._seq, equalTo(1));
        assertThatIncomingDataMatchesTestData(chunk.is());
    }

    @Test
    public void shouldReturnAnAbortIncomingStreamMessageIfAStreamDoesNotExist()
            throws Exception
    {
        StreamID streamId = new StreamID(1999);

        //
        // send the BEGIN_STREAM message
        //

        ChannelBuffer beginStreamBuffer = newBeginStreamBuffer(streamId);
        TransportMessage beginStreamMessage = new TransportMessage(beginStreamBuffer, DID_0, USER_0);
        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, beginStreamMessage, REMOTE_ADDRESS_0));

        // we shouldn't enqueue an event in the core
        assertThat(outgoingEventSink.tryDequeue(new OutArg<Prio>(null)), nullValue());

        //
        // send stream chunk 0
        //

        ChannelBuffer chunk0Buffer = serializeToChannelBuffer(newStreamPayload(streamId, 0, TEST_DATA));
        TransportMessage chunk0Message = new TransportMessage(chunk0Buffer, DID_0, USER_0);
        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, chunk0Message, REMOTE_ADDRESS_0));

        // verify that the stream has now truly begun
        assertThat(streamManager.hasStreamBegun(DID_0, streamId), equalTo(true));

        // we _did_ send a EIStreamBegun to the core
        EIStreamBegun streamBegun = (EIStreamBegun) outgoingEventSink.tryDequeue(new OutArg<Prio>(null));
        streamBegun = checkNotNull(streamBegun);

        assertThat(streamBegun._ep, equalTo(new Endpoint(transport, DID_0)));
        assertThat(streamBegun._userID, equalTo(USER_0));
        assertThat(streamBegun._streamId, equalTo(streamId));
        assertThat(streamBegun._seq, equalTo(0));
        assertThatIncomingDataMatchesTestData(streamBegun.is());

        //
        // now, let's remove that stream
        //

        streamManager.removeIncomingStream(DID_0, streamId);
        assertThat(streamManager.streamExists(DID_0, streamId), is(false)); // it's removed

        //
        // send stream chunk 1
        //

        ChannelBuffer chunk1Buffer = serializeToChannelBuffer(newStreamPayload(streamId, 1, TEST_DATA));
        TransportMessage chunk1Message = new TransportMessage(chunk1Buffer, DID_0, USER_0);
        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, chunk1Message, REMOTE_ADDRESS_0));

        // at this point we should attempt to send out an AbortIncomingStream message

        ArgumentCaptor<ChannelEvent> eventCaptor = ArgumentCaptor.forClass(ChannelEvent.class);
        verify(ctx).sendDownstream(eventCaptor.capture());

        // it should be a MessageEvent
        MessageEvent message = (MessageEvent) eventCaptor.getValue();

        // pick out the stream abort reply bytes and verify that it has the correct values
        byte[][] abortStreamBytes = (byte[][]) message.getMessage();
        assertThat(abortStreamBytes, notNullValue());

        PBTPHeader deserializedHeader = PBTPHeader.parseDelimitedFrom(new ByteArrayInputStream(abortStreamBytes[0]));
        assertThat(deserializedHeader.getType(), equalTo(Type.STREAM));

        PBStream streamHeader = deserializedHeader.getStream();
        assertThat(streamHeader.getType(), equalTo(PBStream.Type.RX_ABORT_STREAM));
        assertThat(streamHeader.getStreamId(), equalTo(streamId.getInt()));
    }

    @Test
    public void shouldAbortStreamWhenSenderSendsAnAbortOutgoingStreamMessage()
            throws Exception
    {
        StreamID streamId = new StreamID(1999);

        //
        // send the BEGIN_STREAM message
        //

        ChannelBuffer beginStreamBuffer = newBeginStreamBuffer(streamId);
        TransportMessage beginStreamMessage = new TransportMessage(beginStreamBuffer, DID_0, USER_0);
        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, beginStreamMessage, REMOTE_ADDRESS_0));

        // we shouldn't enqueue an event in the core
        assertThat(outgoingEventSink.tryDequeue(new OutArg<Prio>(null)), nullValue());

        //
        // send stream chunk 0
        //

        ChannelBuffer chunk0Buffer = serializeToChannelBuffer(newStreamPayload(streamId, 0, TEST_DATA));
        TransportMessage chunk0Message = new TransportMessage(chunk0Buffer, DID_0, USER_0);
        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, chunk0Message, REMOTE_ADDRESS_0));

        // verify that the stream has now truly begun
        assertThat(streamManager.hasStreamBegun(DID_0, streamId), equalTo(true));

        // we _did_ send a EIStreamBegun to the core
        EIStreamBegun streamBegun = (EIStreamBegun) outgoingEventSink.tryDequeue(new OutArg<Prio>(null));
        streamBegun = checkNotNull(streamBegun);

        assertThat(streamBegun._ep, equalTo(new Endpoint(transport, DID_0)));
        assertThat(streamBegun._userID, equalTo(USER_0));
        assertThat(streamBegun._streamId, equalTo(streamId));
        assertThat(streamBegun._seq, equalTo(0));
        assertThatIncomingDataMatchesTestData(streamBegun.is());

        //
        // send abort outgoing stream message
        //

        ChannelBuffer abortStreamBuffer = serializeToChannelBuffer(
                TransportProtocolUtil.newControl(TransportProtocolUtil.newAbortOutgoingStreamHeader(
                        streamId,
                        InvalidationReason.UPDATE_IN_PROGRESS)));
        TransportMessage abortStreamMessage = new TransportMessage(abortStreamBuffer, DID_0, USER_0);
        handler.messageReceived(ctx, new UpstreamMessageEvent(channel, abortStreamMessage, REMOTE_ADDRESS_0));

        // verify that we have removed the stream
        assertThat(streamManager.streamExists(DID_0, streamId), is(false));
    }

    private ChannelBuffer newBeginStreamBuffer(StreamID streamId)
            throws IOException
    {
        return serializeToChannelBuffer(
                    newControl(PBTPHeader.newBuilder()
                                    .setType(Type.STREAM)
                                    .setStream(PBStream.newBuilder()
                                            .setType(PBStream.Type.BEGIN_STREAM)
                                            .setStreamId(streamId.getInt()))
                            .build()));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalStateException.class)
    public void shouldNotSwallowException()
            throws Exception
    {
        UpstreamMessageEvent brokenEvent = mock(UpstreamMessageEvent.class);
        when(brokenEvent.getMessage()).thenThrow(IllegalStateException.class);

        handler.messageReceived(ctx, brokenEvent);
    }

    private ChannelBuffer serializeToChannelBuffer(byte[][] wireChunks)
            throws IOException
    {
        ChannelBufferOutputStream os = null;
        try {
            ChannelBuffer channelBuffer = ChannelBuffers.buffer(CHUNK_SIZE);
            os = new ChannelBufferOutputStream(channelBuffer);
            for (byte[] wireChunk : wireChunks) os.write(wireChunk);
            return channelBuffer;
        } finally {
            Closeables.close(os, true);
        }
    }

    private void assertThatIncomingDataMatchesTestData(InputStream payloadInputstream)
            throws IOException
    {
        byte[] incomingBytes = new byte[CHUNK_SIZE];
        int dataSize = payloadInputstream.read(incomingBytes);

        assertThat(dataSize, equalTo(TEST_DATA.length));
        assertThat(new String(incomingBytes, 0, dataSize, Charsets.US_ASCII), equalTo(TEST_STRING));
    }
}
