package com.aerofs.daemon.core.phy.block.swift;

import com.aerofs.base.C;
import com.aerofs.daemon.core.phy.block.AbstractBlockTest;
import com.aerofs.lib.ContentBlockHash;
import com.google.common.io.ByteStreams;
import org.javaswift.joss.exception.CommandException;
import org.javaswift.joss.exception.CommandExceptionError;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class TestSwiftBackend extends AbstractBlockTest
{
    private final SwiftTestConfig swiftTestConfig = new SwiftTestConfig();
    private SwiftBackend swiftBackend;

    @Before
    public void setUp() throws Exception
    {
        swiftBackend = new SwiftBackend(
                swiftTestConfig.getSwiftConfig(),
                swiftTestConfig.getCryptoConfig()
        );

        SwiftMagicChunk magic = new SwiftMagicChunk();
        swiftBackend.init_();
        magic.init_(swiftBackend);
    }

    @After
    public void tearDown() throws Exception
    {

    }

    @Test
    public void shouldThrowWhenTryingToFetchInvalidBlock() throws Exception
    {
        TestBlock b = newBlock();

        assertBlockNotFound(b._key);
    }

    // The magic chunk is an empty block, we should always be able to fetch it after SwiftBackend.init_
    @Test
    public void shouldFetchEmptyBlock() throws Exception
    {
        TestBlock b = newEmptyBlock();
        Assert.assertArrayEquals(b._content, ByteStreams.toByteArray(swiftBackend.getBlock(b._key)));
    }

    @Test
    public void shouldStoreAndFetchBlock() throws Exception
    {
        TestBlock b = newBlock();
        put(swiftBackend, b);
        Assert.assertArrayEquals(b._content, ByteStreams.toByteArray(swiftBackend.getBlock(b._key)));
    }

    @Test
    public void shouldStoreAndFetchLargeBlock() throws Exception
    {
        byte[] d = new byte[4 * C.MB];
        ThreadLocalRandom.current().nextBytes(d);
        TestBlock b = new TestBlock(d);

        put(swiftBackend, b);
        Assert.assertArrayEquals(b._content, ByteStreams.toByteArray(swiftBackend.getBlock(b._key)));
    }

    // TODO: shouldDeleteExistingBlock
    // Not implemented in TestS3Client
    // Need a TokenWrapper, which is unused by Swift methods

    void assertBlockNotFound(ContentBlockHash key) throws IOException
    {
        try {
            swiftBackend.getBlock(key);
            Assert.assertTrue(false);
        } catch (CommandException e) {
            if (e.getCause() instanceof CommandException) {
                CommandException ce = (CommandException)e.getCause();
                Assert.assertTrue(ce.getHttpStatusCode() == 404 && ce.getError().equals(CommandExceptionError.ENTITY_DOES_NOT_EXIST));
            }
        }
    }
}
