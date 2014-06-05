package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.IDeviceEvictionListener;
import com.aerofs.daemon.core.protocol.CoreProtocolUtil;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IDID2UserDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.proto.Core.PBCore.Type;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

/**
 * Creates and maintains mappings from DID->UserID.
 * <p/>
 * Mappings are maintained in both an in-memory cache and persistently on disk.
 * The in-memory mappings are flushed on an LRU basis.
 * <p/>
 * Mappings can be retrieved using {@link DeviceToUserMapper#getUserIDForDIDNullable_(DID)}.
 * When this method is called a two-step process is followed:
 * <ol>
 *     <li>Retrieve UserID from the in-memory cache.</li>
 *     <li>Retrieve UserID from the persistent store (if successful, refresh the in-memory cache).</li>
 * </ol>
 * Mappings are created using an SP call or a peer-to-peer resolution process.
 * In either case the mapping is set via:
 * <ul>
 *     <li>{@link DeviceToUserMapper#onUserIDResolved_(DID, UserID)}</li>
 *     <li>{@link DeviceToUserMapper#onUserIDResolved_(DID, UserID, Trans)}</li>
 * </ul>
 * To initiate a peer-to-peer resolution process the user <strong>must</strong>
 * call {@link DeviceToUserMapper#issuePeerToPeerResolveUserIDRequest_(DID)}. On receiving
 * the RESOLVE_USER_ID_REQUEST the user <strong>must</strong> call
 * {@link DeviceToUserMapper#respondToPeerToPeerResolveUserIDRequest_(DID)}.
 */
public class DeviceToUserMapper
{
    private static final Logger l = Loggers.getLogger(DeviceToUserMapper.class);

    private final Map<DID, UserID> _mappingCache = Maps.newHashMap();
    private final Multimap<DID, TCB> _resolutionWaiters = HashMultimap.create();
    private final TransportRoutingLayer _transportRoutingLayer;
    private final TokenManager _tokenManager;
    private final IDID2UserDatabase _mappingStore;
    private final CoreDeviceLRU _deviceLRU;
    private final TransManager _tm;

    @Inject
    public DeviceToUserMapper(
            TokenManager tokenManager,
            TransportRoutingLayer transportRoutingLayer,
            IDID2UserDatabase mappingStore,
            CoreDeviceLRU deviceLRU,
            TransManager tm)
    {
        _tokenManager = tokenManager;
        _transportRoutingLayer = transportRoutingLayer;
        _mappingStore = mappingStore;
        _deviceLRU = deviceLRU;
        _tm = tm;

        // mappings for old (i.e. devices that are not used within
        // this process lifetime) are removed from the in-memory cache
        deviceLRU.addEvictionListener_(new IDeviceEvictionListener()
        {
            @Override
            public void evicted_(DID did)
            {
                _mappingCache.remove(did);
            }
        });
    }

    /**
     * Get the local DID->UserID mapping. This method searches, in order:
     * <ol>
     *     <li>Local in-memory cache.</li>
     *     <li>Local persistent store.</li>
     * </ol>
     * @return null if no mapping is found
     */
    public @Nullable UserID getUserIDForDIDNullable_(DID did)
            throws SQLException
    {
        UserID userID = _mappingCache.get(did);

        if (userID == null) {
            userID = _mappingStore.getNullable_(did);

            if (userID != null) {
                _mappingCache.put(did, userID);
            }
        }

        _deviceLRU.addDevice_(did);

        return userID;
    }

