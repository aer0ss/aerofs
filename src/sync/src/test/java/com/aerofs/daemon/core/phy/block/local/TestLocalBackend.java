/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block.local;

import com.aerofs.daemon.core.phy.block.AbstractBlockTest;
import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.TokenWrapper;
import com.aerofs.lib.cfg.CfgAbsDefaultRoot;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.io.ByteStreams;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.FileNotFoundException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestLocalBackend extends AbstractBlockTest
{
    @Mock CfgAbsDefaultRoot absRootAnchor;

    LocalBackend bsb;

    @Before
    public void setUp() throws Exception
    {
        when(absRootAnchor.get()).thenReturn(testTempDirFactory.getTestTempDir().getAbsolutePath());

        bsb = new LocalBackend(absRootAnchor, new InjectableFile.Factory());
        bsb.init_();
    }

    @Test
    public void shouldThrowWhenTryingToFetchInvalidBlock() throws Exception
    {
        TestBlock b = newBlock();

        try {
            bsb.getBlock(b._key);
            Assert.assertTrue(false);
        } catch (FileNotFoundException e) {}
    }

    @Test
    public void shouldStoreAndFetchBlock() throws Exception
    {
        TestBlock b = newBlock();
        put(bsb, b);
        Assert.assertArrayEquals(b._content, ByteStreams.toByteArray(bsb.getBlock(b._key)));
    }

    @Test
    public void shouldDeleteExistingBlock() throws Exception
    {
        TokenWrapper tk = mock(TokenWrapper.class);
        TestBlock b = newBlock();
        put(bsb, b);

        bsb.deleteBlock(b._key, tk);

        try {
            bsb.getBlock(b._key);
            Assert.assertTrue(false);
        } catch (FileNotFoundException e) {}
    }
}
