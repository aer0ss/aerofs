/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.cache;

import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.phy.block.AbstractBlockTest;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.lib.cfg.CfgAbsDefaultAuxRoot;
import com.aerofs.testlib.InMemorySQLiteDBCW;
import com.google.common.io.ByteStreams;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestCacheBackend extends AbstractBlockTest
{
    @Mock Trans t;
    @Mock TransManager tm;
    @Mock CoreScheduler sched;
    @Mock CfgAbsDefaultAuxRoot auxRoot;

    // use in-memory DB
    InMemorySQLiteDBCW idbcw = new InMemorySQLiteDBCW();
    CacheDatabase cdb = new CacheDatabase(idbcw);

    @Mock IBlockStorageBackend bsb;

    CacheBackend cache;

    @Before
    public void setUp() throws Exception
    {
        idbcw.init_();
        new CacheSchema().create_(idbcw.getConnection().createStatement(), idbcw);

        when(tm.begin_()).thenReturn(t);
        when(auxRoot.get()).thenReturn(testTempDirFactory.getTestTempDir().getAbsolutePath());

        // shame @InjectMocks does not deal with a mix of Mock and real objects...
        cache = new CacheBackend(auxRoot, tm, sched, cdb, bsb);
        cache.init_();
    }

    @After
    public void tearDown() throws Exception
    {
        idbcw.fini_();
    }

    @Test
    public void shouldStoreBlock() throws Exception
    {
        TestBlock b = newBlock();

        cache.putBlock(b._key, new ByteArrayInputStream(b._content), b._content.length);

        verify(bsb).putBlock(eq(b._key), any(InputStream.class), eq((long)b._content.length));
    }

    @Test
    public void shouldThrowWhenTryingToFetchInvalidBlock() throws Exception
    {
        when(bsb.getBlock(any(ContentBlockHash.class))).thenThrow(new FileNotFoundException());

        boolean ok = false;
        try {
            cache.getBlock(contentHash(new byte[0]));
        } catch (FileNotFoundException e) {
            ok = true;
        }
        Assert.assertTrue(ok);
    }

    @Test
    public void shouldFetchUncachedBlock() throws Exception
    {
        byte[] content = new byte[] {0, 1, 2, 3};
        ContentBlockHash key = contentHash(content);

        when(bsb.getBlock(eq(key))).thenReturn(new ByteArrayInputStream(content));

        Assert.assertArrayEquals(content, ByteStreams.toByteArray(cache.getBlock(key)));

        verify(bsb, times(1)).getBlock(eq(key));
    }

    @Test
    public void shouldNotFetchCachedBlock() throws Exception
    {
        byte[] content = new byte[] {0, 1, 2, 3};
        ContentBlockHash key = contentHash(content);

        when(bsb.getBlock(eq(key))).thenReturn(new ByteArrayInputStream(content));

        Assert.assertArrayEquals(content, ByteStreams.toByteArray(cache.getBlock(key)));
        Assert.assertArrayEquals(content, ByteStreams.toByteArray(cache.getBlock(key)));
        Assert.assertArrayEquals(content, ByteStreams.toByteArray(cache.getBlock(key)));

        verify(bsb, times(1)).getBlock(eq(key));
    }

    @Test
    public void shouldEvictBlock() throws Exception
    {
        // TODO:
    }

    @Test
    public void shouldAllowConcurrentAccess() throws Exception
    {
        // TODO:
    }
}
