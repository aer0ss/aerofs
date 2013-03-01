/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.lib.Param;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.base.id.UserID;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Cmd.TransientCommands;
import com.aerofs.proto.Cmd.TransientCommand;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.UI;
import com.aerofs.verkehr.client.lib.IConnectionListener;
import com.aerofs.verkehr.client.lib.subscriber.ClientFactory;
import com.aerofs.verkehr.client.lib.subscriber.ISubscriptionListener;
import com.aerofs.verkehr.client.lib.subscriber.VerkehrSubscriber;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Iterator;

import static com.aerofs.lib.Param.Verkehr.VERKEHR_HOST;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_PORT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_RETRY_INTERVAL;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static java.util.concurrent.Executors.newCachedThreadPool;

public final class TransientCommandNotificationSubscriber
{
    private static final Logger l = Loggers.getLogger(TransientCommandNotificationSubscriber.class);

    private final String _topic;
    private final VerkehrSubscriber _subscriber;

    public TransientCommandNotificationSubscriber(UserID userId, String caCertFilename)
    {
        VerkehrListener listener = new VerkehrListener();

        l.warn("tcmd: " + VERKEHR_HOST + ":" + VERKEHR_PORT);
        ClientFactory factory = new ClientFactory(VERKEHR_HOST, VERKEHR_PORT,
                newCachedThreadPool(), newCachedThreadPool(),
                caCertFilename, new CfgKeyManagersProvider(),
                VERKEHR_RETRY_INTERVAL, Cfg.db().getLong(Key.TIMEOUT), new HashedWheelTimer(),
                listener, listener, sameThreadExecutor());

        this._topic = Param.CMD_CHANNEL_TOPIC_PREFIX + userId.getString();
        this._subscriber = factory.create();
    }

    public void start()
    {
        l.debug("tcmd: started notification subscriber");
        _subscriber.start();
    }

    private final class VerkehrListener implements IConnectionListener, ISubscriptionListener
    {
        @Override
        public void onConnected()
        {
            l.warn("tcmd: subscribe topic=" + _topic);
            _subscriber.subscribe_(_topic);
        }

        @Override
        public void onNotificationReceivedFromVerkehr(String topic, @Nullable final byte[] payload)
        {
            l.debug("tcmd: notification received");

            if (payload == null) {
                l.error("tcmd: empty payload");
                return;
            }

            TransientCommands cmds;
            try {
                cmds = TransientCommands.parseFrom(payload);
            }
            catch (InvalidProtocolBufferException e) {
                l.error("tcmd: invalid protobuf: " + e);
                return;
            }

            Iterator<TransientCommand> it = cmds.getCommandsList().iterator();

            // Only perform the command if its command ID is greater than the local command ID
            // stored in the db. After all operations have been completed, store the new max
            // command ID for next time.

            long localMaxCmdId = Long.parseLong(Cfg.db().get(Key.TRANSIENT_CMD_CHANNEL_ID));
            long newMaxCmdId = localMaxCmdId;

            while (it.hasNext()) {
                TransientCommand cmd = it.next();

                long cmdId = cmd.getCommandId();
                newMaxCmdId = newMaxCmdId > cmdId ? newMaxCmdId : cmdId;

                // Skip the command if we have already executed it previously.
                if (cmdId <= localMaxCmdId) {
                    l.debug("tcmd: skipping id " + cmdId + " (local max: " + localMaxCmdId + ")");
                    continue;
                }

                // Execute the command.
                l.info("tcmd: id=" + cmdId + " type=" + cmd.getType());
                try {
                    switch (cmd.getType()) {
                    case UPLOAD_DATABASE:
                        UI.ic().startUploadDatabase();
                        break;
                    case CHECK_UPDATE:
                        checkUpdate();
                        break;
                    case SEND_DEFECT:
                        sendDefect();
                        break;
                    case LOG_THREADS:
                        logThreads();
                        break;
                    default:
                        l.error("tcmd: unkown type: " + cmd.getType());
                        break;
                    }
                } catch (InvalidProtocolBufferException e) {
                    l.error("tcmd: invalid protobuf error: " + Util.e(e));
                } catch (Exception e) {
                    l.error("tcmd: execution error: " + Util.e(e));
                }
            }

            // Save the max command ID.
            try {
                Cfg.db().set(Key.TRANSIENT_CMD_CHANNEL_ID, newMaxCmdId);
            } catch (SQLException e) {
                l.error("tcmd: set local max: " + Util.e(e));
            }
        }

        /**
         * N.B. this method blocks for a few seconds. See commments below.
         */
        private void logThreads() throws Exception
        {
            l.debug("tcmd: log threads");

            // The delay is required by the command (see cmd.proto). It also blocks the subscriber
            // thread from processing more commands. Otherwise, multiple LOG_THREADS requests would
            // be processed at the same time, defeating the purpose of the delay. However, this
            // approach has an undesired side effect: processing of other command types are also
            // blocked. If it becomes a problem, we can work around by, e.g., having a dedicated
            // request queue for LOG_THREADS, or by changing the semantics of the command.
            ThreadUtil.sleepUninterruptable(5 * C.SEC);

            // Log threads for the current process
            Util.logAllThreadStackTraces();

            // Log threads for the daemon process
            RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
            try {
                ritual.logThreads();
            } finally {
                ritual.close();
            }
        }

        private void sendDefect()
        {
            l.debug("tcmd: send defect");
            SVClient.logSendDefectAsync(true, "tcmd call");
        }

        private void checkUpdate()
        {
            l.debug("tcmd: check for updates");
            UI.updater().checkForUpdate(true);
        }

        @Override
        public void onDisconnected()
        {
            // noop
        }
    }
}
