/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.phy.block;

import com.aerofs.daemon.core.phy.block.IBlockStorageBackend.IBlockMetadata;
import com.aerofs.daemon.lib.HashStream;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.id.UniqueID;
import com.aerofs.testlib.AbstractTest;
import com.aerofs.testlib.UnitTestTempDir;
import org.junit.Rule;
import org.mockito.ArgumentMatcher;

import static org.mockito.Matchers.argThat;
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

    protected static class TestBlock implements IBlockMetadata
    {
        private final ContentHash _key;
        public final byte[] _content;
        private Object _data;

        TestBlock(byte[] content)
        {
            _content = content;
            _key = contentHash(content);
        }

        @Override
        public ContentHash getKey()
        {
            return _key;
        }

        @Override
        public long getDecodedLength()
        {
            return _content.length;
        }

        @Override
        public Object getEncoderData()
        {
            return _data;
        }

        @Override
        public void setEncoderData(Object d)
        {
            _data = d;
        }
    }

    protected static TestBlock newBlock()
    {
        return new TestBlock(UniqueID.generate().getBytes());
    }


    protected static class HasKey extends ArgumentMatcher<IBlockMetadata>
    {
        private final ContentHash _key;

        public HasKey(byte[] c) { _key = contentHash(c); }

        @Override
        public boolean matches(Object argument)
        {
            IBlockMetadata bm = (IBlockMetadata)argument;
            return bm.getKey().equals(_key);
        }
    }

    protected static IBlockMetadata hasKey(byte[] c) { return argThat(new HasKey(c)); }

    protected static ContentHash forKey(byte[] c) { return eq(contentHash(c)); }
}
