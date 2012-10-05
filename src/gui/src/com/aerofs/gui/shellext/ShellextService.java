package com.aerofs.gui.shellext;

import com.aerofs.lib.spsv.SVClient;
import org.apache.log4j.Logger;

import com.aerofs.lib.Path;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Shellext.RootAnchorNotification;
import com.aerofs.proto.Shellext.FileStatusNotification;
import com.aerofs.proto.Shellext.GreetingCall;
import com.aerofs.proto.Shellext.ShellextCall;
import com.aerofs.proto.Shellext.ShellextNotification;
import com.aerofs.proto.Shellext.ShellextNotification.Type;
import com.aerofs.ui.UIUtil;
import com.google.protobuf.InvalidProtocolBufferException;

public class ShellextService
{
    private static final Logger l = Util.l(ShellextService.class);

    private final ShellextServer _server;
    private static ShellextService _instance = null;

    /**
     * Used to make sure we are communicating with the right version of the shell extension
     * Bump this number every time shellext.proto changes
     */
    private final static int PROTOCOL_VERSION = 3;

    public static ShellextService get()
    {
        if (_instance == null) _instance = new ShellextService();
        return _instance;
    }

    private ShellextService()
    {
        new TransferStateNotifier(this);
        _server = new ShellextServer(Cfg.port(Cfg.PortType.UI));
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
     * Tell the shell extension the path to root anchor
     */
    public void notifyRootAnchor()
    {
        ShellextNotification notification = ShellextNotification.newBuilder()
                .setType(Type.ROOT_ANCHOR)
                .setRootAnchor(RootAnchorNotification.newBuilder()
                    .setPath(Cfg.absRootAnchor())
                    .setUser(Cfg.user()))
                .build();

        _server.send(notification.toByteArray());
    }

    protected void notifyDownload(String path, boolean value)
    {
        notifyFileStatus(FileStatusNotification.newBuilder().setPath(path).setDownloading(value));
    }

    protected void notifyUpload(String path, boolean value)
    {
        notifyFileStatus(FileStatusNotification.newBuilder().setPath(path).setUploading(value));
    }

    protected void notifyClearCache()
    {
        ShellextNotification reply = ShellextNotification.newBuilder()
                .setType(Type.CLEAR_STATUS_CACHE)
                .build();

        _server.send(reply.toByteArray());
    }

    private void notifyFileStatus(FileStatusNotification.Builder statusBuilder)
    {
        FileStatusNotification st = statusBuilder.build();
        if (st.getPath().isEmpty()) return;

        ShellextNotification reply = ShellextNotification.newBuilder()
                .setType(Type.FILE_STATUS)
                .setFileStatus(st)
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
        default:
            throw new ExProtocolError(ShellextCall.Type.class);
        }
    }

    private void greeting(GreetingCall call) throws ExProtocolError
    {
        if (call.getProtocolVersion() != PROTOCOL_VERSION) {
            String msg = "Trying to communicate with a different version of the shell extension." +
                    "GUI: " + PROTOCOL_VERSION + " ShellExt: " + call.getProtocolVersion();
            SVClient.logSendDefectAsync(true, msg);
            throw new ExProtocolError(msg);
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

        UIUtil.createOrManageSharedFolder(Path.fromAbsoluteString(absRootAnchor, absPath));
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

        UIUtil.showSyncStatus(Path.fromAbsoluteString(absRootAnchor, absPath));
    }

    private void versionHistory(final String absPath)
    {
        String absRootAnchor = Cfg.absRootAnchor();
        if (!Path.isUnder(absRootAnchor, absPath)) {
            l.warn("shellext provided an external path " + absPath);
            return;
        }

        UIUtil.showVersionHistory(Path.fromAbsoluteString(absRootAnchor, absPath));
    }
}
