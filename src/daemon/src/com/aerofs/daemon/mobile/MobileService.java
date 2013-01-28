package com.aerofs.daemon.mobile;

import com.aerofs.base.BaseParam;
import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.C;
import com.aerofs.base.id.UserID;
import com.aerofs.base.net.AbstractRpcServerHandler;
import com.aerofs.base.net.MagicHeader;
import com.aerofs.base.ssl.CNameVerificationHandler;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.labeling.L;
import com.aerofs.lib.Path;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgCACertFilename;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.KIndex;
import com.aerofs.proto.Common;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Mobile;
import com.aerofs.proto.Mobile.DownloadCookie;
import com.aerofs.proto.Mobile.DownloadPacketReply;
import com.aerofs.proto.Mobile.IMobileService;
import com.aerofs.proto.Mobile.StartDownloadReply;
import com.aerofs.proto.Objects.GetChildrenAttributesReply;
import com.aerofs.proto.Objects.GetObjectAttributesReply;
import com.aerofs.proto.Objects.PBBranch;
import com.aerofs.proto.Objects.PBObjectAttributes;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.jboss.netty.handler.ssl.SslHandler;

import java.security.cert.Certificate;
import java.util.Map.Entry;

public class MobileService implements IMobileService
{
    private static final Prio PRIO = Prio.LO;
    private static final MagicHeader MAGIC_HEADER = new MagicHeader(
            BaseParam.MobileService.MAGIC_BYTES, BaseParam.MobileService.VERSION_NUMBER);

    private final IIMCExecutor _imce;

    private MobileService(IIMCExecutor imce)
    {
        _imce = imce;
    }

    @Override
    public Common.PBException encodeError(Throwable e)
    {
        return Exceptions.toPBWithStackTrace(e);
    }

    @Override
    public ListenableFuture<Void> heartbeat() throws Exception
    {
        return createVoidReply();
    }

    @Override
    public ListenableFuture<GetObjectAttributesReply> getObjectAttributes(String user, PBPath path)
            throws Exception
    {
        EIGetAttr ev = new EIGetAttr(UserID.fromExternal(user), _imce, new Path(path));
        ev.execute(PRIO);
        if (ev._oa == null) throw new ExNotFound();

        GetObjectAttributesReply reply = GetObjectAttributesReply.newBuilder()
                .setObjectAttributes(toPB(ev._oa))
                .build();
        return createReply(reply);
    }

