package com.aerofs.daemon.transport.xmpp.jingle;

import com.aerofs.base.id.DID;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExJingle;
import org.slf4j.Logger;

// see Engine.java for documentation

class Tandem
{
    private static final Logger l = Util.l(Tandem.class);

    private final Channel[] _cs = new Channel[2];
    private boolean _notified = false;
    private final IJingle ij;

    Tandem(IJingle ij)
    {
        this.ij = ij;
    }

    Channel get_()
    {
        return _cs[0];
    }

    boolean isEmpty_()
    {
        if (_cs[0] == null) assert _cs[1] == null;
        return get_() == null;
    }

    void unpack_()
    {
        if (_cs[0] != null) {
            assert _cs[1] == null;
            _cs[1] = _cs[0];
            _cs[0] = null;
        }
    }

    void pack_()
    {
        if (_cs[1] != null) {
            assert _cs[0] == null;
            _cs[0] = _cs[1];
            _cs[1] = null;
        }
    }

    /**
     * remove, but not close nor delete, the specified channel
     */
    void remove_(Channel c)
    {
        for (int i = 0; i < 2; i++) {
            if (_cs[i] == c) {
                _cs[i] = null;
                pack_();
                return;
            }
        }
        assert false;
    }

    void add_(Channel c)
    {
        if (c.isIncoming()) {
            // close the existing incoming stream if any
            for (int i = 0; i < 2; i++) {
                if (_cs[i] != null && _cs[i].isIncoming()) {
                    l.debug("t:" + this + " discard old incoming tunnel c:" + _cs[i]);

                    _cs[i].close_(new ExJingle("stream overwritten"));
                    _cs[i].delete_();
                    _cs[i] = null;
                }
            }

        } else {
            // cannot add outgoing streams for more than once
            for (Channel p2 : _cs) assert p2 == null || p2.isIncoming();
        }

        unpack_();
        _cs[0] = c;

        l.debug("t:" + this + " after add");
    }

    void connected_()
    {
        assert _cs[0] != null;
        DID did = _cs[0].did();

        if (!_notified) ij.peerConnected(did);
        _notified = true;
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
            if (_cs[i] != null) {
                _cs[i].close_(e);
                _cs[i].delete_();
                _cs[i] = null;
            }
        }
    }

    @Override
    public String toString()
    {
        return "[" + _cs[0] + ", " + _cs[1] + "]";
    }

    long getBytesIn_()
    {
        long ret = 0;
        for (Channel c : _cs) {
            if (c != null) ret += c.getBytesIn_();
        }
        return ret;
    }

    String diagnose_()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            if (_cs[i] != null) {
                sb.append(_cs[i] + "\n-----------\n");
                sb.append(_cs[i].diagnose_());
            }
        }

        return sb.toString();
    }
}
