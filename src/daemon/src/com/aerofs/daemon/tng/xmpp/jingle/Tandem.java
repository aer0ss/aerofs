/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.jingle;

import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExJingle;
import com.aerofs.lib.id.DID;

// see Engine.java for documentation

final class Tandem
{

    private final Channel[] _cs = new Channel[2];
    private boolean _notified = false;
    private final DID _did;
    private final IJingle ij;

    Tandem(DID did, IJingle ij)
    {
        this._did = did;
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
        assert c.did().equals(_did);

        if (c.isIncoming()) {
            // close the existing incoming stream if any
            for (int i = 0; i < 2; i++) {
                if (_cs[i] != null && _cs[i].isIncoming()) {
                    Util.l(this).info("discard old incoming tunnel " + _cs[i]);
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
    }

    void connected_()
    {
        assert _cs[0] != null;

        if (!_notified) ij.peerConnected(_did);
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
        Util.l(this).info("close tandem " + this + ": " + Util.e(e, ExJingle.class));

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
