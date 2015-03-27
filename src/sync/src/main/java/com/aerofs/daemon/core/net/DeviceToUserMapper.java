package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.daemon.lib.db.DBCache;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.core.protocol.CoreProtocolUtil;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.lib.db.IDID2UserDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.cfg.CfgTimeout;
import com.aerofs.proto.Core.PBCore.Type;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

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
 * {@link DeviceToUserMapper#respondToPeerToPeerResolveUserIDRequest_(DigestedMessage)}.
 */
public class DeviceToUserMapper
{
    private static final Logger l = Loggers.getLogger(DeviceToUserMapper.class);

    static final int CACHE_SIZE = 64;

    private DBCache<DID, UserID> _mappingCache;
    private final Map<DID, TCB> _resolutionWaiters = Maps.newHashMap();
    private TransportRoutingLayer _transportRoutingLayer;
    private TokenManager _tokenManager;
    private IDID2UserDatabase _mappingStore;
    private TransManager _tm;
    private CfgTimeout _cfgTimeout;

    @Inject
    public void inject_(
            TokenManager tokenManager,
            TransportRoutingLayer transportRoutingLayer,
            IDID2UserDatabase mappingStore,
            TransManager tm,
            CfgTimeout cfgTimeout)
    {
        _tokenManager = tokenManager;
        _transportRoutingLayer = transportRoutingLayer;
        _mappingStore = mappingStore;
        _mappingCache = new DBCache<>(tm, CACHE_SIZE);
        _tm = tm;
        _cfgTimeout = cfgTimeout;
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
        return _mappingCache.get_(did, _mappingStore::getNullable_);
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
        UserID cachedUserID = _mappingCache.get_(did);
        if (cachedUserID != null) {
            l.trace("{} mapping exists {}->{}", did, did, cachedUserID);
            return cachedUserID;
        }

        // do not allow concurrent resolutions
        // rationale: maxcast are only used for NEWS_UPDATE and similar signals
        // that can deal with message loss. No point wasting core threads on
        // low-value messages from unkown devices.
        if (_resolutionWaiters.containsKey(did)) {
            throw new ExNoResource("maxcast resolution cap for " + did);
        }

        l.info("{} send resolve", did);
        _transportRoutingLayer.sendUnicast_(did,
                CoreProtocolUtil.newCoreMessage(Type.RESOLVE_USER_ID_REQUEST).build());

        try (Token tk = _tokenManager.acquireThrows_(Cat.RESOLVE_USER_ID, did.toString())) {
            TCB tcb = TC.tcb();
            checkState(_resolutionWaiters.put(did, tcb) == null);
            try {
                tk.pause_(_cfgTimeout.get(), String.format("d2u %s", did));
            } finally {
                checkState(_resolutionWaiters.remove(did) == tcb);
            }
        }

        // this should always return a non-null value
        // if the resolution succeeds then both
        // _mappingStore and _mappingCache have been refreshed
        UserID userID = _mappingCache.get_(did);
        checkState(userID != null);
        return userID;
    }

    /**
     * Issue a unicast RESOLVE_USER_ID_RESPONSE.
     */
    public void respondToPeerToPeerResolveUserIDRequest_(DigestedMessage msg)
            throws Exception
    {
        l.info("{} respond to resolve", msg.did());

        _transportRoutingLayer.sendUnicast_(msg.ep(),
                CoreProtocolUtil.newCoreMessage(Type.RESOLVE_USER_ID_RESPONSE).build());
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
        if (_mappingCache.get_(did) == null) {
            try (Trans t = _tm.begin_()) {
                onUserIdResolvedInternal_(did, userID, t);
                t.commit_();
            }
        }

        wakeResolutionWaiter_(did, userID);
    }

    /**
     * Creates a mapping from DID->UserID if one does not already exist.
     * This method is a noop if a mapping exists.
     * <p/>
     * Callers <strong>must</strong> securely authenticate
     * the remote device before calling this method.
     * <p/>
     * This method <strong>does not</strong> start a transaction
     */
    public void onUserIDResolved_(DID did, UserID userID, Trans t)
            throws SQLException
    {
        if (_mappingCache.get_(did) == null) {
            onUserIdResolvedInternal_(did, userID, t);
        }

        wakeResolutionWaiter_(did, userID);
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
            checkState(persistedUserID.equals(userID), "exp:%s act:%s", userID, persistedUserID);
        }

        _mappingCache.put_(did, userID);
    }

    // Verifies that the in-memory cache has been updated
    // and wakes any threads waiting on the UserID having been resolved
    private void wakeResolutionWaiter_(DID did, UserID userID)
    {
        UserID cachedUserID = checkNotNull(_mappingCache.get_(did));
        checkState(cachedUserID.equals(userID), "userID:%s", cachedUserID);

        TCB waiter = _resolutionWaiters.get(did);
        if (waiter != null) {
            waiter.resume_();
        }
    }
}
