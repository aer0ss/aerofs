package com.aerofs.daemon.core.phy.block;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.ContentBlockHash;
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

    private final static byte[] EMPTY = new byte[0];
    private final static ContentBlockHash EMPTY_H = new ContentBlockHash(BaseSecUtil.hash(EMPTY));

    private final static byte[] FULL = new byte[(int) ClientParam.FILE_BLOCK_SIZE];
    private final static ContentBlockHash FULL_H = new ContentBlockHash(BaseSecUtil.hash(FULL));

    private final static byte[] PARTIAL = new byte[42];
    private final static ContentBlockHash PARTIAL_H = new ContentBlockHash(BaseSecUtil.hash(PARTIAL));

    private BlockInputStream is(long length, ContentBlockHash... h) {
        return new BlockInputStream(bsb, BlockUtil.concat(h), length);
    }

    @Before
    public void setUp() throws IOException
    {
        when(bsb.getBlock(EMPTY_H)).thenReturn(new ByteArrayInputStream(EMPTY));
        when(bsb.getBlock(FULL_H)).thenReturn(new ByteArrayInputStream(FULL));
        when(bsb.getBlock(PARTIAL_H)).thenReturn(new ByteArrayInputStream(PARTIAL));
    }

    @Test
    public void shouldSkipPast2Gb() throws IOException
    {
        byte[] h = new byte[ContentBlockHash.UNIT_LENGTH * (1 << 11)];
        for (int i = 0; i < (1 << 11); ++i) {
            System.arraycopy(FULL_H.getBytes(), 0, h, ContentBlockHash.UNIT_LENGTH * i,
                    ContentBlockHash.UNIT_LENGTH);
        }

        try (BlockInputStream bis = new BlockInputStream(bsb, new ContentBlockHash(h),
                (long)FULL.length * (1 << 11))) {
            assertEquals(1L << 32, bis.skip(1L << 32));
        }
    }

    @Test
    public void shouldSkipToEndEmptyHash() throws IOException {
        assertEquals(0, new BlockInputStream(bsb, new ContentBlockHash(new byte[0]), 0).skip(0));
    }

    @Test
    public void shouldSkipOutOfBoundsEmptyHash() throws IOException {
        assertEquals(0, is(0, EMPTY_H).skip(10));
    }

    @Test
    public void shouldSkipToEndEmpty() throws IOException {
        assertEquals(0, is(0, EMPTY_H).skip(0));
    }

    @Test
    public void shouldSkipOutOfBoundsEmpty() throws IOException {
        assertEquals(0, new BlockInputStream(bsb, new ContentBlockHash(new byte[0]), 0).skip(10));
    }

    @Test
    public void shouldSkipZeroNonEmpty() throws IOException {
        assertEquals(0, is(FULL.length, FULL_H).skip(0));
    }

    @Test
    public void shouldSkipInBoundsFullBlock() throws IOException {
        assertEquals(10, is(FULL.length, FULL_H).skip(10));
    }

    @Test
    public void shouldSkipToEndFullBlock() throws IOException {
        assertEquals(FULL.length, is(FULL.length, FULL_H).skip(FULL.length));
    }

    @Test
    public void shouldSkipOutOfBoundsFullBlock() throws IOException {
        assertEquals(FULL.length, is(FULL.length, FULL_H).skip(FULL.length + 10));
    }

    @Test
    public void shouldSkipInBoundsPartialBlock() throws IOException {
        assertEquals(FULL.length + 10, is(FULL.length + PARTIAL.length, FULL_H, PARTIAL_H)
                .skip(FULL.length + 10));
    }

    @Test
    public void shouldSkipToEndPartialBlock() throws IOException {
        assertEquals(FULL.length + PARTIAL.length, is(FULL.length + PARTIAL.length, FULL_H, PARTIAL_H)
                .skip(FULL.length + PARTIAL.length));
    }

    @Test
    public void shouldSkipOutOfBoundsPartialBlock() throws IOException {
        assertEquals(FULL.length + PARTIAL.length, is(FULL.length + PARTIAL.length, FULL_H, PARTIAL_H)
                .skip(FULL.length + FULL.length));
    }
}
