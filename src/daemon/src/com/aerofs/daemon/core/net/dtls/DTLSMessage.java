package com.aerofs.daemon.core.net.dtls;

import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.Profiler;
import com.aerofs.lib.cfg.Cfg;

import javax.annotation.Nullable;

/*
  TODO(AL): This is technically a future with DTLSMessage as the result. Create a Core future that
  handles all the core lock release and reclaim logic?
 */
class DTLSMessage<T>
{
    static enum Type
    {
        UNICAST_RECV, STREAM_BEGUN, CHUNK_RECV,
        SEND_UNICAST, BEGIN_STREAM, SEND_CHUNK
    }

    final Type _type;
    final T _msg;
    final StreamID _sid;
    final int _seq;
    final Token _tk;

    private TCB _tcb;
    private boolean _beginStreamSent;
    private boolean _done;
    private Exception _e;

    private final Profiler _profiler;

    static public class Factory<T>
    {
        public DTLSMessage<T> create_(Type mt, T msg)
        {
            return new DTLSMessage<T>(mt, msg, null, 0, null);
        }

        public DTLSMessage<T> create_(Type mt, T msg, StreamID strmId, int seq)
        {
            return new DTLSMessage<T>(mt, msg, strmId, seq, null);
        }

        public DTLSMessage<T> create_(Type mt, T msg, StreamID strmId, int seq,
                Token tk)
        {
            return new DTLSMessage<T>(mt, msg, strmId, seq, tk);
        }
    }

    private DTLSMessage(Type mt, T msg, StreamID sid, int seq, Token tk)
    {
        if (mt == Type.BEGIN_STREAM || mt == Type.SEND_CHUNK) {
            assert tk != null;
        } else {
            assert tk == null;
        }

        _type = mt;
        _msg = msg;
        _sid = sid;
        _seq = seq;
        _tk = tk;
        _profiler = new Profiler(DTLSMessage.class.getSimpleName());
    }

    /**
     * pause until done() is called (by any other thread)
     */
    void wait_() throws Exception
    {
        assert _tk != null;

        if (!_done) {
            _tcb = TC.tcb();
            try {
                _tk.pause_(Cfg.timeout(), "wait " + _type);
            } finally {
                _tcb = null;
            }
            assert _done;
        }

        if (_e != null) throw _e;
    }

    /**
     * see wait()
     */
    void done_(@Nullable Exception e)
    {
        if (_done) {
            return;
        }

        _done = true;
        _e = e;

        // If there is a profiler set, stop or reset it, depending
        // on whether this message was delivered successfully or if an
        // exception occurred.
        if (_profiler.started()) {
            if (e == null) {
                _profiler.stop();
            } else {
                _profiler.reset();
            }
        }

        if (_tcb != null) {
            _tcb.resume_();
        }
    }

    void setBeginStreamSent()
    {
        _beginStreamSent = true;
    }

    boolean isBeginStreamSent()
    {
        return _beginStreamSent;
    }

    public Profiler getProfiler()
    {
        return _profiler;
    }
}
