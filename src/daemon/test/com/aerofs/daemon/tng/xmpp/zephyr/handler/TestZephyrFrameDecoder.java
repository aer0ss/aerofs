package com.aerofs.daemon.tng.xmpp.zephyr.handler;

import com.aerofs.daemon.tng.xmpp.zephyr.Constants;
import com.aerofs.daemon.tng.xmpp.zephyr.message.IZephyrMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrDataMessage;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrRegistrationMessage;
import com.aerofs.lib.Param;
import com.aerofs.testlib.AbstractTest;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TestZephyrFrameDecoder extends AbstractTest
{
    DecoderEmbedder<IZephyrMessage> decoder;

    @Before
    public void setUp()
    {
        decoder = new DecoderEmbedder<IZephyrMessage>(
                new ZephyrFrameDecoder());
    }

    @Test
    public void shouldOutputRegistrationMessage() throws Exception
    {
        final int expectedZid = 23;

        // Generate the incoming registration message
        ChannelBuffer input = ChannelBuffers.dynamicBuffer();
        input.writeBytes(Constants.ZEPHYR_MAGIC);
        input.writeInt(4);
        input.writeInt(expectedZid);

        // Feed the input into the system
        decoder.offer(input);
        decoder.finish();

        // Verify the correct message type
        IZephyrMessage output = decoder.poll();
        assertTrue(output instanceof ZephyrRegistrationMessage);

        // Verify the correct value
        ZephyrRegistrationMessage message = (ZephyrRegistrationMessage) output;
        assertEquals(expectedZid, message.zid);
    }

    @Test
    public void shouldOutputClientMessage() throws Exception
    {
        final byte[] expectedMessage = "Hello, this is a test message".getBytes();

        // Generate the incoming client message
        ChannelBuffer input = ChannelBuffers.dynamicBuffer();
        input.writeInt(Param.CORE_MAGIC);
        input.writeInt(expectedMessage.length);
        input.writeBytes(expectedMessage);

        // Feed the input into the system
        decoder.offer(input);
        decoder.finish();

        // Verify the correct message type
        IZephyrMessage output = decoder.poll();
        assertTrue(output instanceof ZephyrDataMessage);

        // Verify that the message received is what was sent
        ChannelBuffer buffer = ((ZephyrDataMessage) output).payload;
        assertEquals(expectedMessage.length, buffer.readableBytes());
        byte[] outputBytes = new byte[buffer.readableBytes()];
        buffer.readBytes(outputBytes);
        assertArrayEquals(expectedMessage, outputBytes);
    }

}
