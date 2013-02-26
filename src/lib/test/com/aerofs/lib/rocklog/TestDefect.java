/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.rocklog;

import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.cfg.InjectableCfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.rocklog.TestDefect.JsonDefect.Priority;
import com.aerofs.testlib.AbstractTest;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.when;

public class TestDefect extends AbstractTest
{
    @Mock RockLog _rocklog;
    @Mock InjectableCfg _cfg;

    /**
     * Test that the JSON output of a defect follows what the RockLog server expects.
     *
     * Important: If this test fail, either:
     *     fix Defect.java
     *  OR fix this test AND fix rocklog.py
     *
     *  Because if this test fails, it is likely that the defect will not be processed by the server
     *  either.
     */
    @Test
    public void shouldMakeWellFormedDefects() throws ExFormatError
    {
        final Exception cause = new IOException("this is the cause");
        final Exception wrapper = new Exception(cause);

        final DID did = new DID("12300000000000000000000000000456");
        final UserID user = UserID.fromInternal("user@test.com");
        final String version = "0.0.1";

        when(_cfg.ver()).thenReturn(version);
        when(_cfg.inited()).thenReturn(true);
        when(_cfg.did()).thenReturn(did);
        when(_cfg.user()).thenReturn(user);

        // Create a new defect
        Defect defect = new Defect(_rocklog, _cfg, "test-defect").setException(wrapper).setMessage(
                "hello");

        // Serialize and de-serialize the defect
        String json = defect.getJSON();
        //System.out.println("JSON: " + json);
        JsonDefect result = new Gson().fromJson(json, JsonDefect.class);

        // Check the basic properties of the defect
        // TODO (GS): Check timestamp
        assertEquals("test-defect", result.name);
        assertEquals("hello", result.message);
        assertEquals(Priority.Fatal, result.priority); // defects should have Fatal priority by default
        assertEquals(version, result.version);
        assertEquals(did.toStringFormal(), result.did);
        assertEquals(user.getString(), result.user_id);
        assertEquals(OSUtil.get().getFullOSName(), result.os_name);
        assertEquals(OSUtil.get().getOSFamily().toString(), result.os_family);
        assertEquals(OSUtil.getOSArch().toString(), result.aerofs_arch);

        // Check that the exception is properly encoded
        assertNotNull(result.exception);
        assertEquals(wrapper.getClass().getName(), result.exception.type);
        assertEquals(wrapper.getStackTrace().length, result.exception.stacktrace.length);
        assertEquals(wrapper.getStackTrace()[0].getClassName(), result.exception.stacktrace[0].klass);
        assertEquals(wrapper.getStackTrace()[0].getFileName(), result.exception.stacktrace[0].file);
        assertEquals(wrapper.getStackTrace()[0].getMethodName(), result.exception.stacktrace[0].method);
        assertEquals(wrapper.getStackTrace()[0].getLineNumber(), result.exception.stacktrace[0].line);

        // Check that the cause is properly encoded
        assertNotNull(result.exception.cause);
        assertEquals(cause.getClass().getName(), result.exception.cause.type);
        assertEquals(cause.getMessage(), result.exception.cause.message);
        assertNull(result.exception.cause.cause);
    }

    /**
     * Gson will convert a JSON Defect back into an instance of this class
     */
    public static class JsonDefect
    {
        public String name;
        @SerializedName("@message") public String message;
        @SerializedName("@timestamp") public String timestamp;
        public enum Priority { Info, Warning, Fatal }
        public Priority priority;
        public String version;
        public String os_name;
        public String os_family;
        public String aerofs_arch;
        public String user_id;
        public String did;
        public DefectException exception;

        public static class DefectException
        {
            public String type;
            public String message;
            public StackFrame[] stacktrace;
            public DefectException cause;

            public static class StackFrame
            {
                @SerializedName("class") public String klass;
                public String method;
                public String file;
                public int line;
            }
        }
    }
}
