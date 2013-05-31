package com.aerofs.daemon.core.net.dtls;

import com.aerofs.base.Loggers;
import com.aerofs.lib.event.Prio;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.*;
import com.aerofs.daemon.core.net.dtls.DTLSLayer.Footer;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.PrioQueue;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.ex.ExDTLS;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.swig.dtls.DTLSEngine;
import com.aerofs.swig.dtls.DTLSEngine.DTLS_RETCODE;
import com.aerofs.swig.dtls.SSLCtx;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.PrintStream;
import java.security.PrivateKey;
import java.util.*;

import static com.aerofs.daemon.core.net.dtls.DTLSEntry.DelayedDTLSMessage;

class DTLSCache implements IDumpStatMisc
{
    private static final Logger l = Loggers.getLogger(DTLSCache.class);

    private SSLCtx _cliCtx;
    private final DTLSLayer _layer;
    private final boolean _isSender;
    private final Map<Endpoint, DTLSEntry> _backlog = Maps.newHashMap();
    private final Map<Endpoint, DTLSEntry> _frontlog = Maps.newHashMap();

    // Endpoints hash on DID and ITransport, meaning the same device on
    // a different transport will have its own DTLS Entry. We need to
    // be able to map a DID to all the Endpoints that have DTLS Entries.
    private final Multimap<DID, Endpoint> _did2eps = HashMultimap.create();

    private final PrivateKey _privateKey;
    private final String _pathCACert;
    private final String _pathDevCert;

    public static class Factory
    {
        private final CoreDeviceLRU _dlru;
        private final PrivateKey _privateKey;

        @Inject
        public Factory(CoreDeviceLRU dlru, CfgKeyManagersProvider keyProvider)
        {
            _dlru = dlru;
            _privateKey = keyProvider.getPrivateKey();
        }

        public DTLSCache create_(DTLSLayer layer, boolean isSender, String pathCACert,
                String pathDevCert) throws IOException
        {
            return new DTLSCache(_dlru, layer, isSender, _privateKey, pathCACert, pathDevCert);
        }
    }

    private DTLSCache(CoreDeviceLRU dlru, DTLSLayer layer, boolean isSender,
            PrivateKey privateKey, String pathCACert, String pathDevCert) throws IOException
    {
        _layer = layer;
        _isSender = isSender;
        _privateKey = privateKey;
        _pathCACert = pathCACert;
        _pathDevCert = pathDevCert;

        dlru.addEvictionListener_(
            new IDeviceEvictionListener()
            {
                private void handleEvicted_(String type, Endpoint ep, Map<Endpoint, DTLSEntry> dm)
                {
                    l.debug(ep + " evicted. drop msg q: " + type);

                    DTLSEntry de = dm.remove(ep);
                    if (de != null) {
                        OutArg<Prio> outPrio = new OutArg<Prio>();
                        Exception e = new Exception("dtls engine evicted: " + type);
                        while (!de._sendQ.isEmpty_()) {
                            DTLSMessage<byte[]> msg = de._sendQ.dequeue_(outPrio)._msg;
                            msg.done_(e);
                        }
                    }
                }

                @Override
                public void evicted_(DID d)
                {
                    if (!_did2eps.containsKey(d)) {
                        l.warn("no ep 4 d:" + d);
                        return;
                    }

                    for (Endpoint ep : _did2eps.get(d)) {
                        // If handleEvicted_ were to ever throw an Exception,
                        // be sure to catch it and continue handling any other
                        // Endpoints that may be associated with this DID
                        handleEvicted_("bl", ep, _backlog);
                        handleEvicted_("fl", ep, _frontlog);
                    }

                    _did2eps.removeAll(d);
                    l.debug("evict d:" + d);
                }
            });
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
    }

