package com.aerofs.daemon.core.net.dtls;

import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.net.PeerContext;
import com.aerofs.daemon.core.net.dtls.DTLSLayer.Footer;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.PrioQueue;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExDTLS;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.id.DID;
import com.aerofs.swig.dtls.DTLSEngine;
import com.aerofs.swig.dtls.DTLSEngine.DTLS_RETCODE;
import com.aerofs.swig.dtls.SSLCtx;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

class DTLSCache implements IDumpStatMisc
{
    private static final Logger l = Util.l(DTLSCache.class);

    private SSLCtx _cliCtx;
    private final boolean _isSender;
    private final Map<PeerContext, DTLSEntry> _backlog = Maps.newHashMap();
    private final Map<PeerContext, DTLSEntry> _frontlog = Maps.newHashMap();
    private final Map<DID, Set<PeerContext>> _did2pcs = Maps.newHashMap();

    private final String _pathCACert;
    private final String _pathDevCert;
    private final DTLSLayer _layer;

    private final CoreDeviceLRU _dlru;

    public static class Factory
    {
        private final CoreDeviceLRU _dlru;

        @Inject
        public Factory(CoreDeviceLRU dlru)
        {
            _dlru = dlru;
        }

        public DTLSCache create_(DTLSLayer layer, boolean isSender,
                String pathCACert, String pathDevCert) throws IOException
        {
            return new DTLSCache(_dlru, layer, isSender, pathCACert, pathDevCert);
        }
    }

    private DTLSCache(CoreDeviceLRU dlru, DTLSLayer layer, boolean isSender,
            String pathCACert, String pathDevCert)
        throws IOException
    {
        _dlru = dlru;
        _layer = layer;
        _isSender = isSender;
        _pathCACert = pathCACert;
        _pathDevCert = pathDevCert;

        _dlru.addEvictionListener_(
            new IDeviceEvictionListener()
            {
                private void handleEvicted_(String type, PeerContext p,
                        Map<PeerContext, DTLSEntry> dm)
                {
                    l.debug(p + " evicted. drop msg q: " + type);

                    DTLSEntry de = dm.remove(p);
                    if (de != null) {
                        OutArg<Prio> outPrio = new OutArg<Prio>();
                        Exception e = new Exception("dtls engine evicted: " + type);
                        while (!de._sendQ.isEmpty_()) {
                            DTLSMessage<byte[]> msg = de._sendQ.dequeue_(outPrio);
                            msg.done_(e);
                        }
                    }
                }

                @Override
                public void evicted_(DID d)
                {
                    try {
                        Set<PeerContext> pcs = _did2pcs.get(d);
                        if (pcs != null) {
                            for (PeerContext p : pcs) {
                                handleEvicted_("bl", p, _backlog);
                                handleEvicted_("bl", p, _frontlog);
                            }
                        } else {
                            l.warn("no pcs 4 d:" + d);
                        }
                    } finally {
                        _did2pcs.remove(d);
                        l.debug("evict d:" + d);
                    }
                }
            });
    }

