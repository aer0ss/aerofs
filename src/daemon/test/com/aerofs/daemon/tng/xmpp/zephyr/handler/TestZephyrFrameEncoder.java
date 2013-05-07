package com.aerofs.daemon.tng.xmpp.zephyr.handler;

import com.aerofs.base.net.ZephyrConstants;
import com.aerofs.daemon.tng.xmpp.zephyr.message.ZephyrBindRequest;
import com.aerofs.lib.LibParam;
import com.aerofs.testlib.AbstractTest;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestZephyrFrameEncoder extends AbstractTest
{
    EncoderEmbedder<ChannelBuffer> encoder;

    @Before
    public void setUp()
    {
        encoder = new EncoderEmbedder<ChannelBuffer>(
                new ZephyrFrameEncoder());
    }

    @Test
    public void shouldProcessBindRequest() throws Exception
    {
        // Generate the expected output buffer
        final int expectedZid = 1;
        ChannelBuffer expectedOutputBuffer = ChannelBuffers.dynamicBuffer();
        expectedOutputBuffer.writeBytes(ZephyrConstants.ZEPHYR_MAGIC);
        expectedOutputBuffer.writeInt(4);
        expectedOutputBuffer.writeInt(expectedZid);
        final byte[] expectedOutput = new byte[expectedOutputBuffer.readableBytes()];
        expectedOutputBuffer.readBytes(expectedOutput);

        // Create the message to send
        ZephyrBindRequest request = new ZephyrBindRequest(expectedZid);

        // Feed the input into the system
        encoder.offer(request);
        encoder.finish();

        ChannelBuffer outputBuffer = encoder.poll();
        byte[] output = new byte[outputBuffer.readableBytes()];
        outputBuffer.readBytes(output);

        Assert.assertArrayEquals(expectedOutput, output);
    }

    @Test
    public void shouldProcessOutgoingPayload() throws Exception
    {
        // Generate the incoming client message
        final byte[] expectedMessage = "Hello, this is a test message".getBytes();

        // Generate the expected output buffer
        ChannelBuffer expectedOutputBuffer = ChannelBuffers.dynamicBuffer();
        expectedOutputBuffer.writeInt(LibParam.CORE_MAGIC);
        expectedOutputBuffer.writeInt(expectedMessage.length);
        expectedOutputBuffer.writeBytes(expectedMessage);

        final byte[] expectedOutput = new byte[expectedOutputBuffer.readableBytes()];
        expectedOutputBuffer.readBytes(expectedOutput);

        // Generate the input
        ChannelBuffer inputBuffer = ChannelBuffers.dynamicBuffer();
        inputBuffer.writeBytes(expectedMessage);

        // Feed the input into the system
        encoder.offer(inputBuffer);
        encoder.finish();

        ChannelBuffer outputBuffer = encoder.poll();
        byte[] output = new byte[outputBuffer.readableBytes()];
        outputBuffer.readBytes(output);

        Assert.assertArrayEquals(expectedOutput, output);
    }

}
