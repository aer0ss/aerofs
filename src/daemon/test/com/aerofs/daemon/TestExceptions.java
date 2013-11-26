/*
 * Copyright (c) Air Computing Inc., 2013.
 */

// this class is in the daemon package only because it needs to access
// DaemonProgram.registerExceptionTypes()
package com.aerofs.daemon;

import com.aerofs.base.ex.ExInternalError;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.lib.Util;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBException.Type;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static com.aerofs.proto.Common.PBException.Type.INTERNAL_ERROR;
import static com.aerofs.proto.Common.PBException.Type.INVALID_EMAIL_ADDRESS;
import static com.aerofs.proto.Common.PBException.Type.LAUNCH_ABORTED;
import static com.aerofs.proto.Common.PBException.Type.NOT_DIR;
import static com.aerofs.proto.Common.PBException.Type.NOT_FILE;
import static com.aerofs.proto.Common.PBException.Type.NO_ADMIN_OR_OWNER;
import static com.aerofs.proto.Common.PBException.Type.SHARING_RULES_ERROR;
import static com.aerofs.proto.Common.PBException.Type.SHARING_RULES_WARNINGS;
import static com.google.protobuf.ByteString.copyFrom;

/**
 * This is an integration test designed to cover 2 common mistakes:
 *   - adding a wirable exception for the client without registering it.
 *   - adding a wirable exception without implementing a required constructor accessed via
 *     reflection.
 */
public class TestExceptions
{
    @Before
    public void setup()
    {
        Util.registerLibExceptions();
        DaemonProgram.registerExceptionTypes();
    }

    // this test ensures all PBExceptions defined in common.proto are registered with the client
    // and have implemented the required constructor. Couple exceptions were excluded, each with
    // their own reason.
    @Test
    public void shouldHaveRegisteredEveryExceptionType() throws Exception
    {
        for (Type t : Type.values()) {
            // excludes the exceptions we don't need to cover
            if (isExcluded(t)) continue;

            PBException.Builder builder = PBException.newBuilder();
            builder.setType(t);

            // AbstractExObfuscatedWirable expects the plain text message field to be populated
            if (isAbstractExObfuscatedWirable(t)) builder.setPlainTextMessageDeprecated("");

            // AbstractExSharedFolderRules expects the data to be populated
            if (isAbstractExSharedFolderRules(t)) builder.setData(copyFrom("{}", "UTF-8"));

            Assert.assertTrue("Failed to unmarshal exception type: " + t.name(),
                    !(Exceptions.fromPB(builder.build()) instanceof ExInternalError));
        }
    }

    private boolean isExcluded(Type t)
    {
        return t == INTERNAL_ERROR
                || t == LAUNCH_ABORTED // thrown by Controller and isn't sent over the wire
                || t == NO_ADMIN_OR_OWNER // intended for the Python web client, not Java
                || t == INVALID_EMAIL_ADDRESS; // intended for the Python web client, not Java
    }

    private boolean isAbstractExObfuscatedWirable(Type t)
    {
        return t == NOT_DIR
                || t == NOT_FILE;
    }

    private boolean isAbstractExSharedFolderRules(Type t)
    {
        return t == SHARING_RULES_ERROR || t == SHARING_RULES_WARNINGS;
    }
}