    private void removePeerContext_(PeerContext pc)
    {
        if (_frontlog.get(pc) == null && _backlog.get(pc) == null) {
            Set<PeerContext> pcs = _did2pcs.get(pc.did());
            pcs.remove(pc);
            if (pcs.isEmpty()) {
                _did2pcs.remove(pc.did());
            }
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
    }

    DTLSEntry findEntryInBacklog_(PeerContext pc)
    {
        return _backlog.get(pc);
    }

    ArrayList<DTLSEntry> findEntryList_(PeerContext pc)
    {
        ArrayList<DTLSEntry> entryList = new ArrayList<DTLSEntry>(2);

        DTLSEntry entry = _frontlog.get(pc);
        if (null != entry) {
            l.debug("found ctx in frontlog");
            entryList.add(entry);
        }

        entry = _backlog.get(pc);
        if (null != entry) {
            l.debug("found ctx in backlog");
            entryList.add(entry);
        }

        if (entryList.isEmpty()) l.debug("cant find ctx");

        return entryList;
    }

    DTLSEntry createEntry_(PeerContext pc) throws ExDTLS
    {
        DTLSEngine engine = null;
        l.debug("create entry");

        if (null == _cliCtx) {
            l.debug("create cliCtx");
            String privKey = SecUtil.exportPrivateKey(Cfg.privateKey());
            SSLCtx ctx = new SSLCtx();
            if (ctx.init(_isSender, _pathCACert, _pathCACert.length(), _pathDevCert,
                    _pathDevCert.length(), privKey, privKey.length()) == -1) {
                throw new ExDTLS("cliCtx creation failed");
            }
            _cliCtx = ctx;
        }

        engine = new DTLSEngine();
        DTLS_RETCODE rc = engine.init(_isSender, _cliCtx);

        if (DTLS_RETCODE.DTLS_OK != rc) throw new ExDTLS("engine.init returned error " + rc);

        DTLSEntry entry = new DTLSEntry(_layer, engine);
        addNewEntry_(_backlog, pc, entry);
        return entry;
    }

    private void addNewEntry_(Map<PeerContext, DTLSEntry> dm, PeerContext pc,
            DTLSEntry e)
    {
        Set<PeerContext> pcs = _did2pcs.get(pc.did());
        if (pcs == null) {
            pcs = new HashSet<PeerContext>();
        }

        pcs.add(pc);

        _did2pcs.put(pc.did(), pcs);
        dm.put(pc, e);
    }

    void promote_(PeerContext pc, DTLSEntry entry)
    {
        assert _did2pcs.get(pc.did()).contains(pc);

        if (_backlog.get(pc) == entry) {
            l.debug("move ctx backlog -> frontlog");
            _backlog.remove(pc);
            _frontlog.put(pc, entry);
        } else {
            assert _frontlog.get(pc) == entry;
        }
    }

    void removeEntry_(PeerContext pc, DTLSEntry entry)
    {
        if (entry == _frontlog.get(pc)) {
            l.debug("Removing entry from main cache.");
            _frontlog.remove(pc);
        } else {
            l.debug("Removing entry from the backlog.");
            _backlog.remove(pc);
        }

        removePeerContext_(pc);
    }

    void removeEntries_(PeerContext pc)
    {
        DTLSEntry entry = _frontlog.get(pc);

        if (null != entry) {
            l.debug("remove entry from frontlog");
            _frontlog.remove(pc);
        }

        entry = _backlog.get(pc);
        if (null != entry) {
            l.debug("remove entry from backlog");
            _backlog.remove(pc);
        }

        removePeerContext_(pc);

        if (null == entry) {
            l.debug("removeEntries: cannot find any");
        }
    }

    void drainAndDiscardQueue_(DTLSEntry entry, Exception e)
    {
        assert _isSender;

        PrioQueue<DTLSMessage<byte[]>> prioQueue = entry._sendQ;
        OutArg<Prio> outPrio = new OutArg<Prio>();

        while (!prioQueue.isEmpty_()) {
            l.debug("discard q'd msg");
            DTLSMessage<byte[]> msg = prioQueue.dequeue_(outPrio);
            msg.done_(e);
        }
    }

    void drainAndSendQueue_(DTLSEntry entry, PeerContext pc)
        throws Exception
    {
        // this method should only be called by senders
        assert _isSender;

        PrioQueue<DTLSMessage<byte[]>> sendQ = entry._sendQ;

        if (!entry.isHshakeDone()) {
            timeoutDTLSInHandShake(entry, pc, false);

        } else if (entry.isDraining_()) {
            // another thread is draining the entry. it's possible because
            // the call to entry.encrypt() below may block, and more draining
            // requests may come in while the thread is blocked.
            l.debug("someone else is draining");

        } else {
            entry.setDraining_(true);
            try {
                if (l.isDebugEnabled()) {
                    l.debug("draining q " + (sendQ.isEmpty_() ? "(empty)" : ""));
                }

                OutArg<Prio> outPrio = new OutArg<Prio>();
                while (!sendQ.isEmpty_()) {
                    DTLSMessage<byte[]> msg = sendQ.peek_(outPrio);
                    byte[] bsToSend = entry.encrypt(msg._msg, pc, Footer.OUT_OLD, null);
                    if (null != bsToSend) {

                        promote_(pc, entry);

                        // have to specify the priority for dequeueing, as messages
                        // with higher priorities may have been enqueued during
                        // the encryption above
                        Util.verify(sendQ.dequeue_(outPrio.get()) == msg);

                        try {
                            _layer.sendToLowerLayer_(msg, bsToSend, pc);
                        } catch (Exception e) {
                            l.warn("error while draining: " + e);
                        }

                    } else {
                        l.debug("Could not encrypt the msg");
                        break;
                    }
                }
            } finally {
                entry.setDraining_(false);
            }
        }
    }

    void timeoutDTLSInHandShake(final DTLSEntry entry, final PeerContext pc,
            boolean updateLastHandshakeMessage)
    {
        long now = System.currentTimeMillis();

        // if we're performing a handshake, check if the
        // timeout has exceeded
        if (Cfg.timeout() < now - entry._lastHshakeMsgTime) {
            l.warn("hs timeout 4 " + pc);
            removeEntry_(pc, entry);
            if (_isSender) {
                drainAndDiscardQueue_(entry, new ExTimeout("DTLS HS"));
            }

        } else if (updateLastHandshakeMessage == true) {
            l.debug("engine still in hs");
            entry._lastHshakeMsgTime = now;
        }
    }
};

