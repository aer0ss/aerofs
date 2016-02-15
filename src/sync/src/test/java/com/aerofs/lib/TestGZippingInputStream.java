package com.aerofs.lib;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.aerofs.base.BaseUtil;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.ByteStreams;

import com.aerofs.testlib.AbstractTest;

public class TestGZippingInputStream extends AbstractTest
{
    @Test
    public void shouldZipTestData() throws IOException
    {
        byte[] buf = "test".getBytes();
        checkData(buf);
    }

    @Test
    public void shouldZipRandomData() throws IOException
    {
        Random random = new Random(Integer.getInteger("seed", 0));

        for (int i = 0; i < 5; ++i) {
            int len = 20 << i;
            byte[] buf = new byte[len];
            random.nextBytes(buf);
            checkData(buf);
        }
    }

    private void checkData(byte[] buf) throws IOException
    {
        l.info("original: " + BaseUtil.hexEncode(buf));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        {
            InputStream in = new ByteArrayInputStream(buf);
            try {
                OutputStream out = new GZIPOutputStream(baos);
                try {
                    ByteStreams.copy(in, out);
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
        }

        byte[] compressed = baos.toByteArray();
        Assert.assertEquals(baos.size(), compressed.length);
        baos.reset();
        l.info("compressed: " + BaseUtil.hexEncode(compressed));

        {
            InputStream in = new GZippingInputStream(new ByteArrayInputStream(buf));
            try {
                ByteStreams.copy(in, baos);
            } finally {
                in.close();
            }
        }

        byte[] compressed2 = baos.toByteArray();
        baos.reset();
        l.info("compressed: " + BaseUtil.hexEncode(compressed2));

        {
            InputStream in = new GZIPInputStream(new ByteArrayInputStream(compressed));
            try {
                ByteStreams.copy(in, baos);
            } finally {
                in.close();
            }
        }

        byte[] uncompressed = baos.toByteArray();
        l.info("uncompressed: " + BaseUtil.hexEncode(uncompressed));

        Assert.assertArrayEquals(buf, uncompressed);
    }
}
