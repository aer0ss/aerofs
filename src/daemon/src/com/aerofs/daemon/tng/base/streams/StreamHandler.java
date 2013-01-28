/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.streams;

import com.aerofs.lib.event.Prio;
import com.aerofs.daemon.lib.async.ISingleThreadedPrioritizedExecutor;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.tng.IIncomingStream;
import com.aerofs.daemon.tng.IOutgoingStream;
import com.aerofs.daemon.tng.IStream;
import com.aerofs.daemon.tng.IStreamMap;
import com.aerofs.daemon.tng.IUnicastListener;
import com.aerofs.daemon.tng.base.IStreamFactory;
import com.aerofs.daemon.tng.base.IncomingAeroFSPacket;
import com.aerofs.daemon.tng.base.MessageEvent;
import com.aerofs.daemon.tng.base.OutgoingAeroFSPacket;
import com.aerofs.daemon.tng.base.SimplePipelineEventHandler;
import com.aerofs.daemon.tng.base.pipeline.IPipelineContext;
import com.aerofs.daemon.tng.ex.ExStreamAlreadyExists;
import com.aerofs.daemon.tng.ex.ExStreamInvalid;
import com.aerofs.daemon.tng.ex.ExTransport;
import com.aerofs.base.id.SID;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.proto.Transport.PBTPHeader;
import com.google.common.util.concurrent.FutureCallback;
import org.apache.log4j.Logger;

import static com.aerofs.daemon.tng.base.Handlers.sendOutgoingMessage_;
import static com.aerofs.base.async.FutureUtil.addCallback;
import static com.aerofs.proto.Transport.PBStream.InvalidationReason.STREAM_NOT_FOUND;

public final class StreamHandler extends SimplePipelineEventHandler
{
    private static final Logger l = com.aerofs.lib.Util.l(StreamHandler.class);

    private final IUnicastListener _unicastListener;
    private final ISingleThreadedPrioritizedExecutor _executor;
    private final IStreamFactory _streamFactory;
    private final IStreamMap<IIncomingStream> _incoming;
    private final IStreamMap<IOutgoingStream> _outgoing;

    public StreamHandler(IUnicastListener unicastListener,
            ISingleThreadedPrioritizedExecutor executor, IStreamFactory streamFactory,
            IStreamMap<IIncomingStream> incoming, IStreamMap<IOutgoingStream> outgoing)
    {
        this._unicastListener = unicastListener;
        this._executor = executor;
        this._streamFactory = streamFactory;
        this._incoming = incoming;
        this._outgoing = outgoing;
    }

    @Override
    protected void onIncomingMessageEvent_(IPipelineContext ctx, MessageEvent messageEvent)
            throws Exception
    {
        if (!(messageEvent.getMessage_() instanceof IncomingAeroFSPacket)) {
            super.onIncomingMessageEvent_(ctx, messageEvent);
            return;
        }

        IncomingAeroFSPacket in = (IncomingAeroFSPacket) messageEvent.getMessage_();

        if (in.getType_() != PBTPHeader.Type.STREAM) {
            super.onIncomingMessageEvent_(ctx, messageEvent);
            return;
        }

        processStreamPacket_(ctx, messageEvent, in);
    }

