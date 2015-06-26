package com.aerofs.daemon.transport.lib;

import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.ids.DID;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestIncomingStream {

    private final static long TIMEOUT = TimeUnit.MILLISECONDS.convert(1, TimeUnit.SECONDS);

    private final DID did = DID.generate();
    private final StreamKey sk = new StreamKey(did, new StreamID(ThreadLocalRandom.current().nextInt()));

    private final Channel channel = mock(Channel.class);
    private final ChannelPipeline pipeline = mock(ChannelPipeline.class);

    private final static byte[] BUFFER = new byte[128];

    @Before
    public void setUp() {
        when(channel.toString()).thenReturn("[Channel]");
        when(channel.getPipeline()).thenReturn(pipeline);
    }

    @Test
    public void shouldNotBeginTwice() throws Exception {
        IncomingStream is = new IncomingStream(sk, channel, TIMEOUT);
        assertTrue(is.begin());
        assertFalse(is.begin());
    }

    @Test
    public void shouldRejectChunkForUnstarted() throws Exception {
        IncomingStream is = new IncomingStream(sk, channel, TIMEOUT);
        try {
            is.offer(ChannelBuffers.wrappedBuffer(BUFFER));
            fail();
        } catch (ExStreamInvalid e) {

        }
    }

    @Test
    public void shouldRejectOutOfOrderChunks() throws Exception {
        IncomingStream is = new IncomingStream(sk, channel, TIMEOUT);
        assertTrue(is.begin());
        is.offer(10, ChannelBuffers.wrappedBuffer(BUFFER));

        try {
            is.read();
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().startsWith("stream failed:"));
        }
    }

    @Test
    public void shouldThrowWhenReadClosed() throws Exception {
        IncomingStream is = new IncomingStream(sk, channel, TIMEOUT);
        assertTrue(is.begin());
        is.close();

        try {
            is.read();
            fail();
        } catch (IOException e) {
            assertEquals("stream closed", e.getMessage());
        }
    }

    @Test
    public void shouldThrowWhenReadFailed() throws Exception {
        IncomingStream is = new IncomingStream(sk, channel, TIMEOUT);
        assertTrue(is.begin());
        is.fail(InvalidationReason.ENDED);

        try {
            is.read();
            fail();
        } catch (IOException e) {
            assertTrue(e.getMessage().startsWith("stream failed:"));
        }
    }

    @Test
    public void shouldTimeout() throws Exception {
        IncomingStream is = new IncomingStream(sk, channel, TIMEOUT);
        assertTrue(is.begin());

        try {
            is.read();
            fail();
        } catch (IOException e) {
            assertEquals("stream timeout", e.getMessage());
        }
    }

    @Test
    public void shouldStreamAndTimeout() throws Exception {
        IncomingStream is = new IncomingStream(sk, channel, TIMEOUT);
        assertTrue(is.begin());
        is.offer(ChannelBuffers.wrappedBuffer(BUFFER));

        for (int i = 0; i < BUFFER.length; ++i) {
            assertEquals(BUFFER[i], is.read());
        }

        try {
            is.read();
            fail();
        } catch (IOException e) {
            assertEquals("stream timeout", e.getMessage());
        }
    }

    @Test
    public void shouldStream() throws Exception {
        IncomingStream is = new IncomingStream(sk, channel, TIMEOUT);
        assertTrue(is.begin());

        int COUNT = 100;

        Thread t = new Thread(() -> {
            try {
                for (int i = 0; i < COUNT; ++i) {
                    is.offer(ChannelBuffers.wrappedBuffer(BUFFER));
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                fail();
            }
        });
        t.start();

        try {
            int total = COUNT * BUFFER.length;
            byte[] buffer = new byte[1024];
            while (total > 0) {
                int n = is.read(buffer);
                if (n < 0) fail();
                total -= n;
            }
        } finally {
            t.join();
        }
    }
}
