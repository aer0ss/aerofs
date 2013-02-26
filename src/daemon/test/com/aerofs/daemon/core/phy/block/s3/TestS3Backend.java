/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.daemon.core.phy.block.AbstractBlockTest;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.ContentHash;
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
import static org.mockito.Mockito.when;

public class TestS3Backend extends AbstractBlockTest
{
    private final S3TestConfig s3TestConfig = new S3TestConfig();

    private S3Backend bsb;

    @Before
    public void setUp() throws Exception
    {
        bsb = new S3Backend(s3TestConfig.getS3Client(testTempDirFactory),
                s3TestConfig.getBucketIdConfig(), s3TestConfig.getS3CryptoConfig());
        bsb.init_();
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

    void assertBlockNotFound(ContentHash key) {
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
        Token tk = mock(Token.class);
        TCB tcb = mock(TCB.class);
        when(tk.pseudoPause_(anyString())).thenReturn(tcb);

        TestBlock b = newBlock();
        put(bsb, b);

        bsb.deleteBlock(b._key, tk);

        verify(tk).pseudoPause_(anyString());
        verify(tcb).pseudoResumed_();

        assertBlockNotFound(b._key);
    }
}
