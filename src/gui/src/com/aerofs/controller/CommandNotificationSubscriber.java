/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import java.sql.SQLException;
import java.util.Iterator;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.C;
import com.aerofs.lib.Util;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Cmd.CommandCheckUpdate;
import com.aerofs.proto.Cmd.CommandUploadDatabase;
import com.aerofs.proto.Cmd.Commands;
import com.aerofs.ui.UI;
import com.aerofs.verkehr.client.lib.subscriber.ClientFactory;
import com.aerofs.verkehr.client.lib.subscriber.ISubscriberEventListener;
import com.aerofs.verkehr.client.lib.subscriber.VerkehrSubscriber;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.annotation.Nullable;

import static com.aerofs.lib.Param.Verkehr.VERKEHR_ACK_TIMEOUT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_HOST;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_PORT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_RETRY_INTERVAL;
import static java.util.concurrent.Executors.newCachedThreadPool;

public final class CommandNotificationSubscriber
{
    private static final Logger l = Util.l(CommandNotificationSubscriber.class);
    private final VerkehrSubscriber _sub;

    public CommandNotificationSubscriber(String user, String caCertFilename)
    {
        String topic = C.CMD_CHANNEL_TOPIC_PREFIX + user;
        l.info("creating command notification subscriber for t:" + topic);

        ClientFactory factory = new ClientFactory(VERKEHR_HOST, VERKEHR_PORT,
                newCachedThreadPool(), newCachedThreadPool(), caCertFilename,
                new CfgKeyManagersProvider(), VERKEHR_RETRY_INTERVAL, VERKEHR_ACK_TIMEOUT,
                new HashedWheelTimer(), topic, new SubscriberEventListener());

        _sub = factory.create();
    }

    public void start()
    {
        l.info("started cmd notification subscriber");
        _sub.start();
    }

    private static final class SubscriberEventListener implements ISubscriberEventListener
    {
        private static final Logger l = Util.l(SubscriberEventListener.class);

        @Override
        public void onSubscribed()
        {
            l.info("cmd: subscribed");
        }

        @Override
        public void onNotificationReceived(String topic, @Nullable final byte[] payload)
        {
            l.info("cmd notification received");

            if (payload == null) {
                l.error("cmd has empty payload");
                return;
            }

            Commands cmds;
            try {
                cmds = Commands.parseFrom(payload);
            }
            catch (InvalidProtocolBufferException e) {
                l.error("invalid cmds received (protobuf error): " + e.toString());
                return;
            }

            Iterator<Command> it = cmds.getCommandsList().iterator();

            // Only perform the command if its command ID is greater than the local command ID
            // stored in the db. After all operations have been completed, store the new max
            // command ID for next time.

            long localMaxCmdId = Long.parseLong(Cfg.db().get(Key.CMD_CHANNEL_ID));
            long newMaxCmdId = localMaxCmdId;

            while (it.hasNext()) {
                Command cmd = it.next();

                long cmdId = cmd.getCommandId();
                newMaxCmdId = newMaxCmdId > cmdId ? newMaxCmdId : cmdId;

                // Skip the command if we have already executed it previously.
                if (cmdId <= localMaxCmdId) {
                    continue;
                }

                // Execute the command.
                try {
                    switch (cmd.getType()) {
                    case UPLOAD_DATABASE:
                        CommandUploadDatabase upload = CommandUploadDatabase.parseFrom(cmd.getPayload());
                        handleUploadDatabase(upload);
                        break;
                    case CHECK_UPDATE:
                        CommandCheckUpdate check = CommandCheckUpdate.parseFrom(cmd.getPayload());
                        handleCheckUpdate(check);
                        break;
                    default:
                        l.error("unkown cmd type: " + cmd.getType());
                        break;
                    }
                }
                catch (InvalidProtocolBufferException e) {
                    l.error("invalid cmd received (protobuf error): " + e.toString());
                }
            }

            // Save the max command ID.
            try {
                Cfg.db().set(Key.CMD_CHANNEL_ID, newMaxCmdId);
            }
            catch (SQLException e) {
                l.error("cannot set cmd channel id in db" + e.toString());
            }
        }

        private void handleUploadDatabase(CommandUploadDatabase upload)
        {
            l.warn("cmd: upload database");
            SVClient.sendCoreDatabaseAsync();
        }

        private void handleCheckUpdate(CommandCheckUpdate check)
        {
            l.warn("cmd: check for updates");
            UI.updater().checkForUpdate(true);
        }
    }
}
