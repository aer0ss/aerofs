package com.aerofs.daemon.transport.xmpp.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.lib.ex.ExJingle;
import org.slf4j.Logger;

class Tandem
{
    private static final Logger l = Loggers.getLogger(Tandem.class);

    private final JingleDataStream[] _jingleDataStreams = new JingleDataStream[2];
    private boolean _notified = false;
    private final IJingle ij;

    Tandem(IJingle ij)
    {
        this.ij = ij;
    }

    JingleDataStream get_()
    {
        return _jingleDataStreams[0];
    }

    boolean isEmpty_()
    {
        if (_jingleDataStreams[0] == null) assert _jingleDataStreams[1] == null;
        return get_() == null;
    }

    void unpack_()
    {
        if (_jingleDataStreams[0] != null) {
            assert _jingleDataStreams[1] == null;
            _jingleDataStreams[1] = _jingleDataStreams[0];
            _jingleDataStreams[0] = null;
        }
    }

    void pack_()
    {
        if (_jingleDataStreams[1] != null) {
            assert _jingleDataStreams[0] == null;
            _jingleDataStreams[0] = _jingleDataStreams[1];
            _jingleDataStreams[1] = null;
        }
    }

    /**
     * remove, but not close nor delete, the specified channel
     */
    void remove_(JingleDataStream jingleDataStream)
    {
        for (int i = 0; i < 2; i++) {
            if (_jingleDataStreams[i] == jingleDataStream) {
                _jingleDataStreams[i] = null;
                pack_();
                return;
            }
        }
        assert false;
    }

    void add_(JingleDataStream jingleDataStream)
    {
        if (jingleDataStream.isIncoming()) {
            // close the existing incoming stream if any
            for (int i = 0; i < 2; i++) {
                if (_jingleDataStreams[i] != null && _jingleDataStreams[i].isIncoming()) {
                    l.debug("t:" + this + " discard old incoming stream jds:" + _jingleDataStreams[i]);

                    _jingleDataStreams[i].close_(new ExJingle("stream overwritten"));
                    _jingleDataStreams[i].delete_();
                    _jingleDataStreams[i] = null;
                }
            }
        } else {
            // cannot add outgoing streams for more than once
            for (JingleDataStream p2 : _jingleDataStreams) assert p2 == null || p2.isIncoming();
        }

        unpack_();
        _jingleDataStreams[0] = jingleDataStream;

        l.debug("t:" + this + " after add");
    }

    void connected_()
    {
        assert _jingleDataStreams[0] != null;
        DID did = _jingleDataStreams[0].did();

        if (!_notified) {
            _notified = true;
            ij.peerConnected(did);
        }
    }

    boolean isConnected_()
    {
        return _notified;
    }

    /**
     * close and delete both channels
     */
    void close_(Exception e)
    {
        l.warn("t:" + this + " close: cause: " + e);

        for (int i = 0; i < 2; i++) {
            if (_jingleDataStreams[i] != null) {
                _jingleDataStreams[i].close_(e);
                _jingleDataStreams[i].delete_();
                _jingleDataStreams[i] = null;
            }
        }
    }

    @Override
    public String toString()
    {
        return "[" + _jingleDataStreams[0] + ", " + _jingleDataStreams[1] + "]";
    }

    long getBytesIn_()
    {
        long ret = 0;
        for (JingleDataStream jingleDataStream : _jingleDataStreams) {
            if (jingleDataStream != null) ret += jingleDataStream.getBytesIn_();
        }
        return ret;
    }

    String diagnose_()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            if (_jingleDataStreams[i] != null) {
                sb.append(_jingleDataStreams[i]).append("\n-----------\n");
                sb.append(_jingleDataStreams[i].diagnose_());
            }
        }

        return sb.toString();
    }
}
