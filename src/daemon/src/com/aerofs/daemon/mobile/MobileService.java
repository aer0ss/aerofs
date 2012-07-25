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
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.async.FutureUtil;
import com.aerofs.lib.cfg.Cfg;
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
import com.aerofs.proto.Ritual.GetChildrenAttributesReply;
import com.aerofs.proto.Ritual.GetObjectAttributesReply;
import com.aerofs.proto.Ritual.PBBranch;
import com.aerofs.proto.Ritual.PBObjectAttributes;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;

import java.util.Map.Entry;

public class MobileService implements IMobileService
{
    private static final Logger l = Util.l(MobileService.class);
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
        EIGetAttr ev = new EIGetAttr(user, _imce, new Path(path));
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
        EIGetChildrenAttr ev = new EIGetChildrenAttr(user, new Path(path), Core.imce());
        ev.execute(PRIO);

        GetChildrenAttributesReply.Builder bd = GetChildrenAttributesReply.newBuilder();
        for (OA oa : ev._oas) {
            bd.addChildrenName(oa.name());
            bd.addChildrenAttributes(toPB(oa));
        }

        return createReply(bd.build());
    }

    @Override
    public ListenableFuture<StartDownloadReply> startDownload(String user, PBPath source)
            throws Exception
    {
        EIDownloadPacket ev = new EIDownloadPacket(user, _imce, new Path(source), -1, -1);
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

        EIDownloadPacket ev = new EIDownloadPacket(user, _imce, new Path(source), offset, length);
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
            return ByteString.copyFrom(cb.array(), cb.arrayOffset() + cb.readerIndex(), cb.readableBytes());
        } else {
            return ByteString.copyFrom(cb.toByteBuffer());
        }
    }

    private static byte[] toByteArray(ChannelBuffer cb)
    {
        if (cb.hasArray() && cb.arrayOffset() == 0 &&
                cb.readerIndex() == 0 && cb.writerIndex() == cb.array().length) {
            return cb.array();
        }
        byte[] array = new byte[cb.readableBytes()];
        cb.getBytes(cb.readerIndex(), array);
        return array;
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
                        .setLength(en.getValue().length()));
            }
            break;
        default: assert false;
        }

        return bd.build();
    }

    private static abstract class BaseServiceHandler extends SimpleChannelUpstreamHandler
    {
        private static final Logger l = Util.l(BaseServiceHandler.class);

        protected abstract ListenableFuture<byte[]> react(byte[] data);

        ListenableFuture<?> _latest = Futures.immediateFuture(null);

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception
        {
            try {
                ChannelBuffer cb = (ChannelBuffer)e.getMessage();
                byte[] message = toByteArray(cb);
                final Channel channel = e.getChannel();

                final SettableFuture<?> next = SettableFuture.create();
                final ListenableFuture<?> previous = _latest;
                _latest = next;

                final ListenableFuture<byte[]> future = react(message);

                previous.addListener(new Runnable() {
                    @Override
                    public void run()
                    {
                        Futures.addCallback(future, new FutureCallback<byte[]>()
                        {
                            @Override
                            public void onSuccess(byte[] response)
                            {
                                try {
                                    channel.write(ChannelBuffers.wrappedBuffer(response));
                                } finally {
                                    next.set(null);
                                }
                            }

                            @Override
                            public void onFailure(Throwable throwable)
                            {
                                next.setException(throwable);
                                l.warn("Received an exception from the reactor. This should never happen. Aborting.");
                                throw Util.fatal(throwable);
                            }
                        });
                    }
                }, MoreExecutors.sameThreadExecutor());

            } catch (Exception ex) {
                l.warn("Exception: " + Util.e(ex));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception
        {
            // Close the connection when an exception is raised
            e.getChannel().close();
        }
    }

    private static class MobileServiceHandler extends BaseServiceHandler
    {
        private final MobileService _service; // = new MobileService(_imce);
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

        private static final String PROPERTY = "aerofs.mobile.enabled";

        public static boolean isEnabled()
        {
            return Boolean.getBoolean(PROPERTY) && Cfg.staging();
//            return Cfg.staging(a);
        }

        private final IIMCExecutor _imce;

        @Inject
        public Factory(CoreIMCExecutor cimce)
        {
            _imce = cimce.imce();
        }

        @Override
        public ChannelPipeline getPipeline() throws Exception
        {
            ChannelPipeline pipeline = Channels.pipeline();
            appendToPipeline(pipeline);
            return pipeline;
        }

        public void appendToPipeline(ChannelPipeline p)
        {
            MobileService mobileService = new MobileService(_imce);
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
