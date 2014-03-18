/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.daemon.core.phy.block.AbstractBlockTest;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.TokenWrapper;
import com.aerofs.lib.ContentBlockHash;
import com.amazonaws.AmazonServiceException;
import com.google.common.io.ByteStreams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TestS3Backend extends AbstractBlockTest
{
    private final S3TestConfig s3TestConfig = new S3TestConfig();

    private S3Backend bsb;

    @Before
    public void setUp() throws Exception
    {
        bsb = new S3Backend(s3TestConfig.getS3Client(testTempDirFactory),
                s3TestConfig.getBucketIdConfig(), s3TestConfig.getS3CryptoConfig());
        S3MagicChunk magic = new S3MagicChunk();
        bsb.init_();
        magic.init_(bsb);
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

    void assertBlockNotFound(ContentBlockHash key) {
        try {
            bsb.getBlock(key);
            Assert.assertTrue(false);
        } catch (IOException e) {
            if (e.getCause() instanceof AmazonServiceException) {
                AmazonServiceException ae = (AmazonServiceException)e.getCause();
                Assert.assertTrue(ae.getStatusCode() == 404 && ae.getErrorCode().equals("NoSuchKey"));
            }
        }
    }

    // The magic chunk is an empty block, we should always be able to fetch it after S3Backend.init_
    @Test
    public void shouldFetchEmptyBlock() throws Exception
    {
        TestBlock b = newEmptyBlock();
        Assert.assertArrayEquals(b._content, ByteStreams.toByteArray(bsb.getBlock(b._key)));
    }

    @Test
    public void shouldStoreAndFetchBlock() throws Exception
    {
        TestBlock b = newBlock();
        put(bsb, b);
        Assert.assertArrayEquals(b._content, ByteStreams.toByteArray(bsb.getBlock(b._key)));
    }

    // TODO: implement FakeS3Client.deleteObject
    @Ignore
    @Test
    public void shouldDeleteExistingBlock() throws Exception
    {
        TokenWrapper tk = mock(TokenWrapper.class);
        TestBlock b = newBlock();
        put(bsb, b);

        bsb.deleteBlock(b._key, tk);

        verify(tk).pseudoPause(anyString());
        verify(tk).pseudoResumed();

        assertBlockNotFound(b._key);
    }
}
