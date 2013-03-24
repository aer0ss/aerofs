package com.aerofs.daemon.mobile;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.CNameVerificationHandler.CNameListener;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.Version;
import com.aerofs.lib.event.Prio;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.proto.Common.PBException;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Mobile.DownloadCookie;
import com.aerofs.proto.Mobile.DownloadPacketReply;
import com.aerofs.proto.Mobile.IMobileService;
import com.aerofs.proto.Mobile.ListChildrenReply;
import com.aerofs.proto.Mobile.PBFile;
import com.aerofs.proto.Mobile.PBFile.Type;
import com.aerofs.proto.Mobile.StartDownloadReply;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;
import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

class MobileService implements IMobileService, CNameListener
{
    private static final Logger l = Loggers.getLogger(MobileService.class);
    private static final Prio PRIO = Prio.LO;
    public static final int DOWNLOAD_PACKET_LENGTH = 100 * C.KB;

    private final IIMCExecutor _imce;
    private UserID _remoteUser;

    MobileService(IIMCExecutor imce)
    {
        _imce = imce;
    }

    /**
     * Called by the cname verification handler after we successfully verified the identity of the
     * remote user
     */
    @Override
    public void onPeerVerified(UserID user, DID did)
    {
        _remoteUser = user;
    }

    @Override
    public PBException encodeError(Throwable e)
    {
        l.info("mobile request failed: ", e);
        return Exceptions.toPB(e);
    }

    @Override
    public ListenableFuture<ListChildrenReply> listChildren(PBPath pbPath)
            throws Exception
    {
        checkNotNull(_remoteUser);
        Path path = Path.fromPB(pbPath);

        EIGetChildrenAttr ev = new EIGetChildrenAttr(_remoteUser, path, Core.imce());
        ev.execute(PRIO);

        ListChildrenReply.Builder bd = ListChildrenReply.newBuilder();
        for (OA oa : ev._oas) {
            // Skip this object if it doesn't have a master branch (file not yet downloaded)
            if (oa.isFile() && oa.caMasterNullable() == null) continue;
            bd.addChildren(toPBFile(oa));
        }

        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<StartDownloadReply> startDownload(PBPath pbPath)
            throws Exception
    {
        checkNotNull(_remoteUser);
        Path path = Path.fromPB(pbPath);

        EIDownloadPacket ev = new EIDownloadPacket(_remoteUser, _imce, path, -1, -1);
        ev.execute(PRIO);

        return createReply(StartDownloadReply.newBuilder()
                .setLength(ev._fileLength)
                .setMtime(ev._fileModTime)
                .setCookie(makeCookie(ev).toByteString())
                .setMaxPacketLength(DOWNLOAD_PACKET_LENGTH)
                .build());
    }

    @Override
    public ListenableFuture<DownloadPacketReply> downloadPacket(ByteString cookie, Long offset,
            Integer length) throws Exception
    {
        checkNotNull(_remoteUser);
        DownloadCookie inCookie = DownloadCookie.parseFrom(cookie);

        EIDownloadPacket ev = new EIDownloadPacket(_remoteUser, _imce,
                Path.fromPB(inCookie.getPath()),
                offset, length);
        ev._inFileLength = inCookie.getLength();
        ev._inFileModTime = inCookie.getModTime();
        ev._inVersion = Version.fromPB(inCookie.getVersion());
        ev.execute(PRIO);

        DownloadPacketReply.Builder b = DownloadPacketReply.newBuilder();
        b.setData((ev._data != null) ? toByteString(ev._data) : ByteString.EMPTY);

        return createReply(b.build());
    }

    private static DownloadCookie makeCookie(EIDownloadPacket ev)
    {
        return DownloadCookie.newBuilder()
                .setPath(ev._path.toPB())
                .setLength(ev._fileLength)
                .setModTime(ev._fileModTime)
                .setVersion(ev._localVersion.toPB_())
                .build();
    }

    private static ByteString toByteString(ChannelBuffer cb)
    {
        if (cb.hasArray()) {
            return ByteString.copyFrom(cb.array(), cb.arrayOffset() + cb.readerIndex(),
                    cb.readableBytes());
        } else {
            return ByteString.copyFrom(cb.toByteBuffer());
        }
    }

    private static <T> ListenableFuture<T> createReply(T reply)
    {
        SettableFuture<T> future = SettableFuture.create();
        future.set(reply);
        return future;
    }

    private static PBFile toPBFile(OA oa)
    {
        PBFile.Builder bd = PBFile.newBuilder();
        bd.setName(oa.name());

        switch (oa.type()) {
        case DIR:
            bd.setType(Type.FOLDER);
            break;
        case ANCHOR:
            bd.setType(Type.SHARED_FOLDER);
            break;
        case FILE:
            bd.setType(Type.FILE);
            bd.setLength(oa.caMaster().length());
            bd.setMtime(oa.caMaster().mtime());
            break;
        default: throw new IllegalArgumentException("unknown type: " + oa.type());
        }

        return bd.build();
    }
}
