package com.aerofs.daemon.core.protocol;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.TransportRoutingLayer;
import com.aerofs.daemon.core.store.MapSIndex2Store;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.lib.Util;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.notifier.ConcurrentlyModifiableListeners;
import com.aerofs.lib.Path;
import com.aerofs.proto.Core.PBCore;
import com.google.common.collect.Maps;
import org.slf4j.Logger;

import java.util.Map;

public abstract class AbstractListRevChildrenHistory<LISTENER>
{
    private static final Logger l = Loggers.getLogger(AbstractListRevChildrenHistory.class);

    protected class RCHListeners extends ConcurrentlyModifiableListeners<LISTENER>
    {
        private final Path _path;

        RCHListeners(Path path)
        {
            _path = path;
        }

        @Override
        protected void beforeAddFirstListener_()
        {
            Util.verify(_path2ls.put(_path, this) == null);

            int seq = _seqNext++;
            Util.verify(_path2seq.put(_path, seq) == null);
            Util.verify(_seq2path.put(seq, _path) == null);
        }

        @Override
        protected void afterRemoveLastListener_()
        {
            Util.verify(_path2ls.remove(_path) == this);

            Integer seq = _path2seq.remove(_path);
            assert seq != null;
            Util.verify(_seq2path.remove(seq).equals(_path));
        }
    }

    protected final MapSIndex2Store _sidx2s;
    protected final DirectoryService _ds;
    protected final TransportRoutingLayer _trl;

    int _seqNext;
    private final Map<Path, Integer> _path2seq = Maps.newHashMap();
    private final Map<Integer, Path> _seq2path = Maps.newHashMap();
    private final Map<Path, RCHListeners> _path2ls = Maps.newHashMap();

    protected AbstractListRevChildrenHistory(TransportRoutingLayer trl, DirectoryService ds, MapSIndex2Store sidx2s)
    {
        _trl = trl;
        _ds = ds;
        _sidx2s = sidx2s;
    }

    public void add_(Path spath, LISTENER listener) throws Exception
    {
        RCHListeners ls = _path2ls.get(spath);
        if (ls == null) ls = new RCHListeners(spath);
        ls.addListener_(listener);

        // have to call send_() after adding the listener as send_() needs
        // the seq number
        try {
            send_(spath);
        } catch (Exception e) {
            ls.removeListener_(listener);
            throw e;
        }

        try {
            fetchFromLocal_(ls, spath);
        } catch (Exception e) {
            l.warn("fetch from local. ignored: " + Util.e(e));
        }
    }

    public void remove_(Path spath, LISTENER l)
    {
        _path2ls.get(spath).removeListener_(l);
    }

    protected abstract PBCore.Builder newRequest_(Path path);

    private void send_(Path path) throws Exception
    {
        SIndex sidx = _ds.resolveThrows_(path).sidx();
        Store s = _sidx2s.get_(sidx);

        PBCore core = newRequest_(path).build();

        // can't use maxcast as it exposes private information
        for (DID did : s.getOnlinePotentialMemberDevices_().keySet()) {
            _trl.sendUnicast_(did, core);
        }
    }

    protected abstract void processRequestImpl_(DigestedMessage msg)
            throws Exception;

    public final void processRequest_(DigestedMessage msg) throws Exception
    {
        processRequestImpl_(msg);
    }

    protected abstract void processResponseImpl_(DigestedMessage msg)
            throws ExProtocolError;

    public final void processResponse_(DigestedMessage msg) throws ExProtocolError
    {
        processResponseImpl_(msg);
    }

    protected abstract void fetchFromLocal_(RCHListeners ls, Path spath)
            throws Exception;

    /**
     * N.B. the caller must guaranteed that the sequence number corresponding
     * to the path exists
     */
    protected final int getSeq_(Path spath)
    {
        return _path2seq.get(spath);
    }

    /**
     * @return may return null
     */
    protected final Path getPath_(int seq)
    {
        return _seq2path.get(seq);
    }

    /**
     * @return may return null
     */
    protected final RCHListeners getListeners_(Path spath)
    {
        return _path2ls.get(spath);
    }
}
