package com.aerofs.daemon.core.net.throttling;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Limit;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Container for messages that pass through the Limiter layer
 */
class Outgoing
{
    public static enum Type
    {
        UNICAST, STREAM_BEGIN, STREAM_CHUNK
    }

    public static String toStringType(Type t)
    {
        switch (t) {
            case UNICAST:
                return "UC";
            case STREAM_BEGIN:
                return "SB";
            case STREAM_CHUNK:
                return "ST";
        }

        assert false;
        return "";
    }

    private static Logger l = Loggers.getLogger(Outgoing.class);

    private final Type type_;

    // common
    private final byte[] pay_;
    private final Endpoint ep_;

    private boolean tokensNeeded_;

    //
    // stream only
    //

    /**
     * ID of the stream beginning or chunk
     */
    private final StreamID sid_;
    private final int seq_;
    private final Token tok_;

    /**
     * holds any exception that occurs while processing this message in a
     * non-originating thread
     */
    private Exception e_;

    /**
     * represents whether this message was processed through the choking layer
     * either successfully or unsuccessfully
     */
    private boolean done_;

    /**
     * opaque thread identifier. Should be set _only_ by the originating thread
     * (it is not this class' responsibility to verify this). The TCB is used to
     * control originating thread resumption and exception throwing
     */
    private TC.TCB tcb_;

    private Outgoing(Type type, byte[] payload, Endpoint ep, @Nullable StreamID sid, int seq,
            @Nullable Token tok)
    {
        assert payload != null && ep != null;
        if (type == Type.STREAM_BEGIN || type == Type.STREAM_CHUNK) {
            assert sid != null && seq >= 0 && tok != null;
        }

        type_ = type;

        pay_ = payload;
        ep_ = ep;

        tokensNeeded_ = false;

        sid_ = sid;
        seq_ = seq;
        tok_ = tok;

        e_ = null;
        done_ = false;
    }

    Outgoing(byte[] payload, Endpoint ep)
    {
        this(Type.UNICAST, payload, ep, null, 0, null);
    }

    Outgoing(byte[] payload, Endpoint ep, StreamID sid, int seq, Token tok)
    {
        this(seq == 0 ? Type.STREAM_BEGIN : Type.STREAM_CHUNK, payload, ep, sid, seq, tok);
    }

    public Type getType()
    {
        return type_;
    }

    public byte[] getPayload()
    {
        return pay_;
    }

    public int getLength()
    {
        return pay_.length;
    }

    public Endpoint getEndpoint()
    {
        return ep_;
    }

    public StreamID getSid()
    {
        assert type_ == Type.STREAM_BEGIN || type_ == Type.STREAM_CHUNK;
        return sid_;
    }

    public int getSeq()
    {
        return seq_;
    }

    public Token getTok()
    {
        assert type_ == Type.STREAM_BEGIN || type_ == Type.STREAM_CHUNK;
        return tok_;
    }

    public void setTokensNeeded()
    {
        tokensNeeded_ = true;
    }

    //
    // serialization
    //

    public byte[] serialize()
            throws IOException
    {
        Limit.PBLimit pbl =
                Limit.PBLimit.newBuilder()
                        .setType(tokensNeeded_ ?
                                Limit.PBLimit.Type.REQUEST_BW :
                                Limit.PBLimit.Type.NOOP)
                        .build();

        ByteArrayOutputStream ba =
                new ByteArrayOutputStream(pbl.getSerializedSize() + pay_.length);
        pbl.writeDelimitedTo(ba);
        ba.write(pay_);

        return ba.toByteArray();
    }

    //
    // execution control
    //

    public void pauseProcessing()
            throws Exception
    {
        if (!done_) {
            l.trace("o not done: pause");

            assert tcb_ == null;
            tcb_ = TC.tcb();
            try {
                tok_.pause_(Cfg.timeout(), "wait " + type_);
            } finally {
                tcb_ = null;
                if (!done_) l.warn(this + ": abort before done");
            }
        }

        if (e_ != null) {
            // if e_ is set, then we want to complete execution for this Choked
            // the setter of e_ should prevent all further execution using this
            // Choked
            assert done_;
            throw e_;
        }
    }

    public void finishProcessing(@Nullable Exception e)
    {
        assert !done_;

        if (e != null) e_ = e;
        done_ = true;
        if (tcb_ != null) tcb_.resume_();
    }
}
