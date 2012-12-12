package com.aerofs.daemon.mobile;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.core.CoreIMCExecutor;
import com.aerofs.daemon.core.ds.CA;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.fs.EIGetAttr;
import com.aerofs.daemon.event.fs.EIGetChildrenAttr;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.C;
import com.aerofs.lib.Path;
import com.aerofs.lib.Version;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.ex.Exceptions;
import com.aerofs.lib.id.KIndex;
import com.aerofs.lib.id.UserID;
import com.aerofs.base.net.AbstractRpcServerHandler;
import com.aerofs.base.net.MagicHeader;
import com.aerofs.proto.Common;
import com.aerofs.proto.Common.PBPath;
import com.aerofs.proto.Common.Void;
import com.aerofs.proto.Mobile;
import com.aerofs.proto.Mobile.DownloadCookie;
import com.aerofs.proto.Mobile.DownloadPacketReply;
import com.aerofs.proto.Mobile.IMobileService;
import com.aerofs.proto.Mobile.StartDownloadReply;
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.GetObjectAttributesReply;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map.Entry;

public class MobileService implements IMobileService
{
    //private final static Logger l = Util.l(MobileService.class);
    private static final Prio PRIO = Prio.LO;

    private static final byte[] MAGIC_BYTES = "MOBL".getBytes();
    private static final int VERSION_NUMBER = 2;
    private static final MagicHeader MAGIC_HEADER = new MagicHeader(MAGIC_BYTES, VERSION_NUMBER);

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
    public ListenableFuture<StartDownloadReply> startDownload(String user, PBPath source)
            throws Exception
    {
        EIDownloadPacket ev = new EIDownloadPacket(UserID.fromExternal(user),
                _imce, new Path(source), -1, -1);
        ev.execute(PRIO);

        StartDownloadReply.Builder b = StartDownloadReply.newBuilder()
                .setLength(ev._fileLength)
                .setCookie(makeCookie(ev).toByteString());

        return createReply(b.build());
    }

    @Override
    public ListenableFuture<DownloadPacketReply> downloadPacket(String user, PBPath source,
            ByteString cookie, Long offset, Integer length) throws Exception
    {
        DownloadCookie inCookie = DownloadCookie.parseFrom(cookie);

        EIDownloadPacket ev = new EIDownloadPacket(UserID.fromExternal(user), _imce,
                new Path(source), offset, length);
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
        //private static final String PROPERTY = "aerofs.mobile.enabled";

        private static final int MAX_FRAME_LENGTH = 1 * C.MB;
        private static final int LENGTH_FIELD_SIZE = 4;

        private static final String JKS = "JKS";
        private static final String SUNX509 = "SunX509";
        private static final String TLS = "TLS";
        private static final String DEVICE = "device";

        public static boolean isEnabled()
        {
//            return Boolean.getBoolean(PROPERTY) && Cfg.staging();
            return Cfg.staging();
        }

        private final IIMCExecutor _imce;
        private final CfgKeyManagersProvider _cfgKeyManagersProvider;

        private SSLContext _sslContext;

        @Inject
        public Factory(
                CoreIMCExecutor cimce,
                CfgKeyManagersProvider cfgKeyManagersProvider)
        {
            _imce = cimce.imce();
            _cfgKeyManagersProvider = cfgKeyManagersProvider;
            try {
                getSSLContext();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ChannelPipeline getPipeline() throws Exception
        {
            ChannelPipeline pipeline = Channels.pipeline();
            appendToPipeline(pipeline);
            return pipeline;
        }

        private SSLContext createSslContext()
                throws CertificateException, IOException, KeyStoreException,
                NoSuchAlgorithmException, UnrecoverableKeyException, KeyManagementException
        {
            // akin to ClientSSLEngineFactory
            char[] passwd = {};
            PrivateKey privateKey = _cfgKeyManagersProvider.getPrivateKey();
            X509Certificate cert = _cfgKeyManagersProvider.getCert();
            Certificate[] chain = { cert };

            KeyStore keyStore = KeyStore.getInstance(JKS);
            keyStore.load(null, null);
            keyStore.setKeyEntry(DEVICE, privateKey, passwd, chain);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(SUNX509);
            keyManagerFactory.init(keyStore, passwd);

            SSLContext context = SSLContext.getInstance(TLS);
            context.init(keyManagerFactory.getKeyManagers(), null, null);
            return context;
        }

        private synchronized SSLContext getSSLContext()
                throws NoSuchAlgorithmException, KeyStoreException, IOException,
                CertificateException, UnrecoverableKeyException, KeyManagementException
        {
            if (_sslContext == null) {
                _sslContext = createSslContext();
            }
            return _sslContext;
        }

        private SslHandler createSslHandler()
                throws Exception
        {
            SSLContext context = getSSLContext();
            SSLEngine engine = context.createSSLEngine();
            engine.setUseClientMode(false);
            SslHandler handler = new SslHandler(engine);
            handler.setCloseOnSSLException(true);
            return handler;
        }

        public void appendToPipeline(ChannelPipeline p)
                throws Exception
        {
            MobileService mobileService = new MobileService(_imce);
            SslHandler sslHandler = createSslHandler();
            p.addLast("ssl", sslHandler);
            p.addLast("frameDecoder",
                    new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, LENGTH_FIELD_SIZE, 0,
                            LENGTH_FIELD_SIZE));
            p.addLast("frameEncoder", new LengthFieldPrepender(LENGTH_FIELD_SIZE));
            p.addLast("magicHeaderWriter", MAGIC_HEADER.new WriteMagicHeaderHandler());
            p.addLast("magicHeaderReader", MAGIC_HEADER.new ReadMagicHeaderHandler());
            p.addLast("mobileServiceHandler", new MobileServiceHandler(mobileService));
        }
    }
}
