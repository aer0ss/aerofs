package com.aerofs.daemon.rest;

import com.aerofs.daemon.core.phy.IPhysicalPrefix;
import com.aerofs.daemon.core.phy.PrefixOutputStream;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.id.SOKID;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class TestFileResource_Upload extends AbstractRestTest
{
    public TestFileResource_Upload(boolean useProxy)
    {
        super(useProxy);
    }

    private class ZeroInputStream extends InputStream {
        private final long _l;
        private long _p = 0;

        ZeroInputStream(long length) {
            _l = length;
        }
        @Override
        public int read() throws IOException {
            return _p < _l ? (int)(++_p & 0L) : -1;
        }
    }

    private class ZeroPhysicalPrefix implements IPhysicalPrefix {
        long _l;

        @Override
        public long getLength_() {
            return _l;
        }

        @Override
        public byte[] hashState_() {
            return null;
        }

        @Override
        public PrefixOutputStream newOutputStream_(boolean append) throws IOException {
            return new PrefixOutputStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    Assert.assertEquals(0, b);
                    ++_l;
                }
                @Override
                public void write(byte b[], int off, int len) throws IOException {
                    for (int i = off; i < off + len; ++i) {
                        assertEquals(0, b[i]);
                    }
                    _l += len;
                }
            });
        }

        @Override
        public void moveTo_(IPhysicalPrefix pf, Trans t) throws IOException {}

        @Override
        public void delete_() throws IOException {}
    }

    @Ignore("run manually")
    @Test
    public void shouldUploadLargeContent() throws Exception
    {
        SOID soid = mds.root().file("foo.txt").soid();
        ZeroPhysicalPrefix pf = new ZeroPhysicalPrefix();
        when(ps.newPrefix_(any(SOKID.class), anyString())).thenReturn(pf);

        final long LENGTH = 4 * (long)Integer.MAX_VALUE;

        givenAccess()
                .header("Content-Type", "application/octet-stream")
                .content(new ZeroInputStream(LENGTH))
        .expect()
                .statusCode(204)
        .when()
                .put("/v0.10/files/" + id(soid) + "/content");

        assertEquals("Output Does Not match", LENGTH, pf._l);
    }
}