    private void processStreamPacket_(IPipelineContext ctx, MessageEvent message,
            IncomingAeroFSPacket in)
    {
        StreamID id = new StreamID(in.getHeader_().getStream().getStreamId());
        try {
            switch (in.getHeader_().getStream().getType()) {
            case BEGIN_STREAM:
                IIncomingStream newStream = _streamFactory.createIncoming_(ctx.getConnection_(), id,
                        makeSid(in), Prio.LO);
                addStream_(newStream, _incoming);
                _unicastListener.onStreamBegun(newStream);
                message.getCompletionFuture_().set(null);
                break;
            case TX_ABORT_STREAM:
                IIncomingStream txAborted = _incoming.get(id);
                message.getCompletionFuture_().chain(txAborted.abort_(makeReason(in)));
                break;
            case RX_ABORT_STREAM:
                OutgoingStream rxAborted = (OutgoingStream) _outgoing.get(id); // FIXME!
                message.getCompletionFuture_().chain(rxAborted.abortByReceiver_(makeReason(in)));
                break;
            case PAYLOAD:
                try {
                    IncomingStream streamWithData = (IncomingStream) _incoming.get(id); // FIXME!
                    streamWithData.onBytesReceived_(in.getHeader_().getStream().getSeqNum(),
                            in.getPayload_(), in.getWirelen_());
                    message.getCompletionFuture_().set(null);
                } catch (ExStreamInvalid e) {
                    sendAbortIncomingStreamMessage_(ctx, message, in.getHeader_(),
                            STREAM_NOT_FOUND);
                    message.getCompletionFuture_().setException(e);
                }
                break;
            default:
                l.warn("unrecognized stream pkt sub-type:" + in.getType_());
                message.getCompletionFuture_()
                        .setException(new ExTransport("unrecognized stream message sub-type"));
            }
        } catch (ExStreamInvalid e) {
            if (in.getHeader_().getStream().getType() == PBStream.Type.RX_ABORT_STREAM) {
                l.warn("id:" + id + " no outgoing stream found");
            } else {
                l.warn("id:" + id + " no incoming stream found");
            }
            message.getCompletionFuture_().setException(e);
        } catch (ExTransport e) {
            l.warn("ExTransport exception occurred in StreamHandler");
            message.getCompletionFuture_().setException(e);
        }
    }

    private static SID makeSid(IncomingAeroFSPacket pkt)
    {
        return new SID(pkt.getHeader_().getSid());
    }

    private static InvalidationReason makeReason(IncomingAeroFSPacket pkt)
    {
        return pkt.getHeader_().getStream().getReason();
    }

    private void sendAbortIncomingStreamMessage_(IPipelineContext ctx, MessageEvent oldEvent,
            PBTPHeader hdr, InvalidationReason reason)
    {
        sendOutgoingMessage_(ctx, oldEvent,
                new OutgoingAeroFSPacket(createAbortIncomingStreamMessage_(hdr, reason), null));
    }

    private PBTPHeader createAbortIncomingStreamMessage_(PBTPHeader hdr, InvalidationReason reason)
    {
        return PBTPHeader.newBuilder()
                .setType(PBTPHeader.Type.STREAM)
                .setSid(hdr.getSid())
                .setStream(Transport.PBStream
                        .newBuilder()
                        .setType(Transport.PBStream.Type.RX_ABORT_STREAM)
                        .setStreamId(hdr.getStream().getStreamId())
                        .setReason(reason))
                .build();
    }

    @Override
    protected void onOutgoingMessageEvent_(IPipelineContext ctx, final MessageEvent messageEvent)
            throws Exception
    {
        if (!(messageEvent.getMessage_() instanceof NewOutgoingStream)) {
            super.onOutgoingMessageEvent_(ctx, messageEvent);
            return;
        }

        final NewOutgoingStream streamParameters = (NewOutgoingStream) messageEvent.getMessage_();

        try {
            final IOutgoingStream stream = _streamFactory.createOutgoing_(
                    messageEvent.getConnection_(), streamParameters.getId_(),
                    streamParameters.getSid_(), messageEvent.getPriority_());

            addStream_(stream, _outgoing);

            OutgoingStream outStream = (OutgoingStream) stream; // FIXME: should have iface
            addCallback(outStream.begin_(), new FutureCallback<Void>()
            {
                @Override
                public void onSuccess(Void v)
                {
                    streamParameters.getStreamCreationFuture_().set(stream);
                    messageEvent.getCompletionFuture_().set(null);
                }

                @Override
                public void onFailure(Throwable t)
                {
                    streamParameters.getStreamCreationFuture_().setException(t);
                    messageEvent.getCompletionFuture_().setException(t);
                }
            });
        } catch (ExStreamAlreadyExists e) {
            l.warn("id:" + streamParameters.getId_() +
                    " cannot begin out stream - stream exists");

            streamParameters.getStreamCreationFuture_().setException(e);
            messageEvent.getCompletionFuture_().setException(e);
        }
    }

    private <Stream extends IStream> Stream addStream_(final Stream stream,
            final IStreamMap<Stream> streamMap)
            throws ExStreamAlreadyExists
    {
        streamMap.add(stream);

        stream.getCloseFuture_().addListener(new Runnable()
        {
            @Override
            public void run()
            {
                streamMap.remove(stream.getStreamId_());
            }
        }, _executor);

        return stream;
    }
}
