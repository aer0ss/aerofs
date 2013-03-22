/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.lib.rocklog.EventType;
import com.aerofs.lib.rocklog.RockLog;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Sp.AckCommandQueueHeadReply;
import com.aerofs.proto.Sp.GetCommandQueueHeadReply;
import com.aerofs.proto.Sv.PBSVEvent.Type;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.UI;
import com.aerofs.verkehr.client.lib.IConnectionListener;
import com.aerofs.verkehr.client.lib.subscriber.ClientFactory;
import com.aerofs.verkehr.client.lib.subscriber.ISubscriptionListener;
import com.aerofs.verkehr.client.lib.subscriber.VerkehrSubscriber;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;

import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.concurrent.Callable;

import static com.aerofs.lib.Param.Verkehr.VERKEHR_HOST;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_PORT;
import static com.aerofs.lib.Param.Verkehr.VERKEHR_RETRY_INTERVAL;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * This is the verkehr command notification subscriber.
 *
 * Commands are sent via verkehr on the fast path or are pulled down via SP and this class is
 * responsible for scheduling the execution of those commands.
 *
 * An important property of commands: they are idempotent. This property is required because in the
 * current implementation it is possible (albeit rare) that a command can be executed twice. For
 * example, this can happen in the following situation:
 *
 *  1) Receive command # 1, fail to execute, ack with error.
 *  2) Receive command # 2, execute successfully, ack with success but the server ignores the ack
 *     because the epoch is too high.
 *  3) Full sync is triggered, command # 1 is executed and then command # 2 is executed a second
 *     time.
 *
 * If (in the future) we do not want commands to be idempotent, we can store the epoch number and
 * only execute commands if the epoch provided is higher than what we have stored locally. We will
 * also need to be smarter about how we schedule syncs, because we cannot execute a command if we've
 * missed its predecessor and have scheduled a sync, as in the above example.
 *
 * For now we assume commands are idempotent because it makes the implementation easier and in fact
 * all commands that we forsee in the future have this property anyway. Therefore this
 * implementation has been selected based on the KISS principle.
 */
public final class CommandNotificationSubscriber
{
    private static final Logger l = Loggers.getLogger(CommandNotificationSubscriber.class);

    private final IScheduler _scheduler;
    private final ExponentialRetry _er;
    private final String _topic;
    private final VerkehrSubscriber _subscriber;

    private final VerkehrListener _listener;
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    public CommandNotificationSubscriber(IScheduler scheduler, DID localDevice,
            String caCertFilename)
    {
        _scheduler = scheduler;
        _er = new ExponentialRetry(_scheduler);

        _listener = new VerkehrListener();

        l.debug("cmd: " + VERKEHR_HOST + ":" + VERKEHR_PORT);
        ClientFactory factory = new ClientFactory(VERKEHR_HOST, VERKEHR_PORT,
                newCachedThreadPool(), newCachedThreadPool(),
                caCertFilename, new CfgKeyManagersProvider(),
                VERKEHR_RETRY_INTERVAL, Cfg.db().getLong(Key.TIMEOUT), new HashedWheelTimer(),
                _listener, _listener, sameThreadExecutor());

        this._topic = Param.CMD_CHANNEL_TOPIC_PREFIX + localDevice.toStringFormal();
        this._subscriber = factory.create();
    }

    public void start()
    {
        l.debug("cmd: started notification subscriber");
        _subscriber.start();

        // Schedule a sync when the device first comes online.
        _listener.scheduleSyncWithCommandServer();
    }

    private final class VerkehrListener implements IConnectionListener, ISubscriptionListener
    {
        @Override
        public void onConnected()
        {
            l.debug("cmd: subscribe topic=" + _topic);
            _subscriber.subscribe_(_topic);

            // Also schedule a sync after we subscribe to the topic to ensure we are up to date
            // after potential verkehr outages.
            scheduleSyncWithCommandServer();
        }

        @Override
        public void onNotificationReceivedFromVerkehr(String topic, @Nullable final byte[] payload)
        {
            l.debug("cmd: notification received");

            if (payload == null) {
                l.error("cmd: empty payload");
                return;
            }

            Command command;
            try {
                command = Command.parseFrom(payload);
            }
            catch (InvalidProtocolBufferException e) {
                l.error("cmd: invalid protobuf: " + e.toString());
                return;
            }

            // Only perform the command if its command ID is greater than the local command ID
            // stored in the db. After all operations have been completed, store the new max
            // command ID for next time and send an ack.

            l.debug("cmd notification: epoch=" + command.getEpoch() + " type=" + command.getType());
            scheduleSingleCommandExecution(command);
        }

        @Override
        public void onDisconnected()
        {
            // noop
        }

