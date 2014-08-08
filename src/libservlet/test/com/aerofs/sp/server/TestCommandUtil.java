/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.proto.Cmd.UploadLogsDestination;
import org.junit.Test;

import static com.aerofs.proto.Cmd.CommandType.*;
import static com.aerofs.sp.server.CommandUtil.createCommandFromMessage;
import static com.aerofs.sp.server.CommandUtil.createCommandMessage;
import static com.aerofs.sp.server.CommandUtil.createUploadLogsToAeroFSCommandMessage;
import static com.aerofs.sp.server.CommandUtil.createUploadLogsToOnSiteCommandMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Covers serialization / deserialization logic and ensures backward compatibility since we do not migrate the db.
 */
public class TestCommandUtil
{
    @Test
    public void shouldDeserializeFirstVersionCommands() throws Exception
    {
        // in the first version, the commands are of the format "0" where 0 is the ordinal
        // of the command type.
        //
        // this is a bit exhaustive but it's the only way to cover backward compatibility
        Object[][] testCases = {
                { "0", INVALIDATE_DEVICE_NAME_CACHE },
                { "1", INVALIDATE_USER_NAME_CACHE },
                { "2", UNLINK_SELF },
                { "3", UNLINK_AND_WIPE_SELF },
                { "4", REFRESH_CRL },
                { "5", OBSOLETE_WAS_CLEAN_SSS_DATABASE },
                { "6", UPLOAD_DATABASE },
                { "7", CHECK_UPDATE },
                { "8", SEND_DEFECT },
                { "9", LOG_THREADS }
        };

        for (Object[] testCase : testCases) {
            String message = (String) testCase[0];
            CommandType expected = (CommandType) testCase[1];
            Command command = createCommandFromMessage(message, 0);

            assertEquals(expected, command.getType());
        }
    }

    @Test
    public void shouldThrowOnCreateCommandMessagesFromInvalidTypes() throws Exception
    {
        try {
            createCommandMessage(UPLOAD_LOGS);

            // unexpected
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Covers the latest serialization / deserialization formats
     */
    @Test
    public void shouldDeserializeSimpleSerializedCommandMessages() throws Exception
    {
        CommandType[] types = {
                INVALIDATE_DEVICE_NAME_CACHE,
                INVALIDATE_USER_NAME_CACHE,
                UNLINK_SELF,
                UNLINK_AND_WIPE_SELF,
                REFRESH_CRL,
                OBSOLETE_WAS_CLEAN_SSS_DATABASE,
                UPLOAD_DATABASE,
                CHECK_UPDATE,
                SEND_DEFECT,
                LOG_THREADS
        };

        for (CommandType type : types) {
            String message = createCommandMessage(type);
            Command command = createCommandFromMessage(message, 0);

            assertEquals(type, command.getType());
        }
    }

    @Test
    public void shouldDeserializeUploadLogsToOnsiteCommandMessage() throws Exception
    {
        String defectID = "9001deadbeef0000deadbeef12345678";
        String host = "logs.example.com";
        int port = 443;
        String cert = "-----BEGIN CERTIFICATE-----\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "000=\n" +
                "-----END CERTIFICATE-----\n";
        long expiry = 0L;
        String expectedMessage = "10:9001deadbeef0000deadbeef12345678:0:" +
                "logs.example.com:443:" +
                "-----BEGIN CERTIFICATE-----\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "000=\n" +
                "-----END CERTIFICATE-----\n";

        String message = createUploadLogsToOnSiteCommandMessage(defectID, expiry, host, port, cert);
        assertEquals(expectedMessage, message);

        Command command = createCommandFromMessage(message, 0);
        assertEquals(UPLOAD_LOGS, command.getType());
        assertTrue(command.hasUploadLogsArgs());
        assertEquals(defectID, command.getUploadLogsArgs().getDefectId());
        assertEquals(expiry, command.getUploadLogsArgs().getExpiryTime());

        assertTrue(command.getUploadLogsArgs().hasDestination());
        UploadLogsDestination destination = command.getUploadLogsArgs().getDestination();
        assertEquals(host, destination.getHostname());
        assertEquals(port, destination.getPort());
        assertEquals(cert, destination.getCert());
    }

    @Test
    public void shouldThrowOnCreateUploadLogsCommandMessgeWithInvalidPort() throws Exception
    {
        String defectID = "9001deadbeef0000deadbeef12345678";
        long expiry = 0L;
        String host = "logs.example.com";
        String cert = "-----BEGIN CERTIFICATE-----\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "0000000000000000000000000000000000000000000000000000000000000000\n" +
                "000=\n" +
                "-----END CERTIFICATE-----\n";

        int[] invalidPorts = { -65535, -5, -1, 65536, 90000 };

        for (int invalidPort : invalidPorts) {
            try {
                createUploadLogsToOnSiteCommandMessage(defectID, expiry, host, invalidPort, cert);
                // unexpected
                fail();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @Test
    public void shouldDeserializeUploadLogsToAeroFSCommandMessage() throws Exception
    {
        String defectID = "9001deadbeef0000deadbeef12345678";
        long expiry = 0L;
        String expectedMessage = "10:9001deadbeef0000deadbeef12345678:0";

        String message = createUploadLogsToAeroFSCommandMessage(defectID, expiry);
        assertEquals(expectedMessage, message);

        Command command = createCommandFromMessage(message, 0);
        assertEquals(UPLOAD_LOGS, command.getType());
        assertTrue(command.hasUploadLogsArgs());
        assertEquals(defectID, command.getUploadLogsArgs().getDefectId());
        assertEquals(expiry, command.getUploadLogsArgs().getExpiryTime());
        assertFalse(command.getUploadLogsArgs().hasDestination());
    }

    @Test
    public void shouldThrowOnDeserializeInvalidUploadLogsCommandMessage() throws Exception
    {
        String[] invalidMessagess = {
                "10",
                "10:",
                "10:9001deadbeef0000deadbeef12345678:INVALID_EXPIRY",
                "10:9001deadbeef0000deadbeef12345678:0:WRONG_NUMBER_OF_ARGS",
                "10:9001deadbeef0000deadbeef12345678:0:" +
                        "logs.example.com:INVALID_PORT:" +
                        "-----BEGIN CERTIFICATE-----\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "000=\n" +
                        "-----END CERTIFICATE-----\n",
                "10:9001deadbeef0000deadbeef12345678:0:" +
                        "logs.example.com:-5:" +
                        "-----BEGIN CERTIFICATE-----\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "000=\n" +
                        "-----END CERTIFICATE-----\n",
                "10:9001deadbeef0000deadbeef12345678:0:" +
                        "logs.example.com:65536:" +
                        "-----BEGIN CERTIFICATE-----\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "000=\n" +
                        "-----END CERTIFICATE-----\n",
                "10:9001deadbeef0000deadbeef12345678:0:" +
                        "logs.example.com:443:" +
                        "-----BEGIN CERTIFICATE-----\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "0000000000000000000000000000000000000000000000000000000000000000\n" +
                        "000=\n" +
                        "-----END CERTIFICATE-----\n" +
                        ":TOO_MANY"
        };

        for (String invalidMessage : invalidMessagess) {
            try {
                CommandUtil.createCommandFromMessage(invalidMessage, 0);
                // unexpected
                fail();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }
}
