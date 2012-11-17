/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.aerofs.testlib.AbstractTest;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class TestExceptions extends AbstractTest
{
    @Test
    public void shouldCreateObfuscatedExceptionWithObfuscatedMessage() throws Exception
    {
        ExNotDir ex = new ExNotDir("testing {}", new File("/test/file"));
        assertNotSame(ex.getMessage(), ex.getPlainTextMessage());
    }

    @Test
    public void shouldCreateObfuscatedPBException() throws Exception
    {
        ExNotDir ex = new ExNotDir("testing {}", new File("/test/file"));
        PBException pb = Exceptions.toPB(ex);
        assertNotSame(pb.getMessage(), pb.getPlainTextMessage());
    }

    @Test
    public void shouldCreateObfuscatedExceptionFromPB() throws Exception
    {
        PBException pb = PBException.newBuilder()
                .setType(Type.NOT_DIR)
                .setMessage("OBFUSCATED MESSAGE")
                .setPlainTextMessage("Secret code")
                .build();
        Exception e = Exceptions.fromPB(pb);
        assertTrue(e instanceof ExNotDir);

        ExNotDir ex = (ExNotDir) e;
        assertEquals("OBFUSCATED MESSAGE", ex.getMessage());
        assertEquals("Secret code", ex.getPlainTextMessage());
    }
}
