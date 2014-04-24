/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandType;
import org.junit.Test;

import static com.aerofs.sp.server.CommandUtil.createCommandFromMessage;
import static com.aerofs.sp.server.CommandUtil.createCommandMessage;
import static com.aerofs.sp.server.CommandUtil.createUploadLogsCommandMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Covers serialization / deserialization logic and ensures backward compatibility since we do not migrate the db.
 */
public class TestCommandUtil
{
    @Test
    public void shouldDeserializeFirstEditionCommands()
    {
        // this is a bit exhaustive but it's the only way to cover backward compatibility
        Object[][] testCases = {
                { "0", CommandType.INVALIDATE_DEVICE_NAME_CACHE },
                { "1", CommandType.INVALIDATE_USER_NAME_CACHE },
                { "2", CommandType.UNLINK_SELF },
                { "3", CommandType.UNLINK_AND_WIPE_SELF },
                { "4", CommandType.REFRESH_CRL },
                { "5", CommandType.OBSOLETE_WAS_CLEAN_SSS_DATABASE },
                { "6", CommandType.UPLOAD_DATABASE },
                { "7", CommandType.CHECK_UPDATE },
                { "8", CommandType.SEND_DEFECT },
                { "9", CommandType.LOG_THREADS }
        };

        for (Object[] testCase : testCases) {
            String message = (String) testCase[0];
            CommandType expected = (CommandType) testCase[1];
            Command command = createCommandFromMessage(message, 0);

            assertEquals(expected, command.getType());
        }
    }

    @Test
    public void shouldDeserializeSimpleSerializedCommandMessages()
    {
        CommandType[] types = {
                CommandType.INVALIDATE_DEVICE_NAME_CACHE,
                CommandType.INVALIDATE_USER_NAME_CACHE,
                CommandType.UNLINK_SELF,
                CommandType.UNLINK_AND_WIPE_SELF,
                CommandType.REFRESH_CRL,
                CommandType.OBSOLETE_WAS_CLEAN_SSS_DATABASE,
                CommandType.UPLOAD_DATABASE,
                CommandType.CHECK_UPDATE,
                CommandType.SEND_DEFECT,
                CommandType.LOG_THREADS
        };

        for (CommandType type : types) {
            String message = createCommandMessage(type);
            Command command = createCommandFromMessage(message, 0);

            assertEquals(type, command.getType());
        }
    }

    @Test
    public void shouldDeserializeUploadLogsCommandMessage()
    {
        String dryadID = "9001DEADBEEF";
        String customerID = "31415926585";
        String expectedMessage = "10:" + dryadID + ":" + customerID;

        String message = createUploadLogsCommandMessage(dryadID, customerID);
        assertEquals(expectedMessage, message);

        Command command = createCommandFromMessage(message, 0);
        assertEquals(CommandType.UPLOAD_LOGS, command.getType());
        assertTrue(command.hasUploadLogsArgs());
        assertEquals(dryadID, command.getUploadLogsArgs().getDryadId());
        assertEquals(customerID, command.getUploadLogsArgs().getCustomerId());
    }
}
