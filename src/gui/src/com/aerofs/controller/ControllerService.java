/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.lib.ThreadUtil;
import com.aerofs.base.async.UncancellableFuture;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.base.id.UserID;
import com.aerofs.proto.Common;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.ControllerNotifications.Type;
import com.aerofs.proto.ControllerProto.GetConfigReply;
import com.aerofs.proto.ControllerProto.GetInitialStatusReply;
import com.aerofs.proto.ControllerProto.GetSetupSettingsReply;
import com.aerofs.proto.ControllerProto.IControllerService;
import com.aerofs.proto.ControllerProto.PBConfig;
import com.aerofs.proto.ControllerProto.PBS3Config;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.GeneratedMessageLite;

/**
 * Implementation of the controller.proto service
 *
 * If you want to call one of the methods from controller.proto, use ControllerClient instead,
 * available from UI.controller()
 *
 * Direct access to this class (through ControllerService.get()) should only be made from classes
 * of the com.aerofs.controller package.
 */
public class ControllerService implements IControllerService
{
    private static ControllerService s_instance = null;
    private final String _rtRoot;
    private final IViewNotifier _notifier;
    private final Launcher _launcher;
    private final Setup _setup;

    // TODO (GS): remove the "public" qualifier when all callers are migrated to the
    // com.aerofs.controller package
    public static ControllerService get()
    {
        assert s_instance != null;
        return s_instance;
    }

    /**
     * Inititializes the ControllerService.
     * This method must be called at the beginning of every *Program class (GUIProgram,
     * CLIProgram, etc)
     * @param rtRoot the rt root
     * @param notifier: who the controller will send notifications to.
     */
    public static void init(String rtRoot, IViewNotifier notifier)
    {
        assert s_instance == null;
        s_instance = new ControllerService(rtRoot, notifier);
    }

    private ControllerService(String rtRoot, IViewNotifier notifier)
    {
        _rtRoot = rtRoot;
        _notifier = notifier;
        _launcher = new Launcher(_rtRoot);
        _setup = new Setup(_rtRoot);
    }

    /**
     * Sends a notification to the UI.
     * This method is thread-safe.
     * @param type : notification type (see notifications.proto)
     * @param notification : notification protobuf message (see notifications.proto)
     */
    public void notifyUI(Type type, GeneratedMessageLite notification)
    {
        _notifier.notify(type, notification);
    }

    @Override
    public PBException encodeError(Throwable error)
    {
        return Exceptions.toPBWithStackTrace(error);
    }

    @Override
    public ListenableFuture<GetInitialStatusReply> getInitialStatus()
            throws Exception
    {
        return UncancellableFuture.createSucceeded(_launcher.getInitialStatus());
    }

    @Override
    public ListenableFuture<Common.Void> launch()
            throws Exception
    {
        final UncancellableFuture<Common.Void> reply = UncancellableFuture.create();
        ThreadUtil.startDaemonThread("launcher-worker", new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    _launcher.launch(false);
                } catch (Exception e) {
                    reply.setException(e);
                }
                reply.set(Common.Void.getDefaultInstance());
            }
        });

        return reply;
    }

    @Override
    public ListenableFuture<GetSetupSettingsReply> getSetupSettings()
            throws Exception
    {
        GetSetupSettingsReply reply = GetSetupSettingsReply.newBuilder()
                .setRootAnchor(_setup.getDefaultAnchorRoot())
                .setDeviceName(_setup.getDefaultDeviceName())
                .build();
        return UncancellableFuture.createSucceeded(reply);
    }

    @Override
    public ListenableFuture<Common.Void> setupSingleuser(String userId, String password,
            String rootAnchor, String deviceName, PBS3Config s3config)
            throws Exception
    {
        _setup.setupSingleuser(UserID.fromExternal(userId), password.toCharArray(), rootAnchor,
                deviceName, s3config);
        return UncancellableFuture.createSucceeded(Common.Void.getDefaultInstance());
    }


    @Override
    public ListenableFuture<Common.Void> setupMultiuser(String userId, String password,
            String rootAnchor, String deviceName, PBS3Config s3config)
            throws Exception
    {
        _setup.setupMultiuser(UserID.fromExternal(userId), password.toCharArray(), rootAnchor,
                deviceName, s3config);
        return UncancellableFuture.createSucceeded(Common.Void.getDefaultInstance());
    }

    @Override
    public ListenableFuture<Common.Void> sendPasswordResetEmail(String userId)
        throws Exception
    {
        CredentialUtil.sendPasswordResetEmail(userId);
        return UncancellableFuture.createSucceeded(Common.Void.getDefaultInstance());
    }

    @Override
    public ListenableFuture<Common.Void> resetPassword(String userID, String resetToken,
            String password)
            throws Exception
    {
        CredentialUtil.resetPassword(UserID.fromExternal(userID), resetToken, password.toCharArray());
        return UncancellableFuture.createSucceeded(Common.Void.getDefaultInstance());
    }

    @Override
    public ListenableFuture<Common.Void> changePassword(String userID, String oldPassword,
            String newPassword)
        throws Exception
    {

        CredentialUtil.changePassword(UserID.fromExternal(userID), oldPassword.toCharArray(),
                newPassword.toCharArray());
        return UncancellableFuture.createSucceeded(Common.Void.getDefaultInstance());
    }

    @Override
    public ListenableFuture<Common.Void> updateStoredPassword(String userID, String password)
            throws Exception
    {
        CredentialUtil.updateStoredPassword(UserID.fromExternal(userID), password.toCharArray());
        return UncancellableFuture.createSucceeded(Common.Void.getDefaultInstance());
    }

    @Override
    public ListenableFuture<GetConfigReply> getConfig()
            throws Exception
    {
        PBConfig config = PBConfig.newBuilder()
                .setVersion(Cfg.ver())
                .setUserName(Cfg.user().getString())
                .setDeviceId(Cfg.did().toStringFormal())
                .setRootAnchor(Cfg.absRootAnchor())
                .build();

        GetConfigReply reply = GetConfigReply.newBuilder()
                .setConfig(config)
                .build();

        return UncancellableFuture.createSucceeded(reply);
    }

    @Override
    public ListenableFuture<PBConfig> updateConfig(PBConfig config)
            throws Exception
    {
        return null;
    }
}