    @Override
    public ListenableFuture<GetChildrenAttributesReply> getChildrenAttributes(String user,
            PBPath path) throws Exception
    {
        EIGetChildrenAttr ev = new EIGetChildrenAttr(UserID.fromExternal(user), new Path(path),
                Core.imce());
        ev.execute(PRIO);

        GetChildrenAttributesReply.Builder bd = GetChildrenAttributesReply.newBuilder();
        for (OA oa : ev._oas) {
            // Skip this object if it doesn't have a master branch (file not yet downloaded)
            if (oa.isFile() && oa.caMasterNullable() == null) continue;
            bd.addChildrenName(oa.name());
            bd.addChildrenAttributes(toPB(oa));
        }

        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<StartDownloadReply> startDownload(PBPath source)
            throws Exception
    {
        EIDownloadPacket ev = new EIDownloadPacket(Cfg.user(), _imce, new Path(source), -1, -1);
        ev.execute(PRIO);

        StartDownloadReply.Builder b = StartDownloadReply.newBuilder()
                .setLength(ev._fileLength)
                .setCookie(makeCookie(ev).toByteString());

        return createReply(b.build());
    }

    @Override
    public ListenableFuture<DownloadPacketReply> downloadPacket(PBPath source,
            ByteString cookie, Long offset, Integer length) throws Exception
    {
        DownloadCookie inCookie = DownloadCookie.parseFrom(cookie);

        EIDownloadPacket ev = new EIDownloadPacket(Cfg.user(), _imce, new Path(source), offset, length);
        ev._inFileLength = inCookie.getLength();
        ev._inFileModTime = inCookie.getModTime();
        ev._inVersion = new Version(inCookie.getVersion());
        ev.execute(PRIO);

        DownloadPacketReply.Builder b = DownloadPacketReply.newBuilder();
        b.setDone(ev._done);
        if (ev._data != null) {
            b.setData(toByteString(ev._data));
        } else {
            b.setData(ByteString.EMPTY);
        }

        return createReply(b.build());
    }

    private static DownloadCookie makeCookie(EIDownloadPacket ev)
    {
        DownloadCookie.Builder b = DownloadCookie.newBuilder()
        .setLength(ev._fileLength)
        .setModTime(ev._fileModTime)
        .setVersion(ev._localVersion.toPB_());
        return b.build();
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

    private static ListenableFuture<Void> createVoidReply()
    {
        return createReply(Void.getDefaultInstance());
    }

    private static PBObjectAttributes toPB(OA oa)
    {
        PBObjectAttributes.Builder bd = PBObjectAttributes.newBuilder()
                .setExcluded(oa.isExpelled());

        switch (oa.type()) {
        case DIR:
            bd.setType(PBObjectAttributes.Type.FOLDER);
            break;
        case ANCHOR:
            bd.setType(PBObjectAttributes.Type.SHARED_FOLDER);
            break;
        case FILE:
            bd.setType(PBObjectAttributes.Type.FILE);
            for (Entry<KIndex, CA> en : oa.cas().entrySet()) {
                bd.addBranch(PBBranch.newBuilder()
                        .setKidx(en.getKey().getInt())
                        .setLength(en.getValue().length())
                        .setMtime(en.getValue().mtime()));
            }
            // asserts that the first branch is indeed the master branch
            if (bd.getBranchCount() > 0) assert bd.getBranch(0).getKidx() == KIndex.MASTER.getInt();
            break;
        default: assert false;
        }

        return bd.build();
    }

    private static class MobileServiceHandler extends AbstractRpcServerHandler
    {
        private final MobileService _service;
        private final Mobile.MobileServiceReactor _reactor;

        public MobileServiceHandler(MobileService service)
        {
            _service = service;
            _reactor = new Mobile.MobileServiceReactor(_service);
        }

        @Override
        protected ListenableFuture<byte[]> react(byte[] data)
        {
            return _reactor.react(data);
        }
    }

    public static class Factory implements ChannelPipelineFactory
    {
        private static final int MAX_FRAME_LENGTH = 1 * C.MB;
        private static final int LENGTH_FIELD_SIZE = 4;

        public static boolean isEnabled()
        {
            return L.get().isStaging() || Cfg.user().isAeroFSUser();
        }

        private final IIMCExecutor _imce;
        private final SSLEngineFactory _sslEngineFactory;
        private final CfgLocalUser _cfgLocalUser;
        private final CfgLocalDID _cfgLocalDID;

        @Inject
        public Factory(
                CoreIMCExecutor cimce,
                CfgCACertFilename cfgCACertFilename,
                CfgKeyManagersProvider cfgKeyManagersProvider,
                CfgLocalUser cfgLocalUser, CfgLocalDID cfgLocalDID)
        {
            _imce = cimce.imce();
            _cfgLocalUser = cfgLocalUser;
            _cfgLocalDID = cfgLocalDID;

            Certificate caCert;
            try {
                caCert = BaseSecUtil.newCertificateFromFile(cfgCACertFilename.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // TODO (GS): Provide a CRL
            _sslEngineFactory = new SSLEngineFactory(false, cfgKeyManagersProvider, caCert, null);
        }

        @Override
        public ChannelPipeline getPipeline() throws Exception
        {
            ChannelPipeline pipeline = Channels.pipeline();
            appendToPipeline(pipeline);
            return pipeline;
        }

        public void appendToPipeline(ChannelPipeline p) throws Exception
        {
            MobileService mobileService = new MobileService(_imce);
            SslHandler sslHandler = new SslHandler(_sslEngineFactory.getSSLEngine());
            sslHandler.setCloseOnSSLException(true);
            p.addLast("ssl", sslHandler);
            p.addLast("frameDecoder",
                    new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_SIZE, 0,
                            LENGTH_FIELD_SIZE));
            p.addLast("frameEncoder", new LengthFieldPrepender(LENGTH_FIELD_SIZE));
            p.addLast("magicHeaderWriter", MAGIC_HEADER.new WriteMagicHeaderHandler());
            p.addLast("magicHeaderReader", MAGIC_HEADER.new ReadMagicHeaderHandler());
            p.addLast("cname", new CNameVerificationHandler(_cfgLocalUser.get(), _cfgLocalDID.get()));
            p.addLast("mobileServiceHandler", new MobileServiceHandler(mobileService));
        }
    }
}
