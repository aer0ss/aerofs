/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.ContentBlockHash;
import com.aerofs.ids.UniqueID;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.UnitTestTempDir;
import org.junit.Rule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.mockito.Matchers.eq;

/**
 * Base class for BlockStorage-related tests
 */
public class AbstractBlockTest extends AbstractTest
{
    // setup temporary folder for use as aux root
    @Rule
    public final UnitTestTempDir testTempDirFactory = new UnitTestTempDir();

    protected static ContentBlockHash contentHash(byte[] content)
    {
        HashStream hs = HashStream.newFileHasher();
        hs.update(content, 0, content.length);
        hs.close();
        return hs.getHashAttrib();
    }

    protected static class TestBlock
    {
        public final ContentBlockHash _key;
        public final byte[] _content;

        public TestBlock(byte[] content)
        {
            _content = content;
            _key = contentHash(content);
        }
    }

    protected static TestBlock newEmptyBlock()
    {
        return new TestBlock(new byte[0]);
    }

    protected static TestBlock newBlock()
    {
        return new TestBlock(UniqueID.generate().getBytes());
    }

    protected void put(IBlockStorageBackend bsb, TestBlock block) throws IOException
    {
        try (InputStream in = new ByteArrayInputStream(block._content)) {
            bsb.putBlock(block._key, in, block._content.length);
        }
    }

    protected static ContentBlockHash forKey(byte[] c) { return eq(contentHash(c)); }
}
