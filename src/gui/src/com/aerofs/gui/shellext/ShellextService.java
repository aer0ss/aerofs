package com.aerofs.gui.shellext;

import com.aerofs.base.Loggers;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.ritual.RitualClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.Ritual.GetPathStatusReply;
import com.aerofs.proto.Shellext.PathStatusNotification;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.slf4j.Logger;

import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Shellext.RootAnchorNotification;
import com.aerofs.proto.Shellext.GreetingCall;
import com.aerofs.proto.Shellext.ShellextCall;
import com.aerofs.proto.Shellext.ShellextNotification;
import com.aerofs.proto.Shellext.ShellextNotification.Type;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.List;

public class ShellextService
{
    private static final Logger l = Loggers.getLogger(ShellextService.class);

    private final ShellextServer _server;
    private static ShellextService _instance = null;
    private RitualClient _ritual = null;

    /**
     * Used to make sure we are communicating with the right version of the shell extension
     * Bump this number every time shellext.proto changes
     */
    private final static int PROTOCOL_VERSION = 5;

    public static ShellextService get()
    {
        if (_instance == null) _instance = new ShellextService();
        return _instance;
    }

    private ShellextService()
    {
        new PathStatusNotificationForwarder(this);
        _server = new ShellextServer(this, Cfg.port(Cfg.PortType.UI));
    }

    public void start_()
    {
        _server.start_();
        OSUtil.get().startShellExtension(getPort());
    }

    public int getPort()
    {
        return _server.getPort();
    }

    /**
     * Tell the shell extension the path to root anchor. Ignore any error.
     */
    public void notifyRootAnchor()
    {
        ShellextNotification notification = ShellextNotification.newBuilder()
                .setType(Type.ROOT_ANCHOR)
                .setRootAnchor(RootAnchorNotification.newBuilder()
                    .setPath(Cfg.absRootAnchor())
                    .setUser(Cfg.user().getString()))
                .build();

        _server.send(notification.toByteArray());
    }

    protected void notifyPathStatus(String path, PBPathStatus status)
    {
        if (path.isEmpty()) return;
        PathStatusNotification st = PathStatusNotification.newBuilder()
                .setPath(path)
                .setStatus(status)
                .build();

        ShellextNotification reply = ShellextNotification.newBuilder()
                .setType(Type.PATH_STATUS)
                .setPathStatus(st)
                .build();

        _server.send(reply.toByteArray());
    }

    protected void notifyClearCache()
    {
        ShellextNotification reply = ShellextNotification.newBuilder()
                .setType(Type.CLEAR_STATUS_CACHE)
                .build();

        _server.send(reply.toByteArray());
    }

    protected void react(byte[] bytes)
            throws InvalidProtocolBufferException, ExProtocolError
    {
        ShellextCall call = ShellextCall.parseFrom(bytes);
        switch (call.getType()) {
        case GREETING:
            assert (call.hasGreeting());
            greeting(call.getGreeting());
            break;
        case SHARE_FOLDER:
            assert (call.hasShareFolder());
            shareFolder(call.getShareFolder().getPath());
            break;
        case SYNC_STATUS:
            assert (call.hasSyncStatus());
            syncStatus(call.getSyncStatus().getPath());
            break;
        case VERSION_HISTORY:
            assert (call.hasVersionHistory());
            versionHistory(call.getVersionHistory().getPath());
            break;
        case GET_PATH_STATUS:
            assert (call.hasGetPathStatus());
            getStatus(call.getGetPathStatus().getPath());
            break;
        case CONFLICT_RESOLUTION:
            assert (call.hasConflictResolution());
            conflictResolution(call.getConflictResolution().getPath());
            break;
        default:
            throw new ExProtocolError(ShellextCall.Type.class);
        }
    }

    private void greeting(GreetingCall call)
    {
        if (call.getProtocolVersion() != PROTOCOL_VERSION) {
            l.warn("Trying to communicate with a different " +
                    "version of the shell extension. GUI: " + PROTOCOL_VERSION + " ShellExt: " +
                    call.getProtocolVersion());
        }

        notifyRootAnchor();
    }

    /**
     * @param absPath the absolute path
     */
    private void shareFolder(final String absPath)
    {
        String absRootAnchor = Cfg.absRootAnchor();
        if (!Path.isUnder(absRootAnchor, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }

        GUIUtil.createOrManageSharedFolder(Path.fromAbsoluteString(absRootAnchor, absPath));
    }

    /**
     * @param absPath the absolute path
     */
    private void syncStatus(final String absPath)
    {
        String absRootAnchor = Cfg.absRootAnchor();
        if (!Path.isUnder(absRootAnchor, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }

        GUIUtil.showSyncStatus(Path.fromAbsoluteString(absRootAnchor, absPath));
    }

    private void versionHistory(final String absPath)
    {
        String absRootAnchor = Cfg.absRootAnchor();
        if (!Path.isUnder(absRootAnchor, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }

        GUIUtil.showVersionHistory(Path.fromAbsoluteString(absRootAnchor, absPath));
    }

    private void conflictResolution(final String absPath)
    {
        String absRootAnchor = Cfg.absRootAnchor();
        if (!Path.isUnder(absRootAnchor, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }

        GUIUtil.showConflictResolutionDialog(Path.fromAbsoluteString(absRootAnchor, absPath));
    }

    private void getStatus(final String absPath)
    {
        String absRoot = Cfg.absRootAnchor();
        if (!Path.isUnder(absRoot, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }

        final List<PBPath> pbPaths = Lists.newArrayList(
                Path.fromAbsoluteString(absRoot, absPath).toPB());

        if (_ritual == null) {
            _ritual = RitualClientFactory.newClient();
        }

        // make asynchronous ritual call and send shellext notifications when reply received
        Futures.addCallback(_ritual.getPathStatus(pbPaths),
                new FutureCallback<GetPathStatusReply>()
                {
                    @Override
                    public void onSuccess(GetPathStatusReply reply)
                    {
                        assert reply.getStatusCount() == 1 : reply.getStatusCount();
                        notifyPathStatus(absPath, reply.getStatus(0));
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        l.warn("ss overview fetch for shellext: " + Util.e(throwable));
                        _ritual = null;
                        // TODO: send clear cache? retry?
                    }
                });
    }
}
