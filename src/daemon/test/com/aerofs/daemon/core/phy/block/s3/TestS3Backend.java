/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.s3;

import com.aerofs.daemon.core.phy.block.AbstractBlockTest;
import com.aerofs.lib.aws.s3.S3TestConfig;
import com.aerofs.testlib.UnitTestTempDir;
import com.amazonaws.AmazonServiceException;
import com.google.common.io.ByteStreams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class TestS3Backend extends AbstractBlockTest
{
    private final S3TestConfig s3TestConfig = new S3TestConfig();

    @Rule
    public final UnitTestTempDir testTempDirFactory = new UnitTestTempDir();

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

        boolean ok = false;
        try {
            bsb.getBlock(b.getKey());
        } catch (IOException e) {
            if (e.getCause() instanceof AmazonServiceException) {
                AmazonServiceException ae = (AmazonServiceException)e.getCause();
                ok = (ae.getStatusCode() == 404 && ae.getErrorCode().equals("NoSuchKey"));
            }
        }
        Assert.assertTrue(ok);
    }

    private void put(TestBlock block) throws IOException
    {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        OutputStream encoder = bsb.wrapForEncoding(data, block);
        ByteStreams.copy(new ByteArrayInputStream(block._content), encoder);
        encoder.close();
        bsb.putBlock(block, new ByteArrayInputStream(data.toByteArray()));
    }

    @Test
    public void shouldStoreAndFetchBlock() throws Exception
    {
        TestBlock b = newBlock();
        put(b);
        Assert.assertArrayEquals(b._content, ByteStreams.toByteArray(bsb.getBlock(b.getKey())));
    }
}
