/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

import com.aerofs.MainUtil;
import com.aerofs.base.ex.ExInternalError;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import com.aerofs.testlib.AbstractTest;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestExceptions extends AbstractTest
{
    @Before
    public void setup()
    {
        MainUtil.registerLibExceptions();
    }

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
        assertNotSame(pb.getMessageDeprecated(), pb.getPlainTextMessageDeprecated());
    }

    @Test
    public void shouldCreateObfuscatedExceptionFromPB() throws Exception
    {
        PBException pb = PBException.newBuilder()
                .setType(Type.NOT_DIR)
                .setMessageDeprecated("OBFUSCATED MESSAGE")
                .setPlainTextMessageDeprecated("Secret code")
                .build();
        Exception e = Exceptions.fromPB(pb);
        assertTrue(e instanceof ExNotDir);

        ExNotDir ex = (ExNotDir) e;
        assertEquals("OBFUSCATED MESSAGE", ex.getMessage());
        assertEquals("Secret code", ex.getPlainTextMessage());
    }

    @Test
    public void shouldDeserializeExceptionsWithNonPublicConstructors()
    {
        // Precondition: check that ExInternalError's constructor is indeed non-public, otherwise
        // this test is meaningless
        try {
            ExInternalError.class.getConstructor(PBException.class);
            fail("ExInternalError constructor is public - this test is meaningless");
        } catch (NoSuchMethodException e) {
            // expected
        }

        Exception e1 = new Exception();
        Exception e2 = Exceptions.fromPB(Exceptions.toPB(e1));
        assertTrue(e2 instanceof ExInternalError);
    }

    @Test
    public void shouldComputeChecksumsProperly()
    {
        Exception ex1a, ex1b, ex2a, ex2b, ex3;

        // Should have different checksums if different stack traces
        ex1a = new Exception("ex 1a");
        ex1b = new Exception("ex 1b");
        assertFalse(Exceptions.getChecksum(ex1a).equals(Exceptions.getChecksum(ex1b)));

        // Should have the same checksum if same stack trace but different messages:
        // Important: Keep those two on the same line so that they have the exact same stack trace
        ex1a = new Exception("ex 1a"); ex1b = new Exception("ex 1b");
        assertEquals(Exceptions.getChecksum(ex1a), Exceptions.getChecksum(ex1b));

        // Same tests but with nested exceptions

        // Should have same checksums if same exceptions with same nested exceptions
        ex2a = new IOException("ex 2a", ex1a); ex2b = new IOException("ex 2b", ex1b);
        assertEquals(Exceptions.getChecksum(ex2a), Exceptions.getChecksum(ex2b));

        // Should have differemnt checksums if same exceptions but different nested exceptions
        ex3 = new Exception("ex3");
        ex2a = new IOException("ex 2a", ex1a); ex2b = new IOException("ex 2b", ex3);
        assertFalse(Exceptions.getChecksum(ex2a).equals(Exceptions.getChecksum(ex2b)));

        // Should have different checksums if same stack traces but different exception types
        ex1a = new Exception(); ex1b = new IOException();
        assertFalse(Exceptions.getChecksum(ex1a).equals(Exceptions.getChecksum(ex1b)));
    }
}