        private void scheduleSyncWithCommandServer()
        {
            l.debug("cmd: sync scheduled");

            // Schedule the exponential retry execution so that we do not block the verkehr IO
            // thread.
            _scheduler.schedule(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    _er.retry("cmd-sync", new Callable<Void>()
                    {
                        @Override
                        public Void call()
                                throws Exception
                        {
                            syncWithCommandServer();
                            return null;
                        }
                    },
                    ExNoPerm.class, ExBadArgs.class, IOException.class);
                }
           }, 0);
        }

        private void syncWithCommandServer()
                throws Exception
        {
            l.debug("cmd: sync attempt");
            int errorCount = 0;

            // Get the command at the head of the queue and init loop variables.
            SPBlockingClient spUnauthenticated = newUnauthenticatedSPClient();
            GetCommandQueueHeadReply head = spUnauthenticated.getCommandQueueHead(Cfg.did().toPB());

            long initialQueueSize = head.getQueueSize();
            boolean more = head.hasCommand();

            if (!more) {
                l.debug("cmd: already up to date");
                return;
            }

            Command command = head.getCommand();

            // Need to handle the case where we encounter an error and the queue entry is marked as
            // "retry later". In this case it will be moved to the tail of the queue and the epoch
            // will be bumped. We must exponential retry in this case. However, if we encounter an
            // error AND a new queue entry is added while we are executing this set of commands, we
            // want to execute that command right away, so in that case we need to schedule a sync
            // again, right away. Keep this variable around so we can check for that case.
            long currentQueueSize = 0;

            for (int i = 0; more && i < initialQueueSize; i++) {
                boolean error = false;

                try {
                    processCommand(command);
                } catch (Exception e) {
                    error = true;
                    errorCount++;
                    l.error("cmd: unable to process in sync: " + Util.e(e));
                }

                SPBlockingClient spAuthenticated = newAuthenticatedSPClient();
                AckCommandQueueHeadReply ack = spAuthenticated.ackCommandQueueHead(
                        Cfg.did().toPB(), command.getEpoch(), error);

                more = ack.hasCommand();
                if (more) {
                    command = ack.getCommand();
                }

                currentQueueSize = ack.getQueueSize();
            }

            // In the first case a new command has been enqueued AND we have encountered an error,
            // so schedule a new sync. In the second case we have just encountered an error, so
            // exponential retry.
            if (more && errorCount < currentQueueSize) {
                scheduleSyncWithCommandServer();
            } else if (errorCount > 0) {
                throw new CommandFailed();
            }

            // Everything went perfectly, sync complete.
            l.debug("cmd: sync complete");
        }

        private void scheduleSingleCommandExecution(final Command command)
        {
            _scheduler.schedule(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    boolean error = false;
                    try {
                        processCommand(command);
                    } catch (Exception e) {
                        error = true;

                        // Use toString()'s in this function for cleaner logs. Only show full stack
                        // traces when the exponential retry class is in full swing.
                        l.error("cmd: unable to process single: " + e.toString());
                    }

                    AckCommandQueueHeadReply ack = null;
                    try {
                        SPBlockingClient sp = newAuthenticatedSPClient();
                        ack = sp.ackCommandQueueHead(Cfg.did().toPB(), command.getEpoch(), error);
                    } catch (Exception e) {
                        error = true;
                        l.error("cmd: unable to ack: " + e.toString());
                    }

                    if (error || ack.hasCommand()) {
                        scheduleSyncWithCommandServer();
                    }
                }
            }, 0);
        }

        private void processCommand(Command command)
                throws Exception
        {
            l.info("cmd: process " + command.getType());
            switch (command.getType()) {
                case INVALIDATE_DEVICE_NAME_CACHE:
                    invalidateDeviceNameCache();
                    break;
                case INVALIDATE_USER_NAME_CACHE:
                    invalidateUserNameCache();
                    break;
                case UNLINK_SELF:
                    unlinkSelf();
                    break;
                case UNLINK_AND_WIPE_SELF:
                    unlinkAndWipeSelf();
                    break;
                case REFRESH_CRL:
                    // TODO (MP) finish this - for now ignore.
                    break;
                case CLEAN_SSS_DATABASE:
                    // TODO (MP) finish this - for now ignore.
                    break;
                default:
                    throw new Exception("cmd type unknown");
            }
       }
    }

    private static SPBlockingClient newAuthenticatedSPClient()
            throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.signInRemote();
        return sp;
    }

    private static SPBlockingClient newUnauthenticatedSPClient()
            throws Exception
    {
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.signInRemote();
        return sp;
    }

    //
    // Command implementations.
    //

    private void invalidateUserNameCache()
            throws Exception
    {
        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();

        try {
            ritual.invalidateUserNameCache();
        } finally {
            ritual.close();
        }
    }

    private void invalidateDeviceNameCache()
            throws Exception
    {
        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();

        try {
            ritual.invalidateDeviceNameCache();
        } finally {
            ritual.close();
        }
    }

    private void unlinkSelf()
            throws Exception
    {
        // Metrics.
        SVClient.sendEventAsync(Type.UNLINK);
        RockLog.newEvent(EventType.UNLINK_DEVICE).sendAsync();

        unlinkImplementation();

        shutdownImplementation();
    }

    private void unlinkAndWipeSelf()
            throws Exception
    {
        // Metrics.
        SVClient.sendEventAsync(Type.UNLINK_AND_WIPE);
        RockLog.newEvent(EventType.UNLINK_AND_WIPE).sendAsync();

        unlinkImplementation();

        // Delete Root Anchor.
        // TODO (MP) possibly implement secure delete.
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRootAnchor()));
        // Also delete the entire config directory, if possible.
        // On Windows, files we hold open may refuse to be deleted.
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot()));

        shutdownImplementation();
    }

    //
    // Implementation helpers.
    //

    private void shutdownImplementation()
    {
        UI.get().shutdown();
        System.exit(0);
    }

    /**
     * Helper function to share code between the unlink and unlink & wipe commands.
     */
    private void unlinkImplementation()
            throws SQLException, IOException
    {
        // Stop the daemon and other GUI services before deleting any files.
        UI.rap().stop();
        UI.rnc().stop();
        UI.dm().stopIgnoreException();

        // Delete revision history.
        // TODO (MP) possibly implement secure delete.
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absAuxRoot()));

        // Delete device key and certificate.
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot(), Param.DEVICE_KEY));
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot(), Param.DEVICE_CERT));

        // Delete the password.
        Cfg.db().set(Key.CRED, Key.CRED.defaultValue());
        // Create the setup file.
        _factFile.create(Util.join(Cfg.absRTRoot(), Param.SETTING_UP)).createNewFile();
    }

    private static class CommandFailed extends Exception
    {
        private static final long serialVersionUID = -5178178014589079953L;
    }
}