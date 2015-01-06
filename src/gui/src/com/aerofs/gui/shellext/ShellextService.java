package com.aerofs.gui.shellext;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.NativeSocketType;
import com.aerofs.lib.nativesocket.NativeSocketAuthenticatorFactory;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.Ritual.GetPathStatusReply;
import com.aerofs.proto.Shellext.GreetingCall;
import com.aerofs.proto.Shellext.PathStatusNotification;
import com.aerofs.proto.Shellext.RootAnchorNotification;
import com.aerofs.proto.Shellext.ShellextCall;
import com.aerofs.proto.Shellext.ShellextNotification;
import com.aerofs.proto.Shellext.ShellextNotification.Type;
import com.aerofs.ritual.RitualBlockingClient;
import com.aerofs.ritual.RitualClient;
import com.aerofs.ritual.RitualClientProvider;
import com.flipkart.phantom.netty.common.OioServerSocketChannel;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import java.nio.channels.ClosedChannelException;
import java.io.File;
import java.util.List;

import static com.google.common.util.concurrent.Futures.addCallback;

public class ShellextService
{
    private static final Logger l = Loggers.getLogger(ShellextService.class);

    private final RitualClientProvider _ritualProvider;
    private ShellextServer _server;

    /**
     * Used to make sure we are communicating with the right version of the shell extension
     * Bump this number every time shellext.proto changes
     */
    private final static int PROTOCOL_VERSION = 5;

    public ShellextService(RitualClientProvider ritualProvider)
    {
        _ritualProvider = ritualProvider;
    }

    public void start_()
    {
        if (_server == null) {
            File socketFile = new File(Cfg.nativeSocketFilePath(NativeSocketType.SHELLEXT));
            _server = new ShellextServer(this, socketFile,
                    NativeSocketAuthenticatorFactory.create());
            _server.start_();

            new PathStatusNotificationForwarder(this);
            OSUtil.get().startShellExtension(socketFile);
        }
    }

    /**
     * Tell the shell extension the path to root anchor. Ignore any error.
     */
    public void notifyRootAnchor()
    {
        if (_server == null) return;

        ShellextNotification notification = ShellextNotification.newBuilder()
                .setType(Type.ROOT_ANCHOR)
                .setRootAnchor(RootAnchorNotification.newBuilder()
                    .setPath(Cfg.absDefaultRootAnchor())
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

    private String normalize(String path)
    {
        return OSUtil.get().normalizeInputFilename(path);
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
            shareFolder(normalize(call.getShareFolder().getPath()));
            break;
        case SYNC_STATUS:
            // TODO (MP) remove this when the shell ext is rebuilt.
            break;
        case VERSION_HISTORY:
            assert (call.hasVersionHistory());
            versionHistory(normalize(call.getVersionHistory().getPath()));
            break;
        case GET_PATH_STATUS:
            assert (call.hasGetPathStatus());
            getStatus(normalize(call.getGetPathStatus().getPath()));
            break;
        case CONFLICT_RESOLUTION:
            assert (call.hasConflictResolution());
            conflictResolution(normalize(call.getConflictResolution().getPath()));
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

    private Path mkpath(String absRoot, String absPath)
    {
        // TODO: support multiroot
        return Path.fromAbsoluteString(Cfg.rootSID(), absRoot, absPath);
    }

    /**
     * @param absPath the absolute path
     */
    private void shareFolder(final String absPath)
    {
        String absRootAnchor = Cfg.absDefaultRootAnchor();
        if (!Path.isUnder(absRootAnchor, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }
        Path path = mkpath(absRootAnchor, absPath);
        GUIUtil.shareFolder(path, path.last());
    }

    private void versionHistory(final String absPath)
    {
        String absRootAnchor = Cfg.absDefaultRootAnchor();
        if (!Path.isUnder(absRootAnchor, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }

        GUIUtil.showVersionHistory(mkpath(absRootAnchor, absPath));
    }

    private void conflictResolution(final String absPath)
    {
        String absRootAnchor = Cfg.absDefaultRootAnchor();
        if (!Path.isUnder(absRootAnchor, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }

        GUIUtil.showConflictResolutionDialog(mkpath(absRootAnchor, absPath));
    }

    private void getStatus(final String absPath)
    {
        String absRoot = Cfg.absDefaultRootAnchor();
        if (!Path.isUnder(absRoot, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }

        //
        // make asynchronous ritual call and send shellext notifications when reply received
        //

        final List<PBPath> pbPaths = Lists.newArrayList(mkpath(absRoot, absPath).toPB());
        addCallback(_ritualProvider.getNonBlockingClient().getPathStatus(pbPaths), new FutureCallback<GetPathStatusReply>()
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
                l.warn("ss overview fetch for shellext: {}",
                        BaseLogUtil.suppress(throwable, ClosedChannelException.class));
                // TODO: send clear cache? retry?
            }
        });
    }
}
