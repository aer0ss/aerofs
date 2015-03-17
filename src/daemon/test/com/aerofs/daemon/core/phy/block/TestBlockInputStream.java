package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.LibParam;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class TestBlockInputStream extends AbstractTest
{
    @Mock IBlockStorageBackend bsb;

    private final static byte[] FULL = new byte[(int)LibParam.FILE_BLOCK_SIZE];
    private final static ContentBlockHash FULL_H = new ContentBlockHash(BaseSecUtil.hash(FULL));

    @Before
    public void setUp() throws IOException
    {
        when(bsb.getBlock(FULL_H)).thenReturn(new ByteArrayInputStream(FULL));
    }

    @Test
    public void shouldSkipPast2Gb() throws IOException
    {
        byte[] h = new byte[ContentBlockHash.UNIT_LENGTH * (1 << 11)];
        for (int i = 0; i < (1 << 11); ++i) {
            System.arraycopy(FULL_H.getBytes(), 0, h, ContentBlockHash.UNIT_LENGTH * i,
                    ContentBlockHash.UNIT_LENGTH);
        }

        try (BlockInputStream bis = new BlockInputStream(bsb, new ContentBlockHash(h))) {
            assertEquals(1L << 32, bis.skip(1L << 32));
        }
    }
}
