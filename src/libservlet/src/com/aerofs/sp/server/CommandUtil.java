/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.Command.Builder;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.proto.Cmd.UploadLogsArgs;

/**
 * This class currently hosts the command serialization / deserialization logic. This may not be
 * the best way to do this, but it's done this way for now. We can refactor this into better
 * classes when we have more methods.
 */
public class CommandUtil
{
    private CommandUtil() {
        // prevent instantiation
    }

    public static String createCommandMessage(CommandType type)
    {
        return String.valueOf(type.getNumber());
    }

    // pre: both dryadID and customerID are sanitized
    public static String createUploadLogsCommandMessage(String dryadID, String customerID)
    {
        return createCommandMessage(CommandType.UPLOAD_LOGS) + ":" + dryadID + ":" + customerID;
    }

    public static Command createCommandFromMessage(String commandMessage, long epoch)
    {
        String[] fields = commandMessage.split(":", 2);

        CommandType type = CommandType.valueOf(Integer.valueOf(fields[0]));
        // prep for future commits
        String args = fields.length > 1 ? fields [1] : null;

        Command.Builder builder = Command.newBuilder()
                .setType(type)
                .setEpoch(epoch);

        switch (type) {
        // parse args for individual types here if any args are expected
        case UPLOAD_LOGS:   return parseUploadLogsArgs(builder, args);
        default:            return builder.build();
        }
    }

    // pre: args is the output of createUploadLogsCommandMessage()
    private static Command parseUploadLogsArgs(Builder builder, String args)
    {
        String[] fields = args.split(":");

        return builder.setUploadLogsArgs(UploadLogsArgs.newBuilder()
                .setDryadId(fields[0])
                .setCustomerId(fields[1]))
                .build();
    }
}
