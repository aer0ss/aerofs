/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.UnitTestTempDir;
import org.junit.Rule;

import static org.mockito.Matchers.eq;

/**
 * Base class for BlockStorage-related tests
 */
public class AbstractBlockTest extends AbstractTest
{
    // setup temporary folder for use as aux root
    @Rule
    public final UnitTestTempDir testTempDirFactory = new UnitTestTempDir();

    protected static ContentHash contentHash(byte[] content)
    {
        HashStream hs = HashStream.newFileHasher();
        hs.update(content, 0, content.length);
        hs.close();
        return hs.getHashAttrib();
    }

    protected static class TestBlock
    {
        public final ContentHash _key;
        public final byte[] _content;

        TestBlock(byte[] content)
        {
            _content = content;
            _key = contentHash(content);
        }
    }

    protected static TestBlock newBlock()
    {
        return new TestBlock(UniqueID.generate().getBytes());
    }

    protected static ContentHash forKey(byte[] c) { return eq(contentHash(c)); }
}