    /**
     * Issue a unicast RESOLVE_USER_ID_REQUEST.
     * <p/>
     * This initiates a TLS-secured unicast connection to the remote
     * device. As a side effect the device's UserID is retrieved. The
     * calling thread pauses until a RESOLVE_USER_ID_RESPONSE is
     * received or the entire process fails (due to a timeout, etc.)
     * This method is a noop if the DID->UserID mapping exists.
     * <p/>
     * The response <strong>must</strong> be processed using
     * {@link DeviceToUserMapper#onUserIDResolved_(DID, UserID)} for the
     * local persistent store to be updated.
     * <p/>
     * This method <strong>does not</strong> require access to
     * the AeroFS servers. This makes it ideal for clients on an
     * isolated network.
     */
    public UserID issuePeerToPeerResolveUserIDRequest_(DID did)
            throws Exception
    {
        UserID cachedUserID = _mappingCache.get(did);
        if (cachedUserID != null) {
            l.trace("{} mapping exists {}->{}", did, did, cachedUserID);
            return cachedUserID;
        }

        l.info("{} send resolve", did);
        _transportRoutingLayer.sendUnicast_(did, CoreProtocolUtil.newCoreMessage(Type.RESOLVE_USER_ID_REQUEST).build());

        Token tk = _tokenManager.acquireThrows_(Cat.RESOLVE_USER_ID, did.toString());
        try {
            TCB tcb = TC.tcb();
            _resolutionWaiters.put(did, tcb);
            try {
                tk.pause_(Cfg.timeout(), String.format("d2u %s", did));
            } finally {
                _resolutionWaiters.remove(did, tcb);
            }
        } finally {
            tk.reclaim_();
        }

        // this should always return a non-null value
        // if the resolution succeeds then both
        // _mappingStore and _mappingCache have been refreshed
        UserID userID = _mappingCache.get(did);
        Preconditions.checkState(userID != null);
        return userID;
    }

    /**
     * Issue a unicast RESOLVE_USER_ID_RESPONSE.
     */
    public void respondToPeerToPeerResolveUserIDRequest_(DID did)
            throws Exception
    {
        l.info("{} respond to resolve", did);

        _transportRoutingLayer.sendUnicast_(did, CoreProtocolUtil.newCoreMessage(Type.RESOLVE_USER_ID_RESPONSE).build());
    }

    /**
     * Creates a mapping from DID->UserID
     * if one does not already exist. This method
     * is a noop if a mapping exists.
     * <p/>
     * Callers <strong>must</strong> securely authenticate
     * the remote device before calling this method.
     * <p/>
     * This method <strong>starts</strong> a transaction.
     */
    public void onUserIDResolved_(DID did, UserID userID)
            throws SQLException
    {
        if (!_mappingCache.containsKey(did)) {
            Trans t = _tm.begin_();
            try {
                onUserIdResolvedInternal_(did, userID, t);
                t.commit_();
            } finally {
                t.end_();
            }
        } else {
            _deviceLRU.addDevice_(did);
        }

        wakeResolutionWaiters_(did, userID);
    }

    /**
     * Creates a mapping from DID->UserID
     * if one does not already exist. This method
     * is a noop if a mapping exists.
     * <p/>
     * Callers <strong>must</strong> securely authenticate
     * the remote device before calling this method.
     * <p/>
     * This method <strong>does not</strong> start a transaction
     */
    public void onUserIDResolved_(DID did, UserID userID, Trans t)
            throws SQLException
    {
        if (!_mappingCache.containsKey(did)) {
            onUserIdResolvedInternal_(did, userID, t);
        } else {
            _deviceLRU.addDevice_(did);
        }

        wakeResolutionWaiters_(did, userID);
    }

    // Sets the DID->UserID mapping in the persistent store
    // if it doesn't exist. Also always updates the in-memory
    // mapping and device caches.
    private void onUserIdResolvedInternal_(DID did, UserID userID, Trans t)
            throws SQLException
    {
        l.debug("{} mapped to {}", did, userID);

        UserID persistedUserID = _mappingStore.getNullable_(did);

        if (persistedUserID == null) {
            _mappingStore.insert_(did, userID, t);
        } else {
            Preconditions.checkState(persistedUserID.equals(userID), "exp:%s act:%s", userID, persistedUserID);
        }

        _mappingCache.put(did, userID);
        _deviceLRU.addDevice_(did);
    }

    // Verifies that the in-memory cache has been updated
    // and wakes any threads waiting on the UserID having been resolved
    private void wakeResolutionWaiters_(DID did, UserID userID)
    {
        UserID cachedUserID = _mappingCache.get(did);
        Preconditions.checkState(cachedUserID.equals(userID), "userID:%s", cachedUserID);

        Collection<TCB> waiters = _resolutionWaiters.get(did);
        if (!waiters.isEmpty()) {
            for (TCB tcb : waiters) {
                tcb.resume_();
            }
        }
    }
}
