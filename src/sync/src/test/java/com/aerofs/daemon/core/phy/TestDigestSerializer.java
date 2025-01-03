package com.aerofs.daemon.core.phy;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

public class TestDigestSerializer extends AbstractTest
{
    private void assertDigestPreserved(MessageDigest md0, byte[] next)
    {
        byte[] s = DigestSerializer.serialize(md0);
        MessageDigest md1 = DigestSerializer.deserialize(s);

        md0.update(next);
        md1.update(next);

        try {
            MessageDigest md = (MessageDigest)md0.clone();
            assertArrayEquals(md1.digest(), md.digest());
        } catch (CloneNotSupportedException e) {
            fail();
        }
    }

    @Test
    public void shouldPreserveIncrementalDigest()
    {
        MessageDigest md0 = BaseSecUtil.newMessageDigest();
        assertDigestPreserved(md0, new byte[0]);
        assertDigestPreserved(md0, "The quick brown fox".getBytes(StandardCharsets.UTF_8));
        assertDigestPreserved(md0, "jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));

        byte buf[] = new byte[2048];
        assertDigestPreserved(md0, buf);

        for (int i = 0; i < buf.length; ++i) {
            buf[i] += i;
        }

        assertDigestPreserved(md0, buf);
    }

    @Test
    public void shouldRejectFinalizedDigest()
    {
        MessageDigest md = BaseSecUtil.newMessageDigest();
        try {
            md.digest();
            DigestSerializer.serialize(md);
            fail();
        } catch (IllegalArgumentException e) {}
    }

    @Test
    public void shouldRejectInvalidSerialization()
    {
        byte[] b = new byte[DigestSerializer.SERIALIZED_SIZE];
        Arrays.fill(b, (byte)0xff);
        try {
            DigestSerializer.deserialize(b);
            fail();
        } catch (IllegalArgumentException e) {}
    }
}
