/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

import com.aerofs.base.BaseParam.Verkehr;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.lib.sched.IScheduler;
import com.aerofs.proto.Cmd.Command;
import com.aerofs.proto.Ritual.CreateSeedFileReply;
import com.aerofs.proto.Sp.AckCommandQueueHeadReply;
import com.aerofs.proto.Sp.GetCommandQueueHeadReply;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.verkehr.client.lib.IConnectionListener;
import com.aerofs.verkehr.client.lib.subscriber.ClientFactory;
import com.aerofs.verkehr.client.lib.subscriber.ISubscriptionListener;
import com.aerofs.verkehr.client.lib.subscriber.VerkehrSubscriber;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.InvalidProtocolBufferException;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.aerofs.base.BaseParam.VerkehrTopics.CMD_CHANNEL_TOPIC_PREFIX;
import static com.aerofs.lib.LibParam.Verkehr.VERKEHR_RETRY_INTERVAL;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

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

    public CommandNotificationSubscriber(
            ClientSocketChannelFactory clientChannelFactory,
            IScheduler scheduler,
            DID localDevice)
    {
        _scheduler = scheduler;
        _er = new ExponentialRetry(_scheduler);

        _listener = new VerkehrListener();

        l.debug("cmd: " + Verkehr.HOST.get() + ":" + Verkehr.SUBSCRIBE_PORT.get());
        ClientFactory factory = new ClientFactory(
                Verkehr.HOST.get(),
                Short.parseShort(Verkehr.SUBSCRIBE_PORT.get()),
                clientChannelFactory,
                new CfgCACertificateProvider(),
                new CfgKeyManagersProvider(),
                VERKEHR_RETRY_INTERVAL,
                Cfg.db().getLong(Key.TIMEOUT),
                new HashedWheelTimer(),
                _listener, _listener, sameThreadExecutor());

        this._topic = CMD_CHANNEL_TOPIC_PREFIX + localDevice.toStringFormal();
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
                case UPLOAD_DATABASE:
                    UIGlobals.ic().startUploadDatabase();
                    break;
                case CHECK_UPDATE:
                    UIGlobals.updater().checkForUpdate(true);
                    break;
                case SEND_DEFECT:
                    SVClient.logSendDefectAsync(true, "cmd call");
                    break;
                case LOG_THREADS:
                    logThreads();
                    break;
                default:
                    throw new Exception("cmd type unknown");
            }
        }
    }

    private static SPBlockingClient newAuthenticatedSPClient()
            throws Exception
    {
        SPBlockingClient.Factory fact = new SPBlockingClient.Factory();
        SPBlockingClient sp = fact.create_(Cfg.user());
        sp.signInRemote();
        return sp;
    }

    private static SPBlockingClient newUnauthenticatedSPClient()
            throws Exception
    {
        SPBlockingClient.Factory fact = new SPBlockingClient.Factory();
        // We would like to avoid making SP do the work of verifying our client cert for this call,
        // since command head is unauthenticated (which is needed to allow the remote wipe command
        // to propagate after credentials are revoked).
        return fact.create_(Cfg.user(), SPBlockingClient.ONE_WAY_AUTH_CONNECTION_CONFIGURATOR);
    }

    //
    // Command implementations.
    //

    private void invalidateUserNameCache()
            throws Exception
    {
        UIGlobals.ritual().invalidateUserNameCache();
    }

    private void invalidateDeviceNameCache()
            throws Exception
    {
        UIGlobals.ritual().invalidateDeviceNameCache();
    }


    // Time, in miliseconds, given to the daemon to populate a seed file for a physical root
    // NB: we will wait AT MOST that amount of time but if the seed file is populated before that
    // timeout we will wait considerably less.
    // Tests show that seed files are populated at ~60k objects per second.
    private static final int SEED_FILE_CREATION_TIMEOUT = 5000;

    private void createSeedFiles()
    {
        if (Cfg.storageType() == StorageType.LINKED) {
            for (SID sid : Cfg.getRoots().keySet()) {
                // try creating a seed file (use async ritual API to leverage SP call latency)
                ListenableFuture<CreateSeedFileReply> reply = UIGlobals.ritualNonBlocking()
                        .createSeedFile(sid.toPB());

                try {
                    // give the daemon some room to create the seed file before making the SP call
                    reply.get(SEED_FILE_CREATION_TIMEOUT, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    l.info("failed to create seed file for {}: {}", sid, Util.e(e));
                }
            }
        }
    }


    private void unlinkSelf()
            throws Exception
    {
        UIGlobals.analytics().track(SimpleEvents.UNLINK_DEVICE);

        createSeedFiles();

        unlinkImplementation();

        UI.get().shutdown();
    }

    private void unlinkAndWipeSelf()
            throws Exception
    {
        UIGlobals.analytics().track(SimpleEvents.UNLINK_AND_WIPE);

        unlinkImplementation();

        // Delete Root Anchor.
        // TODO (MP) possibly implement secure delete.
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absDefaultRootAnchor()));
        // Also delete the entire config directory, if possible.
        // On Windows, files we hold open may refuse to be deleted.
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot()));

        UI.get().shutdown();
    }

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
        UIGlobals.ritual().logThreads();
    }

    //
    // Implementation helpers.
    //

    /**
     * Helper function to share code between the unlink and unlink & wipe commands.
     */
    private void unlinkImplementation()
            throws SQLException, IOException
    {
        // Stop the daemon and other GUI services before deleting any files.
        UIGlobals.rap().stop();
        UIGlobals.rnc().stop();
        UIGlobals.dm().stopIgnoreException();

        // TODO (MP) possibly implement secure delete.
        // Delete aux roots (partial downloads, conflicts and revision history)
        if (Cfg.storageType() == StorageType.LINKED) {
            for (Entry<SID, String> e : Cfg.getRoots().entrySet()) {
                RootAnchorUtil.cleanAuxRootForPath(e.getValue(), e.getKey());
            }
        } else {
            RootAnchorUtil.cleanAuxRootForPath(Cfg.absDefaultRootAnchor(), Cfg.rootSID());
        }

        // Delete device key and certificate.
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot(), LibParam.DEVICE_KEY));
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot(), LibParam.DEVICE_CERT));

        // Delete the password.
        Cfg.db().set(Key.CRED, Key.CRED.defaultValue());
        // Delete the device id
        Cfg.db().set(Key.DEVICE_ID, Key.DEVICE_ID.defaultValue());
        // Create the setup file.
        _factFile.create(Util.join(Cfg.absRTRoot(), LibParam.SETTING_UP)).createNewFile();
    }

    private static class CommandFailed extends Exception
    {
        private static final long serialVersionUID = -5178178014589079953L;
    }
}