    ArrayList<DTLSEntry> findEntryList_(final Endpoint ep)
    {
        final ArrayList<DTLSEntry> entryList = new ArrayList<DTLSEntry>(2);

        DTLSEntry entry = _frontlog.get(ep);
        if (null != entry) {
            l.trace("found ctx in frontlog");
            entryList.add(entry);
        }

        entry = _backlog.get(ep);
        if (null != entry) {
            l.trace("found ctx in backlog");
            entryList.add(entry);
        }

        if (entryList.isEmpty()) {
            l.info("cant find ctx");
        }

        return entryList;
    }

    private SSLCtx initializeSSLContext_() throws ExDTLS
    {
        l.debug("create cliCtx");
        final String privKey = SecUtil.exportPrivateKey(_privateKey);
        final SSLCtx ctx = new SSLCtx();
        if (ctx.init(_isSender, _pathCACert, _pathCACert.length(), _pathDevCert,
                _pathDevCert.length(), privKey, privKey.length()) == -1) {
            throw new ExDTLS("cliCtx creation failed");
        }
        return ctx;
    }

    DTLSEntry createEntry_(final Endpoint ep) throws ExDTLS
    {
        l.debug("create entry");

        // Lazy initialize the SSL context
        if (null == _cliCtx) {
            _cliCtx = initializeSSLContext_();
            assert _cliCtx != null;
        }

        // Create a new DTLS Engine for this entry
        final DTLSEngine engine = new DTLSEngine();
        final DTLS_RETCODE rc = engine.init(_isSender, _cliCtx);

        if (DTLS_RETCODE.DTLS_OK != rc) {
            throw new ExDTLS("engine.init returned error " + rc);
        }

        // Create the DTLS Entry and place it in the backlog
        final DTLSEntry entry = new DTLSEntry(_layer, engine);
        _backlog.put(ep, entry);

        // Add an entry to allow DID -> Endpoint lookup
        _did2eps.put(ep.did(), ep);

        return entry;
    }

    void promote_(final Endpoint ep, final DTLSEntry entry)
    {
        assert _did2eps.containsEntry(ep.did(), ep);

        if (removeMapEntryByValueReference(_backlog, ep, entry)) {
            l.debug("move ctx backlog -> frontlog");
            _frontlog.put(ep, entry);
        } else {
            assert _frontlog.get(ep) == entry;
        }
    }

    private void removeEntry_(final Endpoint ep, final DTLSEntry entry)
    {
        if (removeMapEntryByValueReference(_frontlog, ep, entry)) {
            l.debug("Removing entry from main cache.");
        } else if (removeMapEntryByValueReference(_backlog, ep, entry)) {
            l.debug("Removing entry from the backlog.");
        } else {
            assert false : "No entry (" + entry + ") to remove";
        }

        removeEndpoint_(ep);
    }

    void removeEntries_(final Endpoint ep)
    {
        boolean removed = false;

        if (_frontlog.remove(ep) != null) {
            l.debug("remove entry from frontlog");
            removed = true;
        }

        if (_backlog.remove(ep) != null) {
            l.debug("remove entry from backlog");
            removed = true;
        }

        if (removed) {
            removeEndpoint_(ep);
        } else {
            l.debug("removeEntries: cannot find any");
        }
    }

    private void removeEndpoint_(final Endpoint ep)
    {
        if (!_frontlog.containsKey(ep) && !_backlog.containsKey(ep)) {
            l.debug("removing ep " + ep);

            Util.verify(_did2eps.remove(ep.did(), ep));
        }
    }

