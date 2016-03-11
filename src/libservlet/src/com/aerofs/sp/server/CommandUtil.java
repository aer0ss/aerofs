/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.sp.server;

import com.aerofs.base.Loggers;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.Command.Builder;
import com.aerofs.proto.Cmd.CommandType;
import com.aerofs.proto.Cmd.UploadLogsArgs;
import com.aerofs.proto.Cmd.UploadLogsDestination;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import java.util.Set;

import static com.aerofs.proto.Cmd.CommandType.CHECK_UPDATE;
import static com.aerofs.proto.Cmd.CommandType.INVALIDATE_DEVICE_NAME_CACHE;
import static com.aerofs.proto.Cmd.CommandType.INVALIDATE_USER_NAME_CACHE;
import static com.aerofs.proto.Cmd.CommandType.LOG_THREADS;
import static com.aerofs.proto.Cmd.CommandType.OBSOLETE_WAS_CLEAN_SSS_DATABASE;
import static com.aerofs.proto.Cmd.CommandType.REFRESH_CRL;
import static com.aerofs.proto.Cmd.CommandType.SEND_DEFECT;
import static com.aerofs.proto.Cmd.CommandType.UNLINK_AND_WIPE_SELF;
import static com.aerofs.proto.Cmd.CommandType.UNLINK_SELF;
import static com.aerofs.proto.Cmd.CommandType.UPLOAD_DATABASE;
import static com.aerofs.proto.Cmd.CommandType.UPLOAD_LOGS;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import static org.apache.commons.lang.StringUtils.join;

/**
 * The intention of this class is to capture _all_ command serialization / deserialization logic.
 */
public class CommandUtil
{
    private static final Logger l = Loggers.getLogger(CommandUtil.class);

    private CommandUtil() {
        // prevent instantiation
    }

    public static String createCommandMessage(CommandType type) throws IllegalArgumentException
    {
        Set<CommandType> validTypes = Sets.immutableEnumSet(
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
        );

        checkArgument(validTypes.contains(type), "Invalid command type: " + type.name());

        return createMessageHeader(type);
    }

    private static String createMessageHeader(CommandType type)
    {
        return String.valueOf(type.getNumber());
    }

    public static String createUploadLogsToAeroFSCommandMessage(String defectID, long expiryTime)
    {
        String[] fields = {
                createMessageHeader(UPLOAD_LOGS),
                defectID,
                String.valueOf(expiryTime)
        };

        return join(fields, ':');
    }

    public static String createUploadLogsToOnSiteCommandMessage(String defectID, long expiryTime,
            String host, int port)
            throws IllegalArgumentException
    {
        throwIfNotValidPort(port);

        String[] fields = {
                createMessageHeader(CommandType.UPLOAD_LOGS),
                defectID,
                String.valueOf(expiryTime),
                host,
                String.valueOf(port)
        };

        return join(fields, ':');
    }

    public static Command createCommandFromMessage(String commandMessage, long epoch)
            throws IllegalArgumentException
    {
        try {
            String[] fields = commandMessage.split(":", 2);

            CommandType type = CommandType.valueOf(Integer.valueOf(fields[0]));
            String args = fields.length > 1 ? fields [1] : null;

            Command.Builder builder = Command.newBuilder()
                    .setType(type)
                    .setEpoch(epoch);

            switch (type) {
            // parse args for individual types here if any args are expected
            case UPLOAD_LOGS:
                checkNotNull(args);
                return parseUploadLogsArgs(builder, args);
            default:
                return builder.build();
            }
        } catch (Exception e) {
            // our current impl. doesn't handle invalid commands.
            //
            // we should never enqueue invalid commands, but we did so one time by accident
            // using the command server.
            l.warn("Command format error: {}", commandMessage);
            throw new IllegalArgumentException(e);
        }
    }

    private static Command parseUploadLogsArgs(Builder builder, String args)
            throws IllegalArgumentException
    {
        String [] fields = args.split(":");

        checkArgument(isNotEmpty(fields[0]));

        switch (fields.length) {
        case 2:
            // upload to AeroFS
            return builder.setUploadLogsArgs(UploadLogsArgs.newBuilder()
                    .setDefectId(fields[0])
                    .setExpiryTime(Long.valueOf(fields[1])))
                    .build();
        case 4:
            int port = Integer.parseInt(fields[3]);
            throwIfNotValidPort(port);

            // upload to on-site
            return builder.setUploadLogsArgs(UploadLogsArgs.newBuilder()
                    .setDefectId(fields[0])
                    .setExpiryTime(Long.valueOf(fields[1]))
                    .setDestination(UploadLogsDestination.newBuilder()
                            .setHostname(fields[2])
                            .setPort(port)
                            .setCert("")))
                    .build();
        default:
            throw new IllegalArgumentException("Expecting either 2 or 5 fields, " +
                    "found " + fields.length);
        }
    }

    private static void throwIfNotValidPort(int port)
            throws IllegalArgumentException
    {
        checkArgument(Range.closed(0, 65535).contains(port), "Invalid port: " + port);
    }
}
