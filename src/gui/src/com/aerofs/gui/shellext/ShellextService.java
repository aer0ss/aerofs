package com.aerofs.gui.shellext;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.gui.GUIUtil;
import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ritual.RitualClientProvider;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.PathStatus.PBPathStatus;
import com.aerofs.proto.Ritual.GetPathStatusReply;
import com.aerofs.proto.Shellext.GreetingCall;
import com.aerofs.proto.Shellext.PathStatusNotification;
import com.aerofs.proto.Shellext.RootAnchorNotification;
import com.aerofs.proto.Shellext.ShellextCall;
import com.aerofs.proto.Shellext.ShellextNotification;
import com.aerofs.proto.Shellext.ShellextNotification.Type;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.protobuf.InvalidProtocolBufferException;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.slf4j.Logger;

import java.util.List;

import static com.google.common.util.concurrent.Futures.addCallback;

public class ShellextService
{
    private static final Logger l = Loggers.getLogger(ShellextService.class);

    private final ServerSocketChannelFactory _serverChannelFactory;
    private final RitualClientProvider _ritualProvider;

    private ShellextServer _server;

    /**
     * Used to make sure we are communicating with the right version of the shell extension
     * Bump this number every time shellext.proto changes
     */
    private final static int PROTOCOL_VERSION = 5;


    public ShellextService(ServerSocketChannelFactory serverChannelFactory, RitualClientProvider ritualProvider)
    {
        _serverChannelFactory = serverChannelFactory;
        _ritualProvider = ritualProvider;
    }

    public void start_()
    {
        if (_server == null) {
            _server = new ShellextServer(Cfg.port(Cfg.PortType.UI), _serverChannelFactory, this);
            _server.start_();

            new PathStatusNotificationForwarder(this);

            OSUtil.get().startShellExtension(getPort());
        }
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
            // TODO (MP) remove this when the shell ext is rebuilt.
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

        GUIUtil.createOrManageSharedFolder(mkpath(absRootAnchor, absPath));
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
                l.warn("ss overview fetch for shellext: " + Util.e(throwable));
                // TODO: send clear cache? retry?
            }
        });
    }
}