    boolean removeEntryInBacklog_(final Endpoint ep)
    {
        if (_backlog.containsKey(ep)) {
            removeEntry_(ep, _backlog.get(ep));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Drains the DTLSMessage's enqueued in the given DTLSEntry and propagates an Exception
     * to any threads waiting on the results of the discarded messages.
     *
     * @param entry The DTLSEntry from which to drain the messages
     * @param e The exception which caused the discarding of the messages
     */
    void drainEnqueuedMessages_(final DTLSEntry entry, final Exception e)
    {
        assert _isSender;

        final PrioQueue<DelayedDTLSMessage> prioQueue = entry._sendQ;
        final OutArg<Prio> outPrio = new OutArg<Prio>();

        while (!prioQueue.isEmpty_()) {
            l.debug("discard q'd msg");
            prioQueue.dequeue_(outPrio)._msg.done_(e);
        }
    }

    /**
     * Drains the DTLSMessage's enqueued in the given DTLSEntry, encrypting and sending them
     * to the lower layer.
     *
     * @param entry The DTLSEntry from which to drain the messages
     * @param ep The Endpoint this DTLSEntry belongs to
     * @throws Exception
     */
    void drainAndSendEnqueuedMessages_(final DTLSEntry entry, final Endpoint ep) throws Exception
    {
        // this method should only be called by senders
        assert _isSender;

        if (!entry.isHshakeDone()) {
            // Check if this DTLSEntry has timed-out
            timeoutDTLSInHandShake(entry, ep, false);
            return;
        }

        if (entry.isDraining_()) {
            // another thread is draining the entry. it's possible because
            // the call to entry.encrypt() below may block, and more draining
            // requests may come in while the thread is blocked.
            l.debug("someone else is draining");
            return;
        }

        // Set the entry as being drained so other threads don't attempt to drain when
        // we get blocked on the encryption call below
        entry.setDraining_(true);
        try {
            final PrioQueue<DelayedDTLSMessage> sendQ = entry._sendQ;

            if (l.isDebugEnabled()) {
                l.debug("draining q " + (sendQ.isEmpty_() ? "(empty)" : ""));
            }

            final OutArg<Prio> outPrio = new OutArg<Prio>();
            while (!sendQ.isEmpty_()) {
                final DelayedDTLSMessage dmsg = sendQ.peek_(outPrio);
                final DTLSMessage<byte[]> msg = dmsg._msg;
                final byte[] bsToSend = entry.encrypt(msg._msg, dmsg._pc, Footer.OUT_OLD, null);
                if (null != bsToSend) {

                    // The encryption succeeded, promote this entry to the frontlog
                    promote_(ep, entry);

                    // have to specify the priority for dequeueing, as messages
                    // with higher priorities may have been enqueued during
                    // the encryption above
                    Util.verify(sendQ.dequeue_(outPrio.get()) == dmsg);

                    // Send the encrypted message down to the lower layer
                    try{
                        _layer.sendToLowerLayer_(msg, bsToSend, dmsg._pc);
                    } catch (Exception e) {
                        l.warn("error while draining: " + e);
                    }
                } else {
                    l.warn("Could not encrypt the msg");
                    break;
                }
            }
        } finally {
            entry.setDraining_(false);
        }
    }

    void timeoutDTLSInHandShake(final DTLSEntry entry, final Endpoint ep,
            final boolean updateLastHandshakeMessage)
    {
        final long now = System.currentTimeMillis();

        // if we're performing a handshake, check if the
        // timeout has exceeded
        if (Cfg.timeout() < now - entry._lastHshakeMsgTime) {
            l.warn("hs timeout 4 " + ep);
            removeEntry_(ep, entry);
            if (_isSender) {
                drainEnqueuedMessages_(entry, new ExTimeout("DTLS HS"));
            }

        } else if (updateLastHandshakeMessage) {
            l.debug("engine still in hs");
            entry._lastHshakeMsgTime = now;
        }
    }

    /**
     * Removes a Key-Value pair from the map if the given key exists and the associated value
     * is a reference to the specified value.
     *
     * @param map The map from which to remove the Key-Value pair
     * @param key The key to remove
     * @param value The reference that must be associated with the key
     * @return true if the entry was deleted, false otherwise
     */
    private static <K, V> boolean removeMapEntryByValueReference(final Map<K, V> map, final K key,
            @Nonnull final V value)
    {
        if (map.get(key) != value) {
            return false;
        }

        Util.verify(map.remove(key) == value);
        return true;
    }
}

